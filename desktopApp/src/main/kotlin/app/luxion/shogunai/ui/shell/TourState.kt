package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class TourStep { EXISTING, WELCOME, TOOL, CREATING, OPEN_AGENT }

class TourActions(
    val create: suspend () -> Result<Project>,
    val end: suspend () -> Result<Unit>,
    val existingProject: () -> Project?,
    val leftoverFolder: () -> Boolean,
    val toolAvailable: suspend () -> Boolean,
    val offered: () -> Boolean,
    val rememberOffered: () -> Unit,
)

class TourState(private val actions: TourActions) {
    var step by mutableStateOf<TourStep?>(null)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var project by mutableStateOf<Project?>(null)
        private set
    var isEndRequested by mutableStateOf(false)
        private set
    var offered by mutableStateOf(actions.offered())
        private set

    val folder: String get() = TOUR_FOLDER_LABEL

    fun showsNotice(hasProjects: Boolean): Boolean = hasProjects && !offered && step == null

    fun start() {
        markOffered()
        error = null
        val existing = actions.existingProject()
        project = existing
        step = if (existing != null || actions.leftoverFolder()) TourStep.EXISTING else TourStep.WELCOME
    }

    fun dismissNotice() = markOffered()

    suspend fun continueFromWelcome() {
        step = if (actions.toolAvailable()) TourStep.CREATING else TourStep.TOOL
        if (step == TourStep.CREATING) create()
    }

    suspend fun continueFromTool() = create()

    suspend fun restart() {
        error = null
        val ended = withContext(Dispatchers.IO) { actions.end() }
        ended.onFailure {
            error = it
            return
        }
        project = null
        continueFromWelcome()
    }

    suspend fun retry() = create()

    fun close() {
        step = null
        error = null
    }

    fun requestEnd() {
        isEndRequested = true
    }

    fun cancelEnd() {
        isEndRequested = false
    }

    suspend fun confirmEnd(): Result<Unit> {
        isEndRequested = false
        val result = withContext(Dispatchers.IO) { actions.end() }
        result.onSuccess { project = null }.onFailure { error = it }
        return result
    }

    fun dismissError() {
        error = null
    }

    private suspend fun create() {
        step = TourStep.CREATING
        error = null
        val created = withContext(Dispatchers.IO) { actions.create() }
        created.onSuccess {
            project = it
            step = TourStep.OPEN_AGENT
        }.onFailure { error = it }
    }

    private fun markOffered() {
        if (offered) return
        offered = true
        actions.rememberOffered()
    }

    private companion object {
        const val TOUR_FOLDER_LABEL = "~/.shogunai/tour"
    }
}
