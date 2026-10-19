package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.HerdrAgentState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.usecase.isInactive
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.Status
import java.time.Instant

data class WorktreeRowModel(
    val branchLabel: String,
    val folderName: String,
    val originLabel: String?,
    val indicator: RowIndicator?,
    val chips: List<RowChip>,
    val isStatusLoading: Boolean,
    val lastCommitLabel: String?,
)

enum class RowIndicator(val status: Status) {
    FOLDER_MISSING(Status.DANGER),
    AGENT_RUNNING(Status.INFO),
    UNCOMMITTED_CHANGES(Status.WARNING),
    MERGED(Status.SUCCESS),
    INACTIVE(Status.NEUTRAL),
}

enum class RowChipKind {
    MAIN,
    FOLDER_MISSING,
    STATUS_UNAVAILABLE,
    AGENT,
    CHANGES,
    DIVERGENCE,
    MERGED,
    INACTIVE,
    LOCKED,
    DEVICE,
}

data class RowChip(
    val kind: RowChipKind,
    val status: Status,
    val label: String,
)

fun orderMainFirst(worktrees: List<Worktree>): List<Worktree> =
    worktrees.sortedWith(compareByDescending { it.isMain })

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
    now: Instant,
    strings: Strings,
): WorktreeRowModel {
    val loaded = (status as? WorktreeStatusState.Loaded)?.status
    val indicator = indicatorOf(worktree, loaded, mainBranchName, agents, now)
    return WorktreeRowModel(
        branchLabel = worktree.branch ?: strings.detachedHead,
        folderName = worktree.path.substringAfterLast('/'),
        originLabel = origin?.let { originLabel(it, strings) },
        indicator = indicator,
        chips = chipsOf(worktree, status, loaded, mainBranchName, agents, emulator, simulator, now, strings),
        isStatusLoading = status == WorktreeStatusState.Loading,
        lastCommitLabel = loaded?.lastCommitAt?.let { relativeTime(it, now, strings) },
    )
}

internal fun indicatorOf(
    worktree: Worktree,
    status: WorktreeStatus?,
    mainBranchName: String?,
    agents: List<RunningAgent>,
    now: Instant,
): RowIndicator? {
    val merged = status?.isMerged == true && mainBranchName != null
    val changes = status?.uncommittedChanges?.takeIf { it > 0 }
    return when {
        worktree.isPrunable -> RowIndicator.FOLDER_MISSING
        agents.isNotEmpty() -> RowIndicator.AGENT_RUNNING
        changes != null -> RowIndicator.UNCOMMITTED_CHANGES
        merged -> RowIndicator.MERGED
        isInactive(worktree, status, agents, now) -> RowIndicator.INACTIVE
        else -> null
    }
}

private fun chipsOf(
    worktree: Worktree,
    state: WorktreeStatusState?,
    status: WorktreeStatus?,
    mainBranchName: String?,
    agents: List<RunningAgent>,
    emulator: WorktreeEmulator?,
    simulator: WorktreeSimulator?,
    now: Instant,
    strings: Strings,
): List<RowChip> {
    val merged = status?.isMerged == true && mainBranchName != null
    val changes = status?.uncommittedChanges?.takeIf { it > 0 }
    val inactive = isInactive(worktree, status, agents, now)
    return buildList {
        if (worktree.isMain) add(RowChip(RowChipKind.MAIN, Status.NEUTRAL, strings.mainWorktree))
        if (worktree.isPrunable) add(RowChip(RowChipKind.FOLDER_MISSING, Status.DANGER, strings.folderMissing))
        if (state == WorktreeStatusState.Unavailable) {
            add(RowChip(RowChipKind.STATUS_UNAVAILABLE, Status.NEUTRAL, strings.statusUnavailable))
        }
        agents.forEach { agent ->
            add(RowChip(RowChipKind.AGENT, agent.herdrState.status(), strings.agentLabel(agent.name, agent.herdrState)))
        }
        if (changes != null) {
            add(RowChip(RowChipKind.CHANGES, Status.WARNING, strings.uncommittedChanges(changes)))
        }
        status?.mainBranch?.takeUnless { merged }?.arrows()?.let { arrows ->
            add(RowChip(RowChipKind.DIVERGENCE, Status.NEUTRAL, listOfNotNull(arrows, mainBranchName).joinToString(" ")))
        }
        if (merged) {
            add(RowChip(RowChipKind.MERGED, Status.SUCCESS, strings.mergedInto(mainBranchName)))
        }
        if (inactive) {
            add(RowChip(RowChipKind.INACTIVE, Status.NEUTRAL, strings.inactive))
        }
        if (worktree.isLocked) add(RowChip(RowChipKind.LOCKED, Status.NEUTRAL, strings.locked))
        activeEmulator(emulator)?.let { state ->
            add(RowChip(RowChipKind.DEVICE, state.status(), emulatorStateLabel(state, strings)))
        }
        activeSimulator(simulator)?.let { state ->
            add(RowChip(RowChipKind.DEVICE, state.status(), simulatorStateLabel(state, strings)))
        }
    }
}

private fun HerdrAgentState?.status(): Status = when (this) {
    HerdrAgentState.BLOCKED -> Status.WARNING
    HerdrAgentState.DONE -> Status.SUCCESS
    HerdrAgentState.IDLE -> Status.NEUTRAL
    HerdrAgentState.WORKING, null -> Status.INFO
}

private fun activeEmulator(emulator: WorktreeEmulator?): EmulatorState? = when (emulator?.state) {
    EmulatorState.BOOTING, EmulatorState.RUNNING -> emulator.state
    else -> null
}

private fun activeSimulator(simulator: WorktreeSimulator?): SimulatorState? = when (simulator?.state) {
    SimulatorState.BOOTING, SimulatorState.BOOTED -> simulator.state
    else -> null
}
