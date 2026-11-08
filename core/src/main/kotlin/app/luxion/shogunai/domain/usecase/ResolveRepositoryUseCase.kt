package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.WorktreeError
import java.nio.file.Path

class ResolveRepositoryUseCase(
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(directory: String): Result<String> = runCatching {
        val layout = git(directory, "rev-parse", "--path-format=absolute", "--git-common-dir", "--is-bare-repository")
        val lines = layout.stdout.lines().map { it.trim() }
        val commonDir = normalizePath(lines.first())
        if (!layout.isSuccess || commonDir.isEmpty()) throw WorktreeError.RepositoryNotFound(directory)
        if (lines.getOrNull(1) == "true") throw WorktreeError.BareRepositoryNotSupported(directory)
        if (commonDir.endsWith("/.git")) return@runCatching commonDir.removeSuffix("/.git")
        if (isBare(directory)) throw WorktreeError.BareRepositoryNotSupported(directory)
        mainWorktree(directory, commonDir)
    }

    private suspend fun isBare(directory: String): Boolean =
        git(directory, "config", "--bool", "core.bare").stdout.trim() == "true"

    private suspend fun mainWorktree(directory: String, commonDir: String): String {
        val configured = git(directory, "config", "--get", "core.worktree").stdout.trim()
        if (configured.isNotEmpty()) return normalizePath(Path.of(commonDir).resolve(configured).normalize().toString())
        val listing = git(directory, "worktree", "list", "--porcelain")
        if (!listing.isSuccess) throw WorktreeError.RepositoryNotFound(directory)
        val main = parseWorktreeList(listing.stdout).firstOrNull() ?: throw WorktreeError.RepositoryNotFound(directory)
        return main.path
    }

    private suspend fun git(directory: String, vararg args: String): CommandResult =
        executor.executeWithin(listOf("git", *args), CommandTimeouts.GIT_READ, directory)
}
