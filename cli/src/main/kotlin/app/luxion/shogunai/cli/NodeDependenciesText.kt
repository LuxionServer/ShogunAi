package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.NodeDependencies
import app.luxion.shogunai.domain.model.NodeDependenciesFailure
import app.luxion.shogunai.domain.model.ShellQuoting
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun nodeJson(dependencies: NodeDependencies): JsonObject = buildJsonObject {
    put("dependencies", nodeDependenciesJson(dependencies))
}

private fun nodeDependenciesJson(dependencies: NodeDependencies): JsonObject = buildJsonObject {
    when (dependencies) {
        is NodeDependencies.Cloned -> {
            put("status", "cloned")
            put("manager", dependencies.manager.id)
            put("folders", JsonArray(dependencies.folders.map(::JsonPrimitive)))
        }
        is NodeDependencies.Installed -> {
            put("status", "installed")
            put("manager", dependencies.manager.id)
        }
        NodeDependencies.Present -> put("status", "present")
        is NodeDependencies.Needed -> {
            put("status", "needed")
            dependencies.project?.let { put("manager", it.manager.id) }
            put("reason", nodeDependenciesReason(dependencies.reason))
            dependencies.project?.let { project -> put("command", JsonArray(project.installCommand.map(::JsonPrimitive))) }
        }
    }
}

fun nodeDependenciesReason(failure: NodeDependenciesFailure): String = when (failure) {
    NodeDependenciesFailure.NoLockfile -> "there is no lockfile in the main folder"
    NodeDependenciesFailure.NotApproved -> "the repository isn't approved on this machine (review it with 'shogun trust')"
    is NodeDependenciesFailure.NotFound -> "${failure.executable} wasn't found"
    is NodeDependenciesFailure.Exited ->
        "exited with code ${failure.exitCode}" + failure.errorLines.takeIf { it.isNotEmpty() }?.joinToString(" | ", prefix = ": ").orEmpty()
    is NodeDependenciesFailure.Unexpected -> failure.message
}

fun nodeDependenciesLine(dependencies: NodeDependencies): String = when (dependencies) {
    is NodeDependencies.Cloned -> "node dependencies: ${dependencies.folders.joinToString(", ")} cloned from the base repository (${dependencies.manager.id})"
    is NodeDependencies.Installed -> "node dependencies: installed with ${dependencies.manager.id}"
    NodeDependencies.Present -> "node dependencies: node_modules already present"
    is NodeDependencies.Needed -> "node dependencies needed: ${nodeDependenciesReason(dependencies.reason)}" +
        dependencies.project?.let { "; run '${ShellQuoting.posixCommand(it.installCommand)}' in the worktree" }.orEmpty()
}

const val NODE_INSTALL_APPROVAL_LINE = "Installs Node dependencies with the repository's package manager (npm ci, pnpm, yarn or bun install)"
