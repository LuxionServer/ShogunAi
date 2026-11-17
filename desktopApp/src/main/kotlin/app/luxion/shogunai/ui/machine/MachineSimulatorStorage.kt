package app.luxion.shogunai.ui.machine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.RuntimeDeletion
import app.luxion.shogunai.domain.model.SimulatorStorage
import app.luxion.shogunai.domain.model.StoredRuntime
import app.luxion.shogunai.domain.model.UnavailableDeletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SimulatorStorageActions(
    val supported: Boolean,
    val list: suspend () -> Result<SimulatorStorage>,
    val deleteRuntime: suspend (identifier: String) -> Result<RuntimeDeletion>,
    val deleteUnavailable: suspend () -> Result<UnavailableDeletion>,
) {
    companion object {
        private val NOT_SUPPORTED = IllegalStateException("simulator storage isn't supported here")

        val NONE = SimulatorStorageActions(
            supported = false,
            list = { Result.success(SimulatorStorage(supported = false)) },
            deleteRuntime = { Result.failure(NOT_SUPPORTED) },
            deleteUnavailable = { Result.failure(NOT_SUPPORTED) },
        )
    }
}

sealed interface SimulatorStorageReport {
    data class Runtime(val deletion: RuntimeDeletion) : SimulatorStorageReport
    data class Unavailable(val deletion: UnavailableDeletion) : SimulatorStorageReport
}

class MachineSimulatorStorage(
    private val scope: CoroutineScope,
    private val actions: SimulatorStorageActions,
) {
    val isSupported: Boolean get() = actions.supported

    var storage by mutableStateOf<MachineLoad<SimulatorStorage>>(MachineLoad.Loading)
        private set

    var isDeleting by mutableStateOf(false)
        private set

    var report by mutableStateOf<SimulatorStorageReport?>(null)
        private set

    var error by mutableStateOf<Throwable?>(null)
        private set

    suspend fun load() {
        storage = actions.list().fold({ MachineLoad.Loaded(it) }, { MachineLoad.Unavailable })
    }

    fun deleteRuntime(runtime: StoredRuntime) =
        delete { actions.deleteRuntime(runtime.image.identifier).map(SimulatorStorageReport::Runtime) }

    fun deleteUnavailable() = delete { actions.deleteUnavailable().map(SimulatorStorageReport::Unavailable) }

    private fun delete(action: suspend () -> Result<SimulatorStorageReport>) {
        if (isDeleting) return
        isDeleting = true
        report = null
        error = null
        scope.launch {
            try {
                action().onSuccess { report = it }.onFailure { error = it }
                load()
            } finally {
                isDeleting = false
            }
        }
    }

    fun dismissReport() {
        report = null
    }

    fun dismissError() {
        error = null
    }
}

internal fun Set<MachineResource>.withSimulatorStorage(storage: MachineSimulatorStorage): Set<MachineResource> =
    if (storage.isSupported && MachineResource.XCODE_DERIVED_DATA in this) this + MachineResource.IOS_SIMULATORS else this
