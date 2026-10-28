package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.namedClaudeCommand
import app.luxion.shogunai.domain.model.uniqueSessionName
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HerdrAgentLauncher(
    private val herdr: HerdrClient,
    private val timeout: Duration = AGENT_START_SECONDS.seconds,
    private val pollInterval: Duration = 500.milliseconds,
    private val registry: ClaudeSessionRegistry? = null,
    private val isClaudeProcessAlive: (Long) -> Boolean = ::isLiveClaudeProcess,
) {

    suspend fun focusLiveAgent(workspaceId: String, folder: String? = null, expectedName: String? = null): HerdrOpening? {
        val pane = herdr.listPanes(workspaceId).firstOrNull { it.hasLiveAgent } ?: return null
        herdr.focusAgent(pane.paneId)
        val sessionName = if (pane.agent == CLAUDE && folder != null) focusedSessionName(folder, expectedName) else null
        return HerdrOpening(HerdrOutcome.FOCUSED, pane.paneId, pane.agent, sessionName)
    }

    suspend fun launch(
        workspaceId: String,
        paneId: String,
        command: String,
        agent: String,
        outcome: HerdrOutcome,
        sessionName: String? = null,
        folder: String? = null,
    ): HerdrOpening {
        val name = sessionName?.takeIf { namedClaudeCommand(command, it) != null }?.let(::freeName)
        val typed = name?.let { namedClaudeCommand(command, it) } ?: command
        herdr.runInPane(paneId, typed)
        withTimeoutOrNull(timeout) {
            while (!hasLiveAgent(workspaceId, paneId)) delay(pollInterval)
        } ?: throw WorktreeError.HerdrAgentNotStarted(paneId, typed)
        val confirmed = if (name != null && folder != null) awaitSession(name, folder) else null
        return HerdrOpening(outcome, paneId, agent, confirmed)
    }

    private suspend fun hasLiveAgent(workspaceId: String, paneId: String): Boolean =
        herdr.listPanes(workspaceId).any { it.paneId == paneId && it.hasLiveAgent }

    private fun freeName(name: String): String =
        uniqueSessionName(name, liveSessions().mapNotNull { it.name }.toSet())

    private suspend fun awaitSession(name: String, folder: String): String? {
        if (registry == null) return null
        return withTimeoutOrNull(timeout) {
            while (liveSessions().none { it.name == name && sameDirectory(it.cwd, folder) }) delay(pollInterval)
            name
        }
    }

    private fun focusedSessionName(folder: String, expectedName: String?): String? {
        val sessions = liveSessions().filter { it.name != null && sameDirectory(it.cwd, folder) }
        return sessions.firstOrNull { it.name == expectedName }?.name
            ?: sessions.maxByOrNull { it.startedAt ?: Long.MIN_VALUE }?.name
    }

    private fun liveSessions(): List<ClaudeSession> {
        val source = registry ?: return emptyList()
        return runCatching { source.list() }.getOrDefault(emptyList())
            .filter { session -> runCatching { isClaudeProcessAlive(session.pid) }.getOrDefault(true) }
    }

    private companion object {
        const val CLAUDE = "claude"
    }
}

fun isLiveClaudeProcess(pid: Long): Boolean {
    val handle = ProcessHandle.of(pid).orElse(null) ?: return false
    if (!handle.isAlive) return false
    val command = handle.info().command().orElse(null) ?: return true
    return command.substringAfterLast('/').substringAfterLast('\\') in setOf("claude", "claude.exe")
}
