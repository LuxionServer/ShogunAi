package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.OverlapSkipReason
import app.luxion.shogunai.domain.model.OverlapWorktree
import app.luxion.shogunai.domain.model.SkippedWorktree
import app.luxion.shogunai.domain.model.WorktreeOverlap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class OverlapCommand(val target: String? = null, val all: Boolean = false, val json: Boolean = false) : CliCommand

internal fun parseOverlap(rest: List<String>): OverlapCommand {
    var command = OverlapCommand()
    rest.forEach { argument ->
        command = when {
            argument == "--all" -> command.copy(all = true)
            argument == "--json" -> command.copy(json = true)
            argument.startsWith("-") -> throw UsageException("unknown option '$argument' for 'overlap'")
            command.target != null -> throw UsageException("'overlap' takes a single target, got '${command.target}' and '$argument'")
            else -> command.copy(target = argument)
        }
    }
    return command
}

internal fun overlapLines(overlap: WorktreeOverlap, scopedBranch: String?, scoped: Boolean): List<String> {
    if (overlap.files.isEmpty()) {
        return listOf(
            if (scoped) "No file of ${scopedBranch ?: DETACHED} is changed by another worktree." else "No file is changed by more than one worktree.",
        )
    }
    return overlap.files.flatMap { file -> listOf(file.path) + file.worktrees.map { "  ${overlapWorktreeLine(it)}" } }
}

private fun overlapWorktreeLine(worktree: OverlapWorktree): String =
    "${worktree.branch ?: DETACHED} (base ${worktree.base}) ${worktree.path}"

internal fun skippedLine(skipped: SkippedWorktree): String = "shogun: skipped ${skipped.path}: ${skipReason(skipped)}"

private fun skipReason(skipped: SkippedWorktree): String = when (skipped.reason) {
    OverlapSkipReason.PRUNABLE -> "its folder is missing (prunable)"
    OverlapSkipReason.NO_BASE -> "no base to compare with (no recorded base and no main branch)"
    OverlapSkipReason.READ_FAILED -> "couldn't read its changes: ${skipped.detail}"
}

internal fun overlapJson(repository: String, worktree: String?, overlap: WorktreeOverlap): JsonObject = buildJsonObject {
    put("repository", repository)
    put("worktree", worktree)
    putJsonArray("files") {
        overlap.files.forEach { file ->
            addJsonObject {
                put("path", file.path)
                putJsonArray("worktrees") {
                    file.worktrees.forEach { changed ->
                        addJsonObject {
                            put("path", changed.path)
                            put("branch", changed.branch)
                            put("base", changed.base)
                        }
                    }
                }
            }
        }
    }
    putJsonArray("skipped") {
        overlap.skipped.forEach { skipped ->
            addJsonObject {
                put("path", skipped.path)
                put("branch", skipped.branch)
                put("reason", skipReason(skipped))
            }
        }
    }
}

private const val DETACHED = "(detached)"
