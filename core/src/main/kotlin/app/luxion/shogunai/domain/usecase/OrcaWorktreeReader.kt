package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.io.OrcaWorkspaceStore
import app.luxion.shogunai.domain.model.ObservedPort
import app.luxion.shogunai.domain.model.OrcaPort
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.nio.file.Path

class OrcaWorktreeReader(
    private val orca: OrcaClient,
    private val store: OrcaWorkspaceStore = OrcaWorkspaceStore.NONE,
    private val userHome: String = System.getProperty("user.home"),
    private val now: () -> Long = System::currentTimeMillis,
    private val canonical: (String) -> String = ::canonicalDirectory,
) {
    @Volatile
    private var retryAt = 0L

    @Volatile
    private var portsRetryAt = 0L

    @Volatile
    private var lastRead: List<OrcaWorktree>? = null

    @Volatile
    private var lastPorts: List<OrcaPort>? = null

    suspend fun read(): List<OrcaWorktree>? {
        if (now() < retryAt || !orca.isInstalled()) return unavailable(backOff = false)
        val worktrees = coroutineScope {
            val running = CompletableDeferred<Boolean>()
            val ports = async { if (running.await()) readPorts() else null }
            val worktrees = withinLimit {
                val isRunning = orca.status().running
                running.complete(isRunning)
                if (isRunning) orca.agentsByWorktree() else null
            }
            running.complete(false)
            lastPorts = ports.await()
            worktrees
        } ?: return unavailable(backOff = true)
        lastRead = worktrees
        rememberWorkspacesRoot(worktrees)
        return worktrees
    }

    fun lastRows(): List<OrcaWorktree>? = lastRead

    fun ports(worktrees: List<Worktree>): Map<String, List<ObservedPort>>? {
        val byWorktree = lastPorts?.groupBy { canonical(it.worktreePath) } ?: return null
        return worktrees.associate { worktree ->
            worktree.path to byWorktree[canonical(worktree.path)].orEmpty()
                .sortedWith(compareBy(OrcaPort::port, { it.url == null }))
                .distinctBy(OrcaPort::port)
                .map { ObservedPort(it.port, it.process, it.url) }
        }
    }

    private suspend fun readPorts(): List<OrcaPort>? {
        if (now() < portsRetryAt) return null
        return withinLimit { orca.ports() } ?: run {
            portsRetryAt = now() + BACK_OFF_MILLIS
            null
        }
    }

    private suspend fun <T> withinLimit(read: suspend () -> T?): T? = try {
        withTimeout(TIMEOUT_MILLIS) { read() }
    } catch (error: CancellationException) {
        if (error is TimeoutCancellationException) null else throw error
    } catch (error: Exception) {
        null
    }

    fun origins(): OrcaOrigins {
        val workspacesRoot = runCatching { store.workspacesRoot() }.getOrNull() ?: defaultWorkspacesRoot()
        val created = lastRead?.filter { it.createdByOrca }?.map { canonical(it.path) }?.toSet()
        return OrcaOrigins(created, workspacesRoot, canonical)
    }

    private fun unavailable(backOff: Boolean): List<OrcaWorktree>? {
        if (backOff) retryAt = now() + BACK_OFF_MILLIS
        lastRead = null
        lastPorts = null
        return null
    }

    private fun rememberWorkspacesRoot(worktrees: List<OrcaWorktree>) {
        val root = worktrees.firstOrNull { it.createdByOrca }
            ?.let { runCatching { Path.of(it.path).parent?.parent?.toString() }.getOrNull() }
            ?: return
        runCatching { store.remember(normalizePath(root)) }
    }

    private fun defaultWorkspacesRoot(): String = "${normalizePath(userHome)}/orca/workspaces"

    private companion object {
        const val TIMEOUT_MILLIS = 2_000L
        const val BACK_OFF_MILLIS = 30_000L
    }
}

data class OrcaOrigins(
    val created: Set<String>?,
    val workspacesRoot: String,
    private val canonical: (String) -> String = ::canonicalDirectory,
) {
    fun createdByOrca(path: String): Boolean =
        if (created != null) canonical(path) in created else isInside(normalizePath(path), normalizePath(workspacesRoot))
}
