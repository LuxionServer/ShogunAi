package app.luxion.shogunai.domain.usecase

import java.util.concurrent.ConcurrentHashMap

class WorktreeEnvironmentCache {
    private val directories = ConcurrentHashMap<String, CachedGitDirectories>()

    internal fun directoriesOf(worktreePath: String): CachedGitDirectories? = directories[worktreePath]

    internal fun remember(worktreePath: String, value: CachedGitDirectories) {
        directories[worktreePath] = value
    }

    fun invalidate() {
        directories.clear()
    }
}

internal data class CachedGitDirectories(val admin: String, val common: String)
