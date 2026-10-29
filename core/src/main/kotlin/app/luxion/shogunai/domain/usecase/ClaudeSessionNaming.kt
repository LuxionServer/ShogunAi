package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.namedClaudeCommand
import app.luxion.shogunai.domain.model.uniqueSessionName
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
) {

    fun freeName(command: String, sessionName: String?): String? =
        sessionName?.takeIf { namedClaudeCommand(command, it) != null }
            ?.let { uniqueSessionName(it, liveSessions().mapNotNull { session -> session.name }.toSet()) }

    fun named(command: String, name: String?): String = name?.let { namedClaudeCommand(command, it) } ?: command

    suspend fun awaitSession(name: String, folder: String): String? {
        if (registry == null) return null
        return withTimeoutOrNull(timeout) {
            while (liveSessions().none { it.name == name && sameDirectory(it.cwd, folder) }) delay(pollInterval)
            name
        }
    }

    fun focusedSessionName(folder: String, expectedName: String?): String? {
        val sessions = liveSessions().filter { it.name != null && sameDirectory(it.cwd, folder) }
        return sessions.firstOrNull { it.name == expectedName }?.name
            ?: sessions.maxByOrNull { it.startedAt ?: Long.MIN_VALUE }?.name
    }

    private fun liveSessions(): List<ClaudeSession> {
        val source = registry ?: return emptyList()
        return runCatching { source.list() }.getOrDefault(emptyList())
            .filter { session -> runCatching { isClaudeProcessAlive(session.pid) }.getOrDefault(true) }
    }
}

fun isLiveClaudeProcess(pid: Long): Boolean {
    val handle = ProcessHandle.of(pid).orElse(null) ?: return false
    if (!handle.isAlive) return false
    val command = handle.info().command().orElse(null) ?: return true
    return command.substringAfterLast('/').substringAfterLast('\\') in setOf("claude", "claude.exe")
}
