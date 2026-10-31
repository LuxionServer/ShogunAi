package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeDerivedDataSizeStore
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDerivedDataSource.Companion.derivedData
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.MeasuredSize
import app.luxion.shogunai.domain.model.RememberedSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.time.Instant
import app.luxion.shogunai.domain.io.DerivedDataSizeStore
import app.luxion.shogunai.domain.io.DerivedDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasureDerivedDataUseCaseTest {

    private val monday = Instant.parse("2026-10-19T09:00:00Z")
    private val tuesday = Instant.parse("2026-10-20T09:00:00Z")

    private fun folder(name: String, accessed: Instant?): DerivedDataFolder =
        derivedData(name, "/home/dev/projects/$name/App.xcodeproj", sizeBytes = null).copy(lastAccessed = accessed)

    private fun source(vararg sizes: Pair<DerivedDataFolder, Long>) =
        FakeDerivedDataSource(existing = sizes.map { it.first }).apply { sizes.forEach { (folder, size) -> this.sizes[folder.path] = size } }

    @Test
    fun `an unchanged folder uses its remembered size without being measured`() = runTest {
        val app = folder("App", monday)
        val source = source(app to 15L)
        val store = FakeDerivedDataSizeStore(mapOf(app.path to RememberedSize(12L, monday, 0L)))

        val sizes = MeasureDerivedDataUseCase(source, store)(listOf(app)).toList()

        assertEquals(listOf(MeasuredSize(app.path, 12L, packagesBytes = 0L)), sizes)
        assertEquals(emptyList(), source.measured)
        assertEquals(mapOf(app.path to RememberedSize(12L, monday, 0L)), store.sizes)
    }

    @Test
    fun `a folder used again since it was remembered is measured and remembered again`() = runTest {
        val app = folder("App", tuesday)
        val source = source(app to 15L)
        val store = FakeDerivedDataSizeStore(mapOf(app.path to RememberedSize(12L, monday, 0L)))

        val sizes = MeasureDerivedDataUseCase(source, store)(listOf(app)).toList()

        assertEquals(listOf(MeasuredSize(app.path, 15L, packagesBytes = 0L)), sizes)
        assertEquals(listOf(app.path), source.measured)
        assertEquals(mapOf(app.path to RememberedSize(15L, tuesday, 0L)), store.sizes)
    }

    @Test
    fun `a folder without a last access is always measured and not remembered`() = runTest {
        val app = folder("App", null)
        val source = source(app to 15L)
        val store = FakeDerivedDataSizeStore()

        MeasureDerivedDataUseCase(source, store)(listOf(app)).collect()
        MeasureDerivedDataUseCase(source, store)(listOf(app)).collect()

        assertEquals(listOf(app.path, app.path), source.measured)
        assertEquals(emptyMap(), store.sizes)
    }

    @Test
    fun `folders that no longer exist are forgotten`() = runTest {
        val app = folder("App", monday)
        val gone = folder("Gone", monday)
        val store = FakeDerivedDataSizeStore(mapOf(app.path to RememberedSize(12L, monday, 0L), gone.path to RememberedSize(8L, monday, 0L)))

        MeasureDerivedDataUseCase(source(app to 12L), store)(listOf(app)).collect()

        assertEquals(setOf(app.path), store.sizes.keys)
    }

    @Test
    fun `measures at most four folders at once`() = runTest {
        val folders = (1..10).map { folder("App$it", monday) }
        val source = source(*folders.map { it to 1L }.toTypedArray())
        var running = 0
        var peak = 0
        source.onSize = {
            running++
            peak = maxOf(peak, running)
            delay(100)
            running--
        }

        val sizes = MeasureDerivedDataUseCase(source)(folders).toList()

        assertEquals(10, sizes.size)
        assertEquals(4, peak)
    }

    @Test
    fun `a folder that can't be measured doesn't stop the others`() = runTest {
        val broken = folder("Broken", monday)
        val app = folder("App", monday)
        val source = source(broken to 1L, app to 5L).apply { failSizeFor = setOf(broken.path) }
        val store = FakeDerivedDataSizeStore()

        val sizes = MeasureDerivedDataUseCase(source, store)(listOf(broken, app)).toList()

        assertEquals(setOf(MeasuredSize(broken.path, null), MeasuredSize(app.path, 5L, packagesBytes = 0L)), sizes.toSet())
        assertEquals(setOf(app.path), store.sizes.keys)
    }

    @Test
    fun `remeasuring shows remembered sizes as provisional and measures every folder`() = runTest {
        val app = folder("App", monday)
        val used = folder("Used", tuesday)
        val source = source(app to 13L, used to 9L)
        val store = FakeDerivedDataSizeStore(mapOf(app.path to RememberedSize(12L, monday, 0L), used.path to RememberedSize(8L, monday, 0L)))

        val sizes = MeasureDerivedDataUseCase(source, store)(listOf(app, used), remeasure = true).toList()

        assertEquals(listOf(MeasuredSize(app.path, 12L, provisional = true, packagesBytes = 0L), MeasuredSize(used.path, 8L, provisional = true, packagesBytes = 0L)), sizes.take(2))
        assertEquals(setOf(MeasuredSize(app.path, 13L, packagesBytes = 0L), MeasuredSize(used.path, 9L, packagesBytes = 0L)), sizes.drop(2).toSet())
        assertEquals(setOf(app.path, used.path), source.measured.toSet())
    }

    @Test
    fun `a stale remembered size isn't shown when not remeasuring`() = runTest {
        val used = folder("Used", tuesday)
        val store = FakeDerivedDataSizeStore(mapOf(used.path to RememberedSize(8L, monday, 0L)))

        val sizes = MeasureDerivedDataUseCase(source(used to 9L), store)(listOf(used)).toList()

        assertEquals(listOf(MeasuredSize(used.path, 9L, packagesBytes = 0L)), sizes)
    }

    @Test
    fun `a cancelled measurement remembers nothing`() = runTest {
        val app = folder("App", monday)
        val source = source(app to 5L)
        val started = CompletableDeferred<Unit>()
        source.onSize = {
            started.complete(Unit)
            delay(10_000)
        }
        val store = FakeDerivedDataSizeStore()

        val job = launch { MeasureDerivedDataUseCase(source, store)(listOf(app)).collect() }
        started.await()
        job.cancel()
        job.join()

        assertEquals(0, store.saves)
        assertTrue(store.sizes.isEmpty())
    }

    @Test
    fun `measures and remembers the packages with the size`() = runTest {
        val app = folder("App", monday)
        val source = source(app to 15L).apply { packageSizes[app.path] = 4L }
        val store = FakeDerivedDataSizeStore()

        val sizes = MeasureDerivedDataUseCase(source, store)(listOf(app)).toList()

        assertEquals(listOf(MeasuredSize(app.path, 15L, packagesBytes = 4L)), sizes)
        assertEquals(mapOf(app.path to RememberedSize(15L, monday, 4L)), store.sizes)
    }

    @Test
    fun `a size remembered without packages is measured again`() = runTest {
        val app = folder("App", monday)
        val source = source(app to 15L).apply { packageSizes[app.path] = 4L }
        val store = FakeDerivedDataSizeStore(mapOf(app.path to RememberedSize(15L, monday)))

        val sizes = MeasureDerivedDataUseCase(source, store)(listOf(app)).toList()

        assertEquals(listOf(MeasuredSize(app.path, 15L, packagesBytes = 4L)), sizes)
        assertEquals(listOf(app.path), source.packagesMeasured)
    }

    @Test
    fun `an unreadable store measures everything`() = runTest {
        val app = folder("App", monday)
        val store = object : DerivedDataSizeStore by FakeDerivedDataSizeStore() {
            override fun load(): Map<String, RememberedSize> = throw IllegalStateException("unreadable")
        }

        val sizes = MeasureDerivedDataUseCase(source(app to 15L), store)(listOf(app)).toList()

        assertEquals(listOf(MeasuredSize(app.path, 15L, packagesBytes = 0L)), sizes)
    }

    @Test
    fun `a remembered size without packages or for a folder without a last access is measured again`() = runTest {
        val app = folder("App", monday)
        val never = folder("Never", null)
        val source = source(app to 15L, never to 3L)
        val store = FakeDerivedDataSizeStore(mapOf(app.path to RememberedSize(12L, monday, null), never.path to RememberedSize(2L, monday, 0L)))

        MeasureDerivedDataUseCase(source, store)(listOf(app, never)).toList()

        assertEquals(setOf(app.path, never.path), source.measured.toSet())
    }

    @Test
    fun `a size whose packages can't be measured isn't remembered`() = runTest {
        val app = folder("App", monday)
        val source = object : DerivedDataSource by source(app to 15L) {
            override suspend fun packagesSize(folder: DerivedDataFolder): Long = throw java.io.IOException("unreadable")
        }
        val store = FakeDerivedDataSizeStore()

        val sizes = MeasureDerivedDataUseCase(source, store)(listOf(app)).toList()

        assertEquals(listOf(MeasuredSize(app.path, 15L, packagesBytes = null)), sizes)
        assertTrue(store.sizes.isEmpty())
    }
}
