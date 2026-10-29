package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DerivedDataSizeStore
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.MeasuredSize
import app.luxion.shogunai.domain.model.RememberedSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

class MeasureDerivedDataUseCase(
    private val source: DerivedDataSource,
    private val store: DerivedDataSizeStore = DerivedDataSizeStore.NONE,
    private val concurrency: Int = MAX_PARALLEL_MEASUREMENTS,
) {
    operator fun invoke(folders: List<DerivedDataFolder>, remeasure: Boolean = false): Flow<MeasuredSize> = channelFlow {
        val remembered = runCatching { store.load() }.getOrDefault(emptyMap())
        val known = ConcurrentHashMap<String, RememberedSize>()
        val pending = mutableListOf<DerivedDataFolder>()
        folders.forEach { folder ->
            val previous = remembered[folder.path]
            val valid = previous?.takeIf { folder.lastAccessed != null && it.lastAccessed == folder.lastAccessed && it.packagesBytes != null }
            if (previous != null && (valid != null || remeasure)) {
                send(MeasuredSize(folder.path, previous.sizeBytes, provisional = remeasure, packagesBytes = previous.packagesBytes))
            }
            if (valid != null && !remeasure) known[folder.path] = valid else pending += folder
        }
        val permits = Semaphore(concurrency)
        coroutineScope {
            pending.forEach { folder ->
                launch {
                    permits.withPermit {
                        val size = measure { source.size(folder) }
                        val packages = size?.let { measure { source.packagesSize(folder) } }
                        val lastAccessed = folder.lastAccessed
                        if (size != null && packages != null && lastAccessed != null) {
                            known[folder.path] = RememberedSize(size, lastAccessed, packages)
                        }
                        send(MeasuredSize(folder.path, size, packagesBytes = packages))
                    }
                }
            }
        }
        runCatching { store.save(known.toMap()) }
    }

    private suspend fun measure(size: suspend () -> Long): Long? = try {
        size()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    private companion object {
        const val MAX_PARALLEL_MEASUREMENTS = 4
    }
}
