package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.usecase.EnvironmentFormat

sealed interface CliCommand {
    data object Help : CliCommand
    data object Version : CliCommand

    data class New(
        val taskId: String? = null,
        val branch: String? = null,
        val branchType: BranchType = BranchType.FEATURE,
        val worktreesRoot: String? = null,
    ) : CliCommand

    data class Provision(val path: String? = null) : CliCommand
    data object Status : CliCommand
    data class Clean(
        val confirmed: Boolean = false,
        val deleteBranches: Boolean = false,
        val stopGradleDaemons: Boolean = false,
    ) : CliCommand

    data object Gradle : CliCommand
    data class GradleStop(val pids: List<Long> = emptyList()) : CliCommand
    data object HookWorktreeCreate : CliCommand
    data object HookWorktreeRemove : CliCommand
    data object HookSessionStart : CliCommand
    data class Env(val path: String? = null, val format: EnvironmentFormat = EnvironmentFormat.SH) : CliCommand
}

sealed interface ParsedArguments {
    data class Command(val command: CliCommand) : ParsedArguments
    data class UsageError(val message: String) : ParsedArguments
}

fun parseArguments(args: List<String>): ParsedArguments {
    val name = args.firstOrNull() ?: return ParsedArguments.Command(CliCommand.Help)
    val rest = args.drop(1)
    return try {
        ParsedArguments.Command(
            when (name) {
                "help", "-h", "--help" -> noArguments(name, rest, CliCommand.Help)
                "version", "-v", "--version" -> noArguments(name, rest, CliCommand.Version)
                "new" -> parseNew(rest)
                "provision" -> parseProvision(rest)
                "status" -> noArguments(name, rest, CliCommand.Status)
                "clean" -> parseClean(rest)
                "hook" -> parseHook(rest)
                "gradle" -> parseGradle(rest)
                "env" -> parseEnv(rest)
                else -> throw UsageException("unknown command '$name'")
            },
        )
    } catch (error: UsageException) {
        ParsedArguments.UsageError(error.message.orEmpty())
    }
}

private class UsageException(message: String) : Exception(message)

private fun noArguments(name: String, rest: List<String>, command: CliCommand): CliCommand {
    rest.firstOrNull()?.let { throw UsageException("'$name' takes no arguments, got '$it'") }
    return command
}

private fun parseNew(rest: List<String>): CliCommand.New {
    var taskId: String? = null
    var branch: String? = null
    var fix = false
    var worktreesRoot: String? = null
    val remaining = ArrayDeque(rest)
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--fix" -> fix = true
            "--branch" -> branch = remaining.valueOf(argument)
            "--worktrees-root" -> worktreesRoot = remaining.valueOf(argument)
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'new'")
                if (taskId != null) throw UsageException("'new' takes a single task id, got '$taskId' and '$argument'")
                taskId = argument
            }
        }
    }
    if (taskId == null && branch == null) throw UsageException("'new' needs a task id or --branch <branch>")
    if (taskId != null && branch != null) throw UsageException("'new' takes either a task id or --branch, not both")
    if (fix && branch != null) throw UsageException("--fix only applies to a new branch, not to --branch")
    return CliCommand.New(
        taskId = taskId,
        branch = branch,
        branchType = if (fix) BranchType.FIX else BranchType.FEATURE,
        worktreesRoot = worktreesRoot,
    )
}

private fun parseProvision(rest: List<String>): CliCommand.Provision {
    rest.firstOrNull { it.startsWith("-") }?.let { throw UsageException("unknown option '$it' for 'provision'") }
    if (rest.size > 1) throw UsageException("'provision' takes at most one path")
    return CliCommand.Provision(rest.firstOrNull())
}

private fun parseClean(rest: List<String>): CliCommand.Clean {
    var confirmed = false
    var deleteBranches = false
    var stopGradleDaemons = false
    rest.forEach { argument ->
        when (argument) {
            "--yes", "-y" -> confirmed = true
            "--delete-branches" -> deleteBranches = true
            "--stop-gradle-daemons" -> stopGradleDaemons = true
            else -> throw UsageException("unknown argument '$argument' for 'clean'")
        }
    }
    return CliCommand.Clean(confirmed = confirmed, deleteBranches = deleteBranches, stopGradleDaemons = stopGradleDaemons)
}

private fun parseGradle(rest: List<String>): CliCommand {
    val subcommand = rest.firstOrNull() ?: return CliCommand.Gradle
    if (subcommand != "stop") throw UsageException("unknown subcommand '$subcommand' for 'gradle', expected stop")
    val pids = rest.drop(1).map { argument ->
        argument.toLongOrNull()?.takeIf { it > 0 }
            ?: throw UsageException("'gradle stop' takes process ids, got '$argument'")
    }
    return CliCommand.GradleStop(pids)
}

private fun parseHook(rest: List<String>): CliCommand {
    val event = rest.firstOrNull() ?: throw UsageException("'hook' needs an event: $HOOK_EVENTS")
    val command = when (event) {
        "worktree-create" -> CliCommand.HookWorktreeCreate
        "worktree-remove" -> CliCommand.HookWorktreeRemove
        "session-start" -> CliCommand.HookSessionStart
        else -> throw UsageException("unknown hook '$event', expected $HOOK_EVENTS")
    }
    return noArguments("hook $event", rest.drop(1), command)
}

private const val HOOK_EVENTS = "worktree-create, worktree-remove or session-start"

private fun parseEnv(rest: List<String>): CliCommand.Env {
    var path: String? = null
    var format = EnvironmentFormat.SH
    val remaining = ArrayDeque(rest)
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--format" -> {
                val value = remaining.valueOf(argument)
                format = EnvironmentFormat.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw UsageException("unknown format '$value' for 'env', expected sh, dotenv or json")
            }
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'env'")
                if (path != null) throw UsageException("'env' takes at most one path")
                path = argument
            }
        }
    }
    return CliCommand.Env(path, format)
}

private fun ArrayDeque<String>.valueOf(option: String): String {
    val value = removeFirstOrNull()
    if (value == null || value.startsWith("-")) throw UsageException("$option needs a value")
    return value
}
