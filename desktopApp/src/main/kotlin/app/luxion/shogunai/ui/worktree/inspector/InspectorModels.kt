package app.luxion.shogunai.ui.worktree.inspector

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
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
        mainBranchName?.let { name ->
            when {
                status.isMerged -> Property(strings.versusBranchKey(name), strings.mergedLabel, isData = false)
                status.mainBranch != null -> Property(strings.versusBranchKey(name), status.mainBranch!!.bothArrows())
                else -> null
            }
        },
        when {
            status.isNotStarted -> Property(strings.lastCommitKey, strings.notStarted, isData = false)
            else -> status.lastCommitAt?.let { Property(strings.lastCommitKey, relativeTime(it, now, strings), isData = false) }
        },
        status.createdAt?.let { Property(strings.createdTitle, relativeTime(it, now, strings), isData = false) },
    )

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

fun agentSectionState(agents: Map<String, List<RunningAgent>>?, worktree: Worktree, strings: Strings): SectionState = when {
    agents == null -> SectionState.Loading
    agents[worktree.path].isNullOrEmpty() -> SectionState.Empty(strings.noAgentRunning)
    else -> SectionState.Content
}

fun environmentProperties(environment: WorktreeEnvironment?, ports: Map<String, Int>): List<Property> =
    ports.entries.filterNot { it.key == EMULATOR_PORT_KEY }.sortedBy { it.value }
        .map { (key, port) -> Property(portKeyLabel(key), port.toString(), copyable = true) } +
        environment?.variables.orEmpty().map { (name, value) -> Property(name, value, copyable = true) }

fun environmentSummary(environment: WorktreeEnvironment?, ports: Map<String, Int>, strings: Strings): String? =
    strings.environmentSummary(
        ports.keys.count { it != EMULATOR_PORT_KEY },
        environment?.variables.orEmpty().size,
    ).ifEmpty { null }

fun environmentSectionState(environment: WorktreeEnvironment?, ports: Map<String, Int>): SectionState =
    if (environment?.variables.isNullOrEmpty() && ports.isEmpty() && environment?.awaitingApproval.isNullOrEmpty()) {
        SectionState.NotApplicable
    } else {
        SectionState.Content
    }

fun provisioningSectionState(worktree: Worktree, state: ProvisioningState?, strings: Strings): SectionState = when {
    worktree.isMain || worktree.isPrunable -> SectionState.NotApplicable
    state == null || state == ProvisioningState.Checking -> SectionState.Loading
    state == ProvisioningState.Unavailable -> SectionState.Empty(strings.provisioningUnavailable)
    else -> SectionState.Content
}

fun environmentAsDotenv(environment: WorktreeEnvironment): String =
    formatEnvironment(environment, EnvironmentFormat.DOTENV)

fun portsLabel(ports: Map<String, Int>): String =
    ports.entries.filterNot { it.key == EMULATOR_PORT_KEY }.sortedBy { it.value }
        .joinToString(" · ") { (key, port) -> "${portKeyLabel(key)} $port" }

private fun Divergence.bothArrows(): String = "↑$ahead ↓$behind"
