package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.usecase.ClaudeCodeHooks
import app.luxion.shogunai.domain.usecase.HooksInstallState
import app.luxion.shogunai.domain.usecase.HooksPreview
import app.luxion.shogunai.domain.usecase.HooksStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class HooksAction { INSTALL, UPDATE, REMOVE }

data class PendingHooksChange(val action: HooksAction, val path: String, val preview: HooksPreview)

class ClaudeHooksState(
    private val hooks: ClaudeCodeHooks,
    private val settingsPath: () -> String,
    private val isShogunOnPath: () -> Boolean,
    private val noticeDismissedStore: () -> Boolean,
    private val rememberNoticeDismissed: () -> Unit,
) {
    var status by mutableStateOf<HooksStatus?>(null)
        private set
    var pending by mutableStateOf<PendingHooksChange?>(null)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var shogunOnPath by mutableStateOf(true)
        private set
    var noticeDismissed by mutableStateOf(noticeDismissedStore())
        private set

    val installAction: HooksAction? get() = when (status?.state) {
        HooksInstallState.MISSING -> HooksAction.INSTALL
        HooksInstallState.OUTDATED -> HooksAction.UPDATE
        else -> null
    }

    val canRemove: Boolean get() = status?.state.let { it == HooksInstallState.INSTALLED || it == HooksInstallState.OUTDATED }

    val suggestsInstalling: Boolean get() = !noticeDismissed && status?.state.let { it != null && it != HooksInstallState.INSTALLED }

    suspend fun refresh() {
        val path = settingsPath()
        val read = withContext(Dispatchers.IO) { hooks.status(path) to isShogunOnPath() }
        status = read.first.getOrNull()
        error = read.first.exceptionOrNull()
        shogunOnPath = read.second
    }

    fun request(action: HooksAction) {
        val path = settingsPath()
        val preview = if (action == HooksAction.REMOVE) hooks.previewUninstall(path) else hooks.previewInstall(path)
        preview.onSuccess {
            error = null
            pending = PendingHooksChange(action, path, it)
        }.onFailure { error = it }
    }

    fun cancel() {
        pending = null
    }

    fun confirm() {
        val change = pending ?: return
        pending = null
        val result = if (change.action == HooksAction.REMOVE) hooks.uninstall(change.path) else hooks.install(change.path)
        result.onFailure { error = it }
        status = hooks.status(change.path).getOrNull() ?: status
    }

    fun dismissNotice() {
        noticeDismissed = true
        rememberNoticeDismissed()
    }

    fun dismissError() {
        error = null
    }
}
