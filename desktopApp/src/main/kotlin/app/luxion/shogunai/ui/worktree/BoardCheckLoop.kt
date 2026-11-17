package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.RefreshCadence
import app.luxion.shogunai.ui.WindowActivity
import app.luxion.shogunai.ui.checkInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BoardCheckLoop(
    private val scope: CoroutineScope,
    private val cadence: suspend () -> RefreshCadence,
    private val watchesWhileHidden: () -> Boolean,
    private val checkShown: suspend (RefreshCadence) -> Unit,
    private val checkHidden: suspend () -> Unit,
) {
    private val windowActivity = MutableStateFlow(WindowActivity.FOCUSED)
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            var check: Job? = null
            while (true) {
                val activity = if (watchesWhileHidden()) windowActivity.value else windowActivity.first { it.isShown }
                val current = cadence()
                val changed = withTimeoutOrNull(current.checkInterval(activity)) { windowActivity.first { it != activity } }
                if (changed != null) continue
                if (check?.isActive != true) check = launch { if (activity.isShown) checkShown(current) else checkHidden() }
            }
        }
    }

    fun setWindowActivity(activity: WindowActivity) {
        windowActivity.value = activity
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
