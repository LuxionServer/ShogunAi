package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedDeviceStatesAndroidSdkTest {

    private val location = SdkLocation("/sdk", "/sdk/emulator/emulator", "/sdk/platform-tools/adb")
    private val otherLocation = SdkLocation("/other", "/other/emulator/emulator", "/other/platform-tools/adb")
    private var now = Instant.parse("2026-01-01T00:00:00Z")

    private class FakeAndroidSdk : AndroidSdk {
        var reads = 0
        var gate: CompletableDeferred<Unit>? = null
        val stopped = mutableListOf<String>()

        override fun locate(repositoryPath: String) = SdkLocation("/sdk", "/sdk/emulator/emulator", "/sdk/platform-tools/adb")

        override suspend fun listAvds(sdk: SdkLocation) = listOf("Pixel")

        override suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState> {
            reads++
            gate?.await()
            return mapOf("emulator-5554" to EmulatorState.RUNNING)
        }

        override fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean) =
            app.luxion.shogunai.domain.FakeAndroidSdk.FakeEmulatorProcess()

        override suspend fun stopEmulator(sdk: SdkLocation, serial: String) {
            stopped += serial
        }
    }

    private val delegate = FakeAndroidSdk()
    private val shared = SharedDeviceStatesAndroidSdk(delegate, clock = { now })

    @Test
    fun `concurrent callers share one read`() = runTest {
        delegate.gate = CompletableDeferred()

        val callers = List(3) { async { shared.deviceStates(location) } }
        runCurrent()
        delegate.gate?.complete(Unit)

        assertEquals(List(3) { mapOf("emulator-5554" to EmulatorState.RUNNING) }, callers.awaitAll())
        assertEquals(1, delegate.reads)
    }

    @Test
    fun `a read within two seconds reuses the last one`() = runTest {
        shared.deviceStates(location)
        now = now.plusMillis(1_999)
        shared.deviceStates(location)

        assertEquals(1, delegate.reads)
    }

    @Test
    fun `a read two seconds later reads again`() = runTest {
        shared.deviceStates(location)
        now = now.plusSeconds(2)
        shared.deviceStates(location)

        assertEquals(2, delegate.reads)
    }

    @Test
    fun `different adb paths don't share a read`() = runTest {
        shared.deviceStates(location)
        shared.deviceStates(otherLocation)

        assertEquals(2, delegate.reads)
    }

    @Test
    fun `stopping an emulator invalidates the cached read and is delegated`() = runTest {
        shared.deviceStates(location)
        shared.stopEmulator(location, "emulator-5554")
        shared.deviceStates(location)

        assertEquals(2, delegate.reads)
        assertEquals(listOf("emulator-5554"), delegate.stopped)
    }

    @Test
    fun `every other call is delegated`() = runTest {
        assertEquals(listOf("Pixel"), shared.listAvds(location))
        assertEquals("/sdk", shared.locate("/repo").root)
    }
}
