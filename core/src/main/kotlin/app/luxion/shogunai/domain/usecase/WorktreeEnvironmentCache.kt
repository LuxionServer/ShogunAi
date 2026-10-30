package app.luxion.shogunai.domain.usecase

import java.io.File
import java.util.concurrent.ConcurrentHashMap

class WorktreeEnvironmentCache(
    private val gitEntryOf: (worktreePath: String) -> String? = ::readGitEntry,
) {
    private val directories = ConcurrentHashMap<String, Validated>()

    internal fun directoriesOf(worktreePath: String): CachedGitDirectories? {
        val cached = directories[worktreePath] ?: return null
        return cached.directories.takeIf { gitEntryOf(worktreePath) == cached.gitEntry }
    }

    internal fun remember(worktreePath: String, value: CachedGitDirectories) {
        val gitEntry = gitEntryOf(worktreePath) ?: return
        directories[worktreePath] = Validated(gitEntry, value)
    }

    fun invalidate() {
        directories.clear()
    }

    private data class Validated(val gitEntry: String, val directories: CachedGitDirectories)
}

internal data class CachedGitDirectories(val admin: String, val common: String)

private const val GIT_DIRECTORY_ENTRY = "directory"

private fun readGitEntry(worktreePath: String): String? {
    val entry = File(worktreePath, ".git")
    return when {
        entry.isDirectory -> GIT_DIRECTORY_ENTRY
        entry.isFile -> runCatching { entry.readText() }.getOrNull()
        else -> null
    }
}
