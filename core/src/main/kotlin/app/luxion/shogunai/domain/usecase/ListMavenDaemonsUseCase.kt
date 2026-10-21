package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.MavenDaemonSource
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.MavenDaemon
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.Worktree

class ListMavenDaemonsUseCase(
    private val source: MavenDaemonSource,
    private val fileManager: FileManager,
) {
    suspend operator fun invoke(worktrees: List<Worktree>?, worktreesRoot: String? = null): Result<List<MavenDaemon>> = runCatching {
        source.list().map { process -> MavenDaemon(process, worktrees?.let { ownerOf(process, it, worktreesRoot) }) }
    }

    private fun ownerOf(process: MavenDaemonProcess, worktrees: List<Worktree>, worktreesRoot: String?): DaemonOwner? {
        val directory = process.workingDirectory ?: return null
        owningWorktree(directory, worktrees)?.let { return DaemonOwner.Worktree(it.path) }
        if (fileManager.exists(directory)) return DaemonOwner.OtherProject
        val insideWorktreesFolder = worktreesRoot?.let { isInside(normalizePath(directory), normalizePath(it)) } == true
        return if (insideWorktreesFolder) DaemonOwner.RemovedWorktree else DaemonOwner.OtherProject
    }
}

fun totalMavenResidentMemory(daemons: List<MavenDaemon>): Long = daemons.sumOf { it.process.residentMemoryBytes }
