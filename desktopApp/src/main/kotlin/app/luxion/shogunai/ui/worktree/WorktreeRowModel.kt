package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.usecase.isInactive
import java.io.File
import java.time.Instant

enum class RowIndicator { MISSING, AGENT_RUNNING, UNCOMMITTED_CHANGES, MERGED, INACTIVE, CLEAN }

enum class RowChipKind { AGENT, CHANGES, DIVERGENCE, MERGED, INACTIVE, LOCKED, DEVICE }

data class RowChip(val kind: RowChipKind, val text: String? = null)

data class WorktreeRowModel(
    val path: String,
    val branch: String?,
    val folderName: String,
    val origin: WorktreeOrigin?,
    val isMain: Boolean,
    val isFolderMissing: Boolean,
    val indicator: RowIndicator,
    val chips: List<RowChip>,
    val statusState: WorktreeStatusState?,
    val lastCommitAt: Instant?,
)

fun worktreeRowModel(
    worktree: Worktree,
    status: WorktreeStatusState?,
    agents: List<RunningAgent>,
    emulator: WorktreeEmulator?,
    simulator: WorktreeSimulator?,
    origin: WorktreeOrigin?,
    now: Instant,
): WorktreeRowModel {
    val loaded = (status as? WorktreeStatusState.Loaded)?.status
    val inactive = !worktree.isPrunable && isInactive(worktree, loaded, agents, now)
    val missing = worktree.isPrunable
    val merged = loaded?.isMerged == true
    val changes = loaded?.uncommittedChanges ?: 0
    val indicator = when {
        missing -> RowIndicator.MISSING
        agents.isNotEmpty() -> RowIndicator.AGENT_RUNNING
        changes > 0 -> RowIndicator.UNCOMMITTED_CHANGES
        merged -> RowIndicator.MERGED
        inactive -> RowIndicator.INACTIVE
        else -> RowIndicator.CLEAN
    }
    val chips = if (missing) {
        emptyList()
    } else {
        listOfNotNull(
            agents.takeIf { it.isNotEmpty() }?.let { RowChip(RowChipKind.AGENT, it.joinToString(", ") { agent -> agent.name }) },
            changes.takeIf { it > 0 }?.let { RowChip(RowChipKind.CHANGES, "✎$it") },
            loaded?.mainBranch?.takeUnless { merged }?.arrows()?.let { RowChip(RowChipKind.DIVERGENCE, it) },
            RowChip(RowChipKind.MERGED).takeIf { merged },
            RowChip(RowChipKind.INACTIVE).takeIf { inactive },
            RowChip(RowChipKind.LOCKED).takeIf { worktree.isLocked },
            RowChip(RowChipKind.DEVICE).takeIf { emulator.isActive() || simulator.isActive() },
        )
    }
    return WorktreeRowModel(
        path = worktree.path,
        branch = worktree.branch,
        folderName = File(worktree.path).name,
        origin = origin,
        isMain = worktree.isMain,
        isFolderMissing = missing,
        indicator = indicator,
        chips = chips,
        statusState = status,
        lastCommitAt = loaded?.lastCommitAt,
    )
}

fun orderedForList(worktrees: List<Worktree>): List<Worktree> = worktrees.sortedByDescending { it.isMain }

internal fun Divergence.arrows(): String? =
    listOfNotNull("↑$ahead".takeIf { ahead > 0 }, "↓$behind".takeIf { behind > 0 })
        .joinToString(" ")
        .ifEmpty { null }

private fun WorktreeEmulator?.isActive(): Boolean = this != null && state != EmulatorState.STOPPED

private fun WorktreeSimulator?.isActive(): Boolean = this != null && state != SimulatorState.SHUTDOWN
