package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.model.OrcaRepo
import app.luxion.shogunai.domain.model.OrcaWorkspace
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OrcaAgentLauncher(
    private val orca: OrcaClient,
    private val naming: ClaudeSessionNaming = ClaudeSessionNaming(),
    private val samePath: (String, String) -> Boolean = ::sameDirectory,
    private val timeout: Duration = AGENT_START_SECONDS.seconds,
    private val pollInterval: Duration = 500.milliseconds,
) {
    suspend fun ensureRunning() {
        if (orca.status().running) return
        orca.start()
        if (!orca.status().running) throw WorktreeError.OrcaNotStarted
    }

    suspend fun registeredRepo(path: String): OrcaRepo =
        orca.repos().firstOrNull { samePath(it.path, path) } ?: orca.addRepo(path)

    suspend fun focusLiveAgent(workspace: OrcaWorkspace, folder: String, sessionName: String): OrcaOpening? {
        val (terminal, agent) = orca.terminals(workspace).firstNotNullOfOrNull { terminal ->
            orca.terminalAgent(terminal.handle)?.let { terminal to it }
        } ?: return null
        orca.switchTo(terminal.handle)
        raiseWindow()
        val name = if (agent == CLAUDE) naming.focusedSessionName(folder, sessionName) else null
        return OrcaOpening(OrcaOutcome.FOCUSED, terminal.handle, agent, name)
    }

    suspend fun start(
        workspace: OrcaWorkspace,
        folder: String,
        command: String,
        agentName: String,
        sessionName: String,
        line: (String) -> String = { it },
    ): OrcaOpening {
        val name = naming.freeName(command, sessionName)
        val commandLine = line(naming.named(command, name))
        val terminal = orca.createTerminal(workspace, name ?: agentName, commandLine, focus = true)
        withTimeoutOrNull(timeout) {
            while (orca.terminalAgent(terminal.handle) == null) delay(pollInterval)
        } ?: throw WorktreeError.OrcaAgentNotStarted(terminal.handle, commandLine)
        raiseWindow()
        val confirmed = name?.let { naming.awaitSession(it, folder) }
        return OrcaOpening(OrcaOutcome.STARTED_AGENT, terminal.handle, agentName, confirmed)
    }

    private suspend fun raiseWindow() {
        try {
            orca.bringToFront()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return
        }
    }

    private companion object {
        const val CLAUDE = "claude"
    }
}
