package app.luxion.shogunai.ui.worktree.inspector

import app.luxion.shogunai.domain.model.AgentActivity
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.ObservedPort
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.model.portKeyLabel
import app.luxion.shogunai.domain.usecase.EnvironmentFormat
import app.luxion.shogunai.domain.usecase.formatEnvironment
import app.luxion.shogunai.ui.components.Property
import app.luxion.shogunai.ui.components.SectionState
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.worktree.ProvisioningState
import app.luxion.shogunai.ui.worktree.WorktreeStatusState
import app.luxion.shogunai.ui.worktree.mergedInto
import app.luxion.shogunai.ui.worktree.relativeTime
import java.time.Instant

object InspectorSectionId {
    const val STATUS = "status"
    const val AGENT = "agent"
    const val ENVIRONMENT = "environment"
    const val RESOURCES = "resources"
    const val PROVISIONING = "provisioning"
}

fun statusProperties(status: WorktreeStatus, mainBranchName: String?, now: Instant, strings: Strings): List<Property> =
    listOfNotNull(
        Property(strings.changesKey, status.uncommittedChanges.toString()),
        status.upstream?.let { Property(strings.upstreamKey, it.bothArrows()) },
        baseProperty(status, mainBranchName, strings),
        when {
            status.isNotStarted -> Property(strings.lastCommitKey, strings.notStarted, isData = false)
            else -> status.lastCommitAt?.let { Property(strings.lastCommitKey, relativeTime(it, now, strings), isData = false) }
        },
        status.createdAt?.let { Property(strings.createdTitle, relativeTime(it, now, strings), isData = false) },
    )

private fun baseProperty(status: WorktreeStatus, mainBranchName: String?, strings: Strings): Property? {
    val mergedInto = status.mergedInto(mainBranchName)
    val baseName = status.baseName ?: mainBranchName
    val base = status.base
    return when {
        mergedInto != null -> Property(strings.versusBranchKey(mergedInto), strings.mergedLabel, isData = false)
        baseName != null && base != null -> Property(strings.versusBranchKey(baseName), base.bothArrows())
        else -> null
    }
}

fun statusSummary(status: WorktreeStatus, strings: Strings): String? =
    listOfNotNull(
        status.uncommittedChanges.takeIf { it > 0 }?.let(strings::uncommittedChanges),
        status.upstream?.ahead?.takeIf { it > 0 }?.let { "↑$it" },
    ).joinToString(" · ").ifEmpty { null }

fun statusSectionState(state: WorktreeStatusState?, strings: Strings): SectionState = when (state) {
    null, WorktreeStatusState.Loading -> SectionState.Loading
    WorktreeStatusState.Unavailable -> SectionState.Empty(strings.statusUnavailable)
    is WorktreeStatusState.Loaded -> SectionState.Content
}

fun agentSectionState(agents: Map<String, List<RunningAgent>>?, worktree: Worktree, runsWith: String, strings: Strings): SectionState = when {
    agents == null -> SectionState.Loading
    agents[worktree.path].isNullOrEmpty() -> SectionState.Empty(strings.noAgentRunningRunsWith(runsWith))
    else -> SectionState.Content
}

fun runsWithLabel(agent: String, runner: String): String = "$agent · $runner"

fun environmentProperties(
    environment: WorktreeEnvironment?,
    ports: Map<String, Int>,
    listening: Set<Int> = emptySet(),
    markListening: (String) -> String = { it },
): List<Property> =
    ports.entries.filterNot { it.key == EMULATOR_PORT_KEY }.sortedBy { it.value }
        .map { (key, port) ->
            val label = portKeyLabel(key)
            Property(if (port in listening) markListening(label) else label, port.toString(), copyable = true)
        } +
        environment?.variables.orEmpty().map { (name, value) -> Property(name, value, copyable = true) }

fun environmentSummary(environment: WorktreeEnvironment?, ports: Map<String, Int>, strings: Strings): String? =
    strings.environmentSummary(
        ports.keys.count { it != EMULATOR_PORT_KEY },
        environment?.variables.orEmpty().size,
    ).ifEmpty { null }

fun environmentSectionState(
    environment: WorktreeEnvironment?,
    ports: Map<String, Int>,
    observed: List<ObservedPort> = emptyList(),
): SectionState =
    if (environment?.variables.isNullOrEmpty() && ports.isEmpty() && environment?.awaitingApproval.isNullOrEmpty() && observed.isEmpty()) {
        SectionState.NotApplicable
    } else {
        SectionState.Content
    }

fun provisioningSectionState(worktree: Worktree, state: ProvisioningState?, strings: Strings): SectionState = when {
    worktree.isMain || worktree.isPrunable -> SectionState.NotApplicable
    state == ProvisioningState.Unavailable -> SectionState.Empty(strings.provisioningUnavailable)
    state is ProvisioningState.Checked && state.drift.isNotEmpty() -> SectionState.Content
    else -> SectionState.NotApplicable
}

fun environmentAsDotenv(environment: WorktreeEnvironment): String =
    formatEnvironment(environment, EnvironmentFormat.DOTENV)

fun portsLabel(ports: Map<String, Int>): String =
    ports.entries.filterNot { it.key == EMULATOR_PORT_KEY }.sortedBy { it.value }
        .joinToString(" · ") { (key, port) -> "${portKeyLabel(key)} $port" }

fun observedPortLabel(port: ObservedPort): String =
    listOfNotNull(port.port.toString(), port.process, port.url).joinToString(" · ")

const val DETAIL_TEXT_MAX_LINES = 3

fun agentActivityLines(activity: AgentActivity, strings: Strings): List<String> =
    listOfNotNull(
        activity.waitingOn?.let { strings.agentWaitingOn(it.name, it.input) },
        activity.prompt?.let(strings::agentPrompt),
        activity.lastMessage?.let(strings::agentLastMessage),
    ).map { shortened(it) }

fun shortened(text: String, maxLines: Int = DETAIL_TEXT_MAX_LINES): String {
    val lines = text.lines()
    return if (lines.size <= maxLines) text else lines.take(maxLines).joinToString("\n") + "…"
}

private fun Divergence.bothArrows(): String = "↑$ahead ↓$behind"
