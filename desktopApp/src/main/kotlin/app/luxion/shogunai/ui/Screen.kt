package app.luxion.shogunai.ui

import app.luxion.shogunai.domain.model.Project

sealed class Screen {
    data object ProjectList : Screen()
    data class ProjectConfigForm(val existingProject: Project? = null) : Screen()
    /**
     * @param previousWorktreesRoot Set when the project was just saved with a
     *   different base repository path or worktrees root, so the worktrees are
     *   repaired before being listed.
     */
    data class WorktreeManagement(val project: Project, val previousWorktreesRoot: String? = null) : Screen()
}
