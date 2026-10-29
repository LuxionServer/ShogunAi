package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.GroupRunnerOpening
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.usecase.AddedRepository
import app.luxion.shogunai.domain.usecase.GroupCleanupSelection
import app.luxion.shogunai.domain.usecase.GroupCreation
import app.luxion.shogunai.domain.usecase.GroupKeptReason
import app.luxion.shogunai.domain.model.RunnerOutcome
import app.luxion.shogunai.domain.model.flag
import app.luxion.shogunai.domain.usecase.GroupRepositoryOutcome
import app.luxion.shogunai.domain.usecase.GroupWorktreeState
import app.luxion.shogunai.domain.usecase.MultirepoState
import app.luxion.shogunai.domain.usecase.RegisteredMultirepo
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun multirepoJson(multirepo: Multirepo, configs: Map<String, ProjectConfig?>, bases: Map<String, String?>): JsonObject = buildJsonObject {
    put("name", multirepo.name)
    put("path", multirepo.folder)
    put("approved", multirepo.approved)
    put("worktreesRoot", multirepo.worktreesRoot)
    put("repos", buildJsonArray {
        multirepo.repositories.forEach { repository ->
            add(buildJsonObject {
                put("alias", repository.alias)
                put("path", repository.path)
                put("project", configs[repository.alias] != null)
                put("worktreesFolder", configs[repository.alias]?.worktreesRoot?.ifBlank { null })
                put("base", bases[repository.alias])
                put("workspace", repository == multirepo.workspaceRepository)
                put("detected", repository == multirepo.workspaceRepository && multirepo.workspaceDetected)
            })
        }
    })
    put("defaultRepos", buildJsonArray { multirepo.defaultRepos.forEach { add(JsonPrimitive(it)) } })
    put("env", sharedVariablesJson(multirepo))
    put("agent", buildJsonObject {
        put("name", multirepo.agent.displayName)
        put("command", multirepo.agent.agentCommand)
        put("useHeadroom", multirepo.agent.useHeadroom)
    })
    put("ignoredDeclaredAgent", multirepo.ignoresDeclaredAgent)
}

fun multirepoTrustJson(multirepo: Multirepo, approved: Boolean, changed: Boolean): JsonObject = buildJsonObject {
    put("path", multirepo.folder)
    put("declaration", multirepo.declarationPath)
    multirepo.declaredName?.let { put("name", it) }
    put("approved", approved)
    put("changed", changed)
    put("repos", buildJsonArray {
        multirepo.repositories.forEach { repository ->
            add(buildJsonObject {
                put("alias", repository.alias)
                put("path", repository.path)
                put("outsideParent", multirepo.isOutsideParent(repository.path))
            })
        }
    })
    put("worktreesRoot", multirepo.worktreesRoot)
    put("worktreesRootOutsideParent", multirepo.isOutsideParent(multirepo.worktreesRoot))
    put("workspace", multirepo.workspaceRepository?.alias)
    put("env", sharedVariablesJson(multirepo))
    put("ignoredDeclaredAgent", multirepo.ignoresDeclaredAgent)
}

fun sharedVariablesListJson(multirepo: Multirepo): JsonObject = buildJsonObject {
    put("env", sharedVariablesJson(multirepo))
}

private fun sharedVariablesJson(multirepo: Multirepo): JsonObject = buildJsonObject {
    multirepo.sharedTemplates.groupBy { it.first }.forEach { (alias, variables) ->
        put(alias, buildJsonObject { variables.forEach { (_, name, template) -> put(name, template) } })
    }
}

fun registeredMultirepoJson(registered: RegisteredMultirepo): JsonObject = buildJsonObject {
    put("name", registered.multirepo.name)
    put("path", registered.multirepo.folder)
    put("declaration", registered.multirepo.declarationPath)
    put("registered", registered.trustedNow)
    put("projects", buildJsonArray {
        registered.registeredProjects.forEach { project ->
            add(buildJsonObject {
                put("name", project.name)
                put("path", project.config.baseRepositoryPath)
                put("worktreesFolder", project.config.worktreesRoot)
            })
        }
    })
}

fun addedRepositoryJson(multirepo: Multirepo, added: AddedRepository): JsonObject = buildJsonObject {
    put("path", multirepo.folder)
    put("alias", added.alias)
    put("repository", added.path)
    put("projectRegistered", added.registered)
    put("worktreesFolder", added.project.config.worktreesRoot)
    put("worktreesFolderProposal", added.worktreesFolder.name.lowercase())
}

fun groupCreationJson(creation: GroupCreation): JsonObject = buildJsonObject {
    put("branch", creation.branch)
    put("repos", buildJsonArray {
        creation.repositories.forEach { result ->
            add(buildJsonObject {
                put("alias", result.alias)
                put("path", result.path)
                put("result", result.outcome.label)
                put("skippedPostCreateCommands", buildJsonArray {
                    result.skippedPostCreateCommands.forEach { command -> add(buildJsonArray { command.forEach { add(JsonPrimitive(it)) } }) }
                })
                put("skippedVariables", buildJsonArray { result.skippedVariables.forEach { add(JsonPrimitive(it)) } })
                put("error", result.error?.message?.let(::JsonPrimitive) ?: JsonNull)
            })
        }
    })
}

private val GroupRepositoryOutcome.label: String
    get() = name.lowercase()

fun groupStatusJson(multirepo: Multirepo, state: MultirepoState): JsonObject = buildJsonObject {
    put("path", multirepo.folder)
    put("groups", buildJsonArray {
        state.groups.forEach { group ->
            add(buildJsonObject {
                put("branch", group.group.branch)
                put("title", group.group.title)
                put("repos", buildJsonArray { group.worktrees.forEach { add(groupWorktreeJson(it)) } })
            })
        }
    })
    put("ungrouped", buildJsonArray { state.ungrouped.forEach { add(groupWorktreeJson(it)) } })
    put("failures", buildJsonObject { state.worktrees.failures.forEach { (alias, error) -> put(alias, error.message) } })
}

private fun groupWorktreeJson(state: GroupWorktreeState): JsonObject = buildJsonObject {
    val status = state.status?.getOrNull()
    put("alias", state.member.alias)
    put("path", state.member.worktree.path)
    put("branch", state.member.worktree.branch)
    put("base", state.base?.name)
    put("folderMissing", state.member.worktree.isPrunable)
    put("uncommittedChanges", status?.uncommittedChanges)
    put("ahead", status?.base?.ahead)
    put("behind", status?.base?.behind)
    put("merged", status?.isMerged)
    state.status?.exceptionOrNull()?.let { put("statusProblem", it.message) }
    put("agents", buildJsonArray { state.agents.forEach { add(agentJson(it)) } })
}

fun groupCleanJson(
    selection: GroupCleanupSelection,
    dryRun: Boolean,
    removed: List<String> = emptyList(),
    failures: List<Pair<String, String>> = emptyList(),
): JsonObject = buildJsonObject {
    put("dryRun", dryRun)
    put("candidates", buildJsonArray {
        selection.candidates.forEach { group ->
            add(buildJsonObject {
                put("branch", group.group.branch)
                put("worktrees", buildJsonArray {
                    group.worktrees.forEach { worktree ->
                        add(buildJsonObject {
                            put("alias", worktree.member.alias)
                            put("path", worktree.member.worktree.path)
                            put("branch", worktree.member.worktree.branch)
                        })
                    }
                })
            })
        }
    })
    put("loose", buildJsonArray {
        selection.loose.forEach { worktree ->
            add(buildJsonObject {
                put("alias", worktree.member.alias)
                put("path", worktree.member.worktree.path)
                put("branch", worktree.member.worktree.branch)
            })
        }
    })
    put("kept", buildJsonArray {
        selection.kept.forEach { kept ->
            add(buildJsonObject {
                put("branch", kept.group.branch)
                put("alias", kept.alias)
                put("reason", kept.reason.label)
                put("agents", buildJsonArray { kept.agents.forEach { add(JsonPrimitive(it.name)) } })
            })
        }
    })
    put("removed", buildJsonArray { removed.forEach { add(JsonPrimitive(it)) } })
    put("failures", buildJsonArray {
        failures.forEach { (path, message) -> add(buildJsonObject { put("path", path); put("message", message) }) }
    })
}

internal val GroupKeptReason.label: String
    get() = when (this) {
        GroupKeptReason.NOT_MERGED -> "not-merged"
        GroupKeptReason.UNCOMMITTED_CHANGES -> "uncommitted-changes"
        GroupKeptReason.STATUS_UNAVAILABLE -> "status-unavailable"
        GroupKeptReason.AGENT_RUNNING -> "agent-running"
    }

fun groupOpenJson(branch: String, runner: RunnerId, openings: List<GroupRunnerOpening>): JsonObject = buildJsonObject {
    put("branch", branch)
    put("repos", buildJsonArray {
        openings.forEach { opening ->
            add(buildJsonObject {
                put("alias", opening.alias)
                put("path", opening.path)
                val opened = opening.result.getOrNull()
                put("result", opened?.outcome?.jsonValue ?: "failed")
                put(runner.handleKey, opened?.handle)
                put("agent", opened?.agent)
                put("session_name", opened?.sessionName)
                put("error", opening.result.exceptionOrNull()?.message?.let(::JsonPrimitive) ?: JsonNull)
            })
        }
    })
}

fun multirepoAgentOpenJson(multirepo: Multirepo, opening: RunnerOpening): JsonObject = buildJsonObject {
    put("path", multirepo.folder)
    if (opening.runner == RunnerId.HERDR) put("opened", opening.outcome != RunnerOutcome.FOCUSED)
    put(opening.runner.flag, opening.outcome.jsonValue)
    put(opening.runner.handleKey, opening.handle)
    put("agent", opening.agent ?: multirepo.agent.displayName)
    put("session_name", opening.sessionName)
}
