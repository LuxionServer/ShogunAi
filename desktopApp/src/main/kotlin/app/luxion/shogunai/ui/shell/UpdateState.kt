package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.executor.ClipboardWriter
import app.luxion.shogunai.domain.io.UpdatePreferenceRepository
import app.luxion.shogunai.domain.model.UpdateCheck
import app.luxion.shogunai.domain.model.UpdateCheckMode
import kotlinx.coroutines.CancellationException

sealed interface ManualUpdateResult {
    data object Checking : ManualUpdateResult
    data class Finished(val check: UpdateCheck) : ManualUpdateResult
    data class Failed(val error: Throwable) : ManualUpdateResult
}

class UpdateState(
    private val check: suspend (UpdateCheckMode) -> Result<UpdateCheck>,
    private val preferences: UpdatePreferenceRepository,
    private val clipboard: ClipboardWriter,
    private val openUrl: (String) -> Unit,
) {
    var notice by mutableStateOf<UpdateCheck.Available?>(null)
        private set
    var manualResult by mutableStateOf<ManualUpdateResult?>(null)
        private set
    var checkAutomatically by mutableStateOf(preferences.load().checkAutomatically)
        private set

    suspend fun checkInBackground() {
        notice = runCheck(UpdateCheckMode.AUTOMATIC).getOrNull() as? UpdateCheck.Available
    }

    suspend fun checkNow() {
        manualResult = ManualUpdateResult.Checking
        manualResult = runCheck(UpdateCheckMode.MANUAL).fold(
            onSuccess = { ManualUpdateResult.Finished(it) },
            onFailure = { ManualUpdateResult.Failed(it) },
        )
    }

    fun closeManualResult() {
        manualResult = null
    }

    fun dismissNotice() {
        notice = null
    }

    fun skipVersion() {
        notice?.let { preferences.skip(it.release.version) }
        notice = null
    }

    fun changeCheckAutomatically(enabled: Boolean) {
        checkAutomatically = enabled
        preferences.setCheckAutomatically(enabled)
    }

    fun copy(command: String) {
        clipboard.write(command)
    }

    fun open(url: String) {
        openUrl(url)
    }

    private suspend fun runCheck(mode: UpdateCheckMode): Result<UpdateCheck> = try {
        check(mode)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
