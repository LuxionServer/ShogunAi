package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.joinPath

class TourLocation(home: String = System.getProperty("user.home")) {
    val root: String = joinPath(home, ".shogunai", "tour")
    val repository: String = joinPath(root, REPOSITORY_FOLDER)
    val remote: String = joinPath(root, REMOTE_FOLDER)
    val worktrees: String = joinPath(root, WORKTREES_FOLDER)

    fun worktree(name: String): String = joinPath(worktrees, name)

    fun isTour(project: Project): Boolean = sameDirectory(project.config.baseRepositoryPath, repository)

    private companion object {
        const val REPOSITORY_FOLDER = "sample-repo"
        const val REMOTE_FOLDER = "origin.git"
        const val WORKTREES_FOLDER = "worktrees"
    }
}
