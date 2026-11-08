package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.io.ProcessEnvironmentReader
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.namedClaudeCommand
import app.luxion.shogunai.domain.model.uniqueSessionName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ClaudeSessionNaming(
    private val registry: ClaudeSessionRegistry? = null,
    private val isClaudeProcessAlive: (Long) -> Boolean = ::isLiveClaudeProcess,
    private val timeout: Duration = AGENT_START_SECONDS.seconds,
    private val pollInterval: Duration = 500.milliseconds,
    private val processEnvironment: ProcessEnvironmentReader? = null,
) {

    fun freeName(command: String, sessionName: String?): String? =
        sessionName?.takeIf { namedClaudeCommand(command, it) != null }
            ?.let { uniqueSessionName(it, liveSessions().mapNotNull { session -> session.name }.toSet()) }

    fun named(command: String, name: String?): String = name?.let { namedClaudeCommand(command, it) } ?: command

    suspend fun awaitSession(name: String, folder: String): String? {
        if (registry == null) return null
        return withTimeoutOrNull(timeout) {
            while (!hasSession(name, folder)) delay(pollInterval)
            name
        }
    }

    fun hasSession(name: String, folder: String): Boolean = liveSessionsIn(folder).any { it.name == name }

    fun liveSessionsIn(folder: String): List<ClaudeSession> = liveSessions().filter { sameDirectory(it.cwd, folder) }

    suspend fun terminalOf(session: ClaudeSession): String? {
        val reader = processEnvironment ?: return null
        return try {
            reader.variable(session.pid, ORCA_TERMINAL_HANDLE)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
    }

    fun focusedSessionName(folder: String, expectedName: String?): String? {
        val sessions = liveSessions().filter { it.name != null && sameDirectory(it.cwd, folder) }
        val focused = sessions.firstOrNull { it.name == expectedName } ?: sessions.maxByOrNull { it.startedAt ?: Long.MIN_VALUE }
        return focused?.name
    }

    private fun liveSessions(): List<ClaudeSession> {
        val source = registry ?: return emptyList()
        return runCatching { source.list() }.getOrDefault(emptyList())
            .filter { session -> runCatching { isClaudeProcessAlive(session.pid) }.getOrDefault(true) }
    }

    private companion object {
        const val ORCA_TERMINAL_HANDLE = "ORCA_TERMINAL_HANDLE"
    }
}

fun isLiveClaudeProcess(pid: Long): Boolean = ProcessHandle.of(pid).map(::isLiveClaudeProcess).orElse(false)

internal fun isLiveClaudeProcess(handle: ProcessHandle): Boolean {
    if (!handle.isAlive) return false
    val command = handle.info().command().orElse(null) ?: return true
    return command.substringAfterLast('/').substringAfterLast('\\') in setOf("claude", "claude.exe")
}
