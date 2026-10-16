package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.Worktree

class ListGradleDaemonsUseCase(
    private val source: GradleDaemonSource,
    private val fileManager: FileManager,
) {
    suspend operator fun invoke(worktrees: List<Worktree>?): Result<List<GradleDaemon>> = runCatching {
        source.list().map { process ->
            GradleDaemon(process, worktrees?.let { ownerOf(process, it) })
        }
    }

    private fun ownerOf(process: DaemonProcess, worktrees: List<Worktree>): DaemonOwner? {
        if (process.kind != DaemonKind.GRADLE) return null
        val directory = process.lastBuildDirectory ?: return null
        if (!fileManager.exists(directory)) return DaemonOwner.RemovedWorktree
        return owningWorktree(directory, worktrees)?.let { DaemonOwner.Worktree(it.path) } ?: DaemonOwner.OtherProject
    }
}

fun idleDaemonsOfRemovedWorktrees(
    daemons: List<GradleDaemon>,
    worktreesBeforeCleanup: List<Worktree>,
    removedPaths: List<String>,
): List<Long> {
    val removed = removedPaths.map(::normalizePath).toSet()
    return daemons
        .filter { it.process.kind == DaemonKind.GRADLE && it.process.state is DaemonState.Idle }
        .filter { daemon ->
            val directory = daemon.process.lastBuildDirectory ?: return@filter false
            val owner = owningWorktree(directory, worktreesBeforeCleanup) ?: return@filter false
            normalizePath(owner.path) in removed
        }
        .map { it.pid }
}

fun totalResidentMemory(daemons: List<GradleDaemon>): Long = daemons.sumOf { it.process.residentMemoryBytes }
