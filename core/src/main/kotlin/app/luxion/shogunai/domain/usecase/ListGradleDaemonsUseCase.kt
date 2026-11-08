package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.model.AgentSessionRef
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.Worktree

class ListGradleDaemonsUseCase(
    private val source: GradleDaemonSource,
    private val fileManager: FileManager,
    private val sessionStore: AgentSessionStore? = null,
) {
    suspend operator fun invoke(
        worktrees: List<Worktree>?,
        listed: List<DaemonProcess>? = null,
        sessionWorktrees: suspend () -> List<String> = { worktrees.orEmpty().map { it.path } },
    ): Result<List<GradleDaemon>> = runCatching {
        val processes = listed ?: source.list()
        val sessions = if (processes.any { it.holder?.client?.ancestors.orEmpty().isNotEmpty() }) sessionsByPid(sessionWorktrees()) else emptyMap()
        processes.map { process ->
            GradleDaemon(withSession(process, sessions), worktrees?.let { ownerOf(process, it) })
        }
    }

    private fun sessionsByPid(worktreePaths: List<String>): Map<Long, AgentSessionRef> {
        val store = sessionStore ?: return emptyMap()
        return worktreePaths.distinct().flatMap { path ->
            runCatching { store.list(path) }.getOrDefault(emptyList())
                .mapNotNull { record -> record.pid?.let { it to AgentSessionRef(record.sessionId, record.agent, path) } }
        }.toMap()
    }

    private fun withSession(process: DaemonProcess, sessions: Map<Long, AgentSessionRef>): DaemonProcess {
        val holder = process.holder ?: return process
        val client = holder.client ?: return process
        val session = client.ancestors.firstNotNullOfOrNull { sessions[it] } ?: return process
        return process.copy(holder = holder.copy(client = client.copy(session = session)))
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
