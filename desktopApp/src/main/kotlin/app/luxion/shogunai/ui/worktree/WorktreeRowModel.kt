package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.domain.usecase.ActivityDate
import app.luxion.shogunai.domain.usecase.ActivityKind
import app.luxion.shogunai.domain.usecase.activityDate
import app.luxion.shogunai.domain.usecase.worktreeActivity
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.Status
import java.time.Duration
import java.time.Instant

data class WorktreeRowModel(
    val badge: StateBadge,
    val execution: ExecutionCell,
    val name: NameCell,
    val git: GitCell,
    val time: TimeCell,
    val isFolderMissing: Boolean,
)

enum class StateBadgeKind(val status: Status?) {
    NEEDS_YOU(Status.WARNING),
    AGENT_WORKING(Status.INFO),
    NEW(null),
    STOPPED(Status.NEUTRAL),
    MERGED(Status.SUCCESS),
    INACTIVE(Status.NEUTRAL),
    FOLDER_MISSING(Status.DANGER),
    OTHER(Status.NEUTRAL),
}

data class StateBadge(val kind: StateBadgeKind, val label: String)

data class AgentLine(
    val label: String,
    val name: String,
    val state: AgentState?,
    val status: Status,
    val moreCount: Int,
    val tooltip: String,
)

data class DeviceLine(val label: String, val status: Status, val isSimulator: Boolean)

data class ExecutionCell(val agent: AgentLine?, val device: DeviceLine?)

data class NameCell(val branch: String, val folder: String?, val origin: String?, val project: String?)

enum class GitCellState { LOADING, UNAVAILABLE, READY }

data class GitCell(
    val state: GitCellState,
    val changes: String?,
    val changesTooltip: String?,
    val secondLine: String?,
    val secondLineTooltip: String?,
    val unavailableTooltip: String?,
)

data class TimeCell(val activity: String?, val marks: List<String>)

fun StateBadgeKind.label(strings: Strings): String = when (this) {
    StateBadgeKind.NEEDS_YOU -> strings.groupNeedsYou
    StateBadgeKind.AGENT_WORKING -> strings.groupAgentWorking
    StateBadgeKind.NEW -> strings.groupNew
    StateBadgeKind.STOPPED -> strings.groupStopped
    StateBadgeKind.MERGED -> strings.groupMerged
    StateBadgeKind.INACTIVE -> strings.groupInactive
    StateBadgeKind.FOLDER_MISSING -> strings.groupFolderMissing
    StateBadgeKind.OTHER -> strings.groupOther
}

fun Activity.badgeKind(): StateBadgeKind = when (this) {
    Activity.NeedsYou -> StateBadgeKind.NEEDS_YOU
    Activity.AgentWorking -> StateBadgeKind.AGENT_WORKING
    Activity.New -> StateBadgeKind.NEW
    Activity.Stopped -> StateBadgeKind.STOPPED
    Activity.Merged -> StateBadgeKind.MERGED
    Activity.Inactive -> StateBadgeKind.INACTIVE
    Activity.Missing -> StateBadgeKind.FOLDER_MISSING
    Activity.Idle, Activity.Unknown -> StateBadgeKind.OTHER
}

internal fun originLabel(origin: WorktreeOrigin, strings: Strings): String = when (origin) {
    WorktreeOrigin.SHOGUNAI -> strings.originShogunAi
    WorktreeOrigin.CLAUDE_CODE -> strings.originClaudeCode
    WorktreeOrigin.HERDR -> strings.originHerdr
    WorktreeOrigin.OTHER -> strings.originOther
}

internal fun Divergence.arrows(): String? =
    listOfNotNull("↑$ahead".takeIf { ahead > 0 }, "↓$behind".takeIf { behind > 0 })
        .joinToString(" ")
        .ifEmpty { null }

fun worktreeRowModel(
    worktree: Worktree,
    status: WorktreeStatusState?,
    mainBranchName: String?,
    agents: List<RunningAgent>,
    emulator: WorktreeEmulator?,
    simulator: WorktreeSimulator?,
    origin: WorktreeOrigin?,
    projectName: String?,
    inactiveAfter: Duration,
    now: Instant,
    strings: Strings,
): WorktreeRowModel {
    val loaded = (status as? WorktreeStatusState.Loaded)?.status
    val kind = worktreeActivity(worktree, loaded, agents, now, inactiveAfter).badgeKind()
    return WorktreeRowModel(
        badge = StateBadge(kind, kind.label(strings)),
        execution = ExecutionCell(
            agent = agentLine(agents, strings),
            device = deviceLine(emulator, simulator, strings),
        ),
        name = nameCell(worktree, origin, projectName, strings),
        git = gitCell(status, loaded, mainBranchName, strings),
        time = TimeCell(
            activity = activityDate(worktree, loaded)?.let { activityLabel(it, now, strings) },
            marks = listOfNotNull(
                strings.mainWorktree.takeIf { worktree.isMain },
                strings.locked.takeIf { worktree.isLocked },
            ),
        ),
        isFolderMissing = worktree.isPrunable,
    )
}

internal fun activityLabel(date: ActivityDate, now: Instant, strings: Strings): String {
    val relative = relativeTime(date.at, now, strings)
    return when (date.kind) {
        ActivityKind.COMMIT -> strings.commitAgo(relative)
        ActivityKind.CREATED -> strings.createdAgo(relative)
    }
}

private fun nameCell(worktree: Worktree, origin: WorktreeOrigin?, projectName: String?, strings: Strings): NameCell {
    val branch = worktree.branch ?: strings.detachedHead
    val folder = worktree.path.replace('\\', '/').trimEnd('/').substringAfterLast('/')
    return NameCell(
        branch = branch,
        folder = folder.takeIf { it != worktree.branch?.substringAfterLast('/') },
        origin = origin?.let { originLabel(it, strings) },
        project = projectName,
    )
}

private val AgentUrgency = listOf(
    AgentState.BLOCKED,
    AgentState.DONE,
    AgentState.WORKING,
    null,
    AgentState.IDLE,
)

private fun agentLine(agents: List<RunningAgent>, strings: Strings): AgentLine? {
    val urgent = agents.minByOrNull { AgentUrgency.indexOf(it.state) } ?: return null
    val displayName = urgent.name.replaceFirstChar { it.uppercase() }
    return AgentLine(
        label = strings.agentLabel(displayName, urgent.state),
        name = displayName,
        state = urgent.state,
        status = urgent.state.status(),
        moreCount = agents.size - 1,
        tooltip = agents.sortedBy { AgentUrgency.indexOf(it.state) }.joinToString("\n") {
            "${it.name.replaceFirstChar { letter -> letter.uppercase() }}: ${strings.agentStateName(it.state)}"
        },
    )
}

private fun deviceLine(emulator: WorktreeEmulator?, simulator: WorktreeSimulator?, strings: Strings): DeviceLine? {
    activeEmulator(emulator)?.let { return DeviceLine(emulatorStateLabel(it, strings), it.status(), isSimulator = false) }
    activeSimulator(simulator)?.let { return DeviceLine(simulatorStateLabel(it, strings), it.status(), isSimulator = true) }
    return null
}

private fun gitCell(
    state: WorktreeStatusState?,
    status: WorktreeStatus?,
    mainBranchName: String?,
    strings: Strings,
): GitCell {
    if (state == WorktreeStatusState.Loading) return GitCell(GitCellState.LOADING, null, null, null, null, null)
    if (status == null) {
        val unavailable = state == WorktreeStatusState.Unavailable
        return GitCell(
            GitCellState.UNAVAILABLE, null, null, null, null,
            unavailableTooltip = strings.statusUnavailable.takeIf { unavailable },
        )
    }
    val merged = status.isMerged && mainBranchName != null
    val changes = status.uncommittedChanges.takeIf { it > 0 }
    val secondLine = if (merged) strings.mergedLabel else status.mainBranch?.arrows()
    return GitCell(
        state = GitCellState.READY,
        changes = changes?.let { "✎ $it" },
        changesTooltip = changes?.let(strings::uncommittedChanges),
        secondLine = secondLine,
        secondLineTooltip = when {
            merged -> strings.mergedInto(mainBranchName!!)
            secondLine != null -> listOfNotNull(secondLine, mainBranchName).joinToString(" ")
            else -> null
        },
        unavailableTooltip = null,
    )
}

internal fun AgentState?.status(): Status = when (this) {
    AgentState.BLOCKED -> Status.WARNING
    AgentState.DONE -> Status.SUCCESS
    AgentState.IDLE -> Status.NEUTRAL
    AgentState.WORKING, null -> Status.INFO
}

private fun activeEmulator(emulator: WorktreeEmulator?): EmulatorState? = when (emulator?.state) {
    EmulatorState.BOOTING, EmulatorState.RUNNING -> emulator.state
    else -> null
}

private fun activeSimulator(simulator: WorktreeSimulator?): SimulatorState? = when (simulator?.state) {
    SimulatorState.BOOTING, SimulatorState.BOOTED -> simulator.state
    else -> null
}
