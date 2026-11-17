package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.WorktreeSimulator

data class MachineSimulator(
    val projectId: String,
    val worktreePath: String,
    val simulator: WorktreeSimulator?,
)
