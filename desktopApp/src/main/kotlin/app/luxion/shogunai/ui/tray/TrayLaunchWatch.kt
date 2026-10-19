package app.luxion.shogunai.ui.tray

import app.luxion.shogunai.ui.worktree.WorktreeRef

class TrayLaunchWatch {
    private val baselines = mutableMapOf<WorktreeRef, Throwable?>()

    fun started(ref: WorktreeRef, currentError: Throwable?) {
        baselines[ref] = currentError
    }

    fun failures(errors: Map<WorktreeRef, Throwable>): List<WorktreeRef> {
        val failed = baselines.filter { (ref, baseline) -> errors[ref]?.let { it !== baseline } == true }.keys.toList()
        failed.forEach(baselines::remove)
        return failed
    }
}
