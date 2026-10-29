package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.InstallationReport
import kotlinx.coroutines.CancellationException

sealed interface InstallationCheckResult {
    data object Running : InstallationCheckResult
    data class Done(val report: InstallationReport) : InstallationCheckResult
    data class Failed(val error: Throwable) : InstallationCheckResult
}

class InstallationCheckState(private val check: suspend () -> InstallationReport) {
    var result by mutableStateOf<InstallationCheckResult>(InstallationCheckResult.Running)
        private set

    suspend fun run() {
        result = InstallationCheckResult.Running
        result = try {
            InstallationCheckResult.Done(check())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            InstallationCheckResult.Failed(error)
        }
    }
}
