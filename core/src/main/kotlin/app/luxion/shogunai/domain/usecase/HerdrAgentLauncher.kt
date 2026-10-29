package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HerdrAgentLauncher(
    private val herdr: HerdrClient,
    private val timeout: Duration = AGENT_START_SECONDS.seconds,
    private val pollInterval: Duration = 500.milliseconds,
    registry: ClaudeSessionRegistry? = null,
    isClaudeProcessAlive: (Long) -> Boolean = ::isLiveClaudeProcess,
) {
    private val naming = ClaudeSessionNaming(registry, isClaudeProcessAlive, timeout, pollInterval)

    suspend fun focusLiveAgent(workspaceId: String, folder: String? = null, expectedName: String? = null): HerdrOpening? {
        val pane = herdr.listPanes(workspaceId).firstOrNull { it.hasLiveAgent } ?: return null
        herdr.focusAgent(pane.paneId)
        val sessionName = if (pane.agent == CLAUDE && folder != null) naming.focusedSessionName(folder, expectedName) else null
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
        val name = naming.freeName(command, sessionName)
        val typed = naming.named(command, name)
        herdr.runInPane(paneId, typed)
        withTimeoutOrNull(timeout) {
            while (!hasLiveAgent(workspaceId, paneId)) delay(pollInterval)
        } ?: throw WorktreeError.HerdrAgentNotStarted(paneId, typed)
        val confirmed = if (name != null && folder != null) naming.awaitSession(name, folder) else null
        return HerdrOpening(outcome, paneId, agent, confirmed)
    }

    private suspend fun hasLiveAgent(workspaceId: String, paneId: String): Boolean =
        herdr.listPanes(workspaceId).any { it.paneId == paneId && it.hasLiveAgent }

    private companion object {
        const val CLAUDE = "claude"
    }
}
