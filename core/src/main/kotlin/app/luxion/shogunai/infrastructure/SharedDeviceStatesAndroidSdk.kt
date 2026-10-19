package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SharedDeviceStatesAndroidSdk(
    private val delegate: AndroidSdk,
    private val clock: () -> Instant = Instant::now,
    private val window: Duration = Duration.ofSeconds(2),
) : AndroidSdk by delegate {

    private val mutex = Mutex()
    private val reads = mutableMapOf<String, Read>()

    override suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState> = mutex.withLock {
        val cached = reads[sdk.adb]
        if (cached != null && Duration.between(cached.at, clock()) < window) return cached.states
        val states = delegate.deviceStates(sdk)
        reads[sdk.adb] = Read(states, clock())
        states
    }

    override suspend fun stopEmulator(sdk: SdkLocation, serial: String) {
        mutex.withLock { reads.remove(sdk.adb) }
        delegate.stopEmulator(sdk, serial)
    }

    private data class Read(val states: Map<String, EmulatorState>, val at: Instant)
}
