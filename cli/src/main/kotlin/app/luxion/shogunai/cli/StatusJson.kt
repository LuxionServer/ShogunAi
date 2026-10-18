package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeStatus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class WorktreeEntry(val worktree: Worktree, val status: WorktreeStatus?, val origin: WorktreeOrigin?)

val WorktreeOrigin.label: String
    get() = when (this) {
        WorktreeOrigin.SHOGUNAI -> "shogunai"
        WorktreeOrigin.CLAUDE_CODE -> "claude-code"
        WorktreeOrigin.HERDR -> "herdr"
        WorktreeOrigin.OTHER -> "other"
    }

fun statusJson(repository: String, mainBranch: String?, entries: List<WorktreeEntry>): JsonObject = buildJsonObject {
    put("repository", repository)
    put("mainBranch", mainBranch)
    put("worktrees", buildJsonArray { entries.forEach { add(worktreeJson(it)) } })
}

private fun worktreeJson(entry: WorktreeEntry): JsonObject = buildJsonObject {
    val worktree = entry.worktree
    put("path", worktree.path)
    put("branch", worktree.branch)
    put("head", worktree.head)
    put("main", worktree.isMain)
    put("locked", worktree.isLocked)
    put("folderMissing", worktree.isPrunable)
    put("origin", entry.origin?.label)
    put("status", entry.status?.let(::statusObject) ?: JsonNull)
}

private fun statusObject(status: WorktreeStatus): JsonObject = buildJsonObject {
    put("uncommittedChanges", status.uncommittedChanges)
    put("upstream", status.upstream?.let(::divergenceObject) ?: JsonNull)
    put("mainBranch", status.mainBranch?.let(::divergenceObject) ?: JsonNull)
    put("merged", status.isMerged)
    put("lastCommitAt", status.lastCommitAt?.toString())
}

private fun divergenceObject(divergence: Divergence): JsonObject = buildJsonObject {
    put("ahead", divergence.ahead)
    put("behind", divergence.behind)
}
