package app.luxion.shogunai.domain.usecase

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
) {

    suspend fun focusLiveAgent(workspaceId: String): HerdrOpening? {
        val pane = herdr.listPanes(workspaceId).firstOrNull { it.hasLiveAgent } ?: return null
        herdr.focusAgent(pane.paneId)
        return HerdrOpening(HerdrOutcome.FOCUSED, pane.paneId, pane.agent)
    }

    suspend fun launch(
        workspaceId: String,
        paneId: String,
        command: String,
        agent: String,
        outcome: HerdrOutcome,
    ): HerdrOpening {
        herdr.runInPane(paneId, command)
        withTimeoutOrNull(timeout) {
            while (!hasLiveAgent(workspaceId, paneId)) delay(pollInterval)
        } ?: throw WorktreeError.HerdrAgentNotStarted(paneId, command)
        return HerdrOpening(outcome, paneId, agent)
    }

    private suspend fun hasLiveAgent(workspaceId: String, paneId: String): Boolean =
        herdr.listPanes(workspaceId).any { it.paneId == paneId && it.hasLiveAgent }
}
