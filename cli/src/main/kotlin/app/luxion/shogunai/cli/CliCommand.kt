package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.usecase.EnvironmentFormat

sealed interface CliCommand {
    data object Help : CliCommand
    data object Version : CliCommand
    data class CommandHelp(val name: String) : CliCommand

    data class New(
        val taskId: String? = null,
        val branch: String? = null,
        val branchType: BranchType = BranchType.FEATURE,
        val worktreesRoot: String? = null,
        val from: String? = null,
        val fetch: Boolean = false,
        val json: Boolean = false,
    ) : CliCommand

    data class Provision(val path: String? = null, val check: Boolean = false, val json: Boolean = false) : CliCommand
    data class Trust(
        val path: String? = null,
        val yes: Boolean = false,
        val revoke: Boolean = false,
        val json: Boolean = false,
    ) : CliCommand
    data class Remove(
        val target: String,
        val force: Boolean = false,
        val deleteBranch: Boolean = false,
        val forceDeleteBranch: Boolean = false,
        val json: Boolean = false,
    ) : CliCommand
    data class Repair(val json: Boolean = false) : CliCommand
    data class Open(val target: String, val herdr: Boolean = false, val agent: String? = null, val json: Boolean = false) : CliCommand
    data class Status(val json: Boolean = false) : CliCommand
    data class Clean(
        val confirmed: Boolean = false,
        val deleteBranches: Boolean = false,
        val stopGradleDaemons: Boolean = false,
        val stopEmulators: Boolean = false,
        val deleteSimulators: Boolean = false,
        val deleteDerivedData: Boolean = false,
        val json: Boolean = false,
    ) : CliCommand

    data class Xcode(val json: Boolean = false) : CliCommand
    data class XcodeClean(val json: Boolean = false) : CliCommand

    data class Gradle(val json: Boolean = false) : CliCommand
    data class Pool(val action: PoolAction, val json: Boolean = false) : CliCommand
    data class GradleStop(val pids: List<Long> = emptyList(), val json: Boolean = false) : CliCommand
    data object HookWorktreeCreate : CliCommand
    data object HookWorktreeRemove : CliCommand
    data object HookSessionStart : CliCommand
    data object HookHerdr : CliCommand
    data class Emulator(
        val action: EmulatorAction,
        val path: String? = null,
        val avd: String? = null,
        val noWindow: Boolean = false,
        val json: Boolean = false,
    ) : CliCommand
    data class Simulator(
        val action: SimulatorAction,
        val path: String? = null,
        val device: String? = null,
        val runtime: String? = null,
        val json: Boolean = false,
    ) : CliCommand
    data class SimulatorGradleInit(val install: Boolean, val confirmed: Boolean = false) : CliCommand
    data class Env(val path: String? = null, val format: EnvironmentFormat = EnvironmentFormat.SH) : CliCommand
}

sealed interface ParsedArguments {
    data class Command(val command: CliCommand) : ParsedArguments
    data class UsageError(val message: String) : ParsedArguments
}

fun parseArguments(args: List<String>): ParsedArguments {
    val name = args.firstOrNull() ?: return ParsedArguments.Command(CliCommand.Help)
    val rest = args.drop(1)
    if (name in CliHelp.commandNames && rest.any { it == "--help" || it == "-h" }) {
        return ParsedArguments.Command(CliCommand.CommandHelp(name))
    }
    val json = name in JSON_COMMANDS && !isSimulatorGradleInit(name, rest) && JSON_FLAG in rest
    val arguments = if (json) rest.filter { it != JSON_FLAG } else rest
    return try {
        ParsedArguments.Command(
            when (name) {
                "help", "-h", "--help" -> noArguments(name, rest, CliCommand.Help)
                "version", "-v", "--version" -> noArguments(name, rest, CliCommand.Version)
                "new" -> parseNew(arguments)
                "provision" -> parseProvision(arguments)
                "trust" -> parseTrust(arguments)
                "remove" -> parseRemove(arguments)
                "repair" -> noArguments(name, arguments, CliCommand.Repair())
                "open" -> parseOpen(arguments)
                "status" -> parseStatus(arguments)
                "clean" -> parseClean(arguments)
                "hook" -> parseHook(arguments)
                "gradle" -> parseGradle(arguments)
                "pool" -> parsePool(arguments)
                "xcode" -> parseXcode(arguments)
                "env" -> parseEnv(arguments)
                "emulator" -> parseEmulator(arguments)
                "simulator" -> parseSimulator(arguments)
                else -> throw UsageException("unknown command '$name'")
            }.withJson(json),
        )
    } catch (error: UsageException) {
        ParsedArguments.UsageError(error.message.orEmpty())
    }
}

private const val JSON_FLAG = "--json"

private val JSON_COMMANDS = setOf("new", "provision", "trust", "remove", "repair", "open", "clean", "gradle", "pool", "xcode", "emulator", "simulator")

private fun isSimulatorGradleInit(name: String, rest: List<String>) = name == "simulator" && rest.firstOrNull() == "gradle-init"

private fun CliCommand.withJson(json: Boolean): CliCommand = if (!json) this else when (this) {
    is CliCommand.New -> copy(json = true)
    is CliCommand.Provision -> copy(json = true)
    is CliCommand.Trust -> copy(json = true)
    is CliCommand.Remove -> copy(json = true)
    is CliCommand.Repair -> copy(json = true)
    is CliCommand.Open -> copy(json = true)
    is CliCommand.Clean -> copy(json = true)
    is CliCommand.Xcode -> copy(json = true)
    is CliCommand.XcodeClean -> copy(json = true)
    is CliCommand.Gradle -> copy(json = true)
    is CliCommand.GradleStop -> copy(json = true)
    is CliCommand.Pool -> copy(json = true)
    is CliCommand.Emulator -> copy(json = true)
    is CliCommand.Simulator -> copy(json = true)
    else -> this
}

private class UsageException(message: String) : Exception(message)

private fun noArguments(name: String, rest: List<String>, command: CliCommand): CliCommand {
    rest.firstOrNull()?.let {
        if (it.startsWith("-")) throw UsageException("unknown option '$it' for '$name'")
        throw UsageException("'$name' takes no arguments, got '$it'")
    }
    return command
}

private fun parseNew(rest: List<String>): CliCommand.New {
    var taskId: String? = null
    var branch: String? = null
    var fix = false
    var worktreesRoot: String? = null
    var from: String? = null
    var fetch = false
    val remaining = ArrayDeque(rest)
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--fix" -> fix = true
            "--fetch" -> fetch = true
            "--from" -> from = remaining.valueOf(argument)
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
    if (from != null && branch != null) throw UsageException("--from only applies to a new branch, not to --branch")
    return CliCommand.New(
        taskId = taskId,
        branch = branch,
        branchType = if (fix) BranchType.FIX else BranchType.FEATURE,
        worktreesRoot = worktreesRoot,
        from = from,
        fetch = fetch,
    )
}

private fun parseRemove(rest: List<String>): CliCommand.Remove {
    var target: String? = null
    var force = false
    var deleteBranch = false
    var forceDeleteBranch = false
    rest.forEach { argument ->
        when {
            argument == "--force" || argument == "-f" -> force = true
            argument == "--delete-branch" -> deleteBranch = true
            argument == "--force-delete-branch" -> forceDeleteBranch = true
            argument.startsWith("-") -> throw UsageException("unknown option '$argument' for 'remove'")
            target != null -> throw UsageException("'remove' takes a single target, got '$target' and '$argument'")
            else -> target = argument
        }
    }
    return CliCommand.Remove(target ?: throw UsageException("'remove' needs a task id, path or branch"), force, deleteBranch, forceDeleteBranch)
}

private fun parseOpen(rest: List<String>): CliCommand.Open {
    var target: String? = null
    var herdr = false
    var agent: String? = null
    val remaining = ArrayDeque(rest)
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--herdr" -> herdr = true
            "--agent" -> agent = remaining.valueOf(argument)
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'open'")
                if (target != null) throw UsageException("'open' takes a single target, got '$target' and '$argument'")
                target = argument
            }
        }
    }
    if (target == null) throw UsageException("'open' needs a task id, path or branch")
    if (!herdr) throw UsageException("'open' needs where to open the worktree: --herdr")
    return CliCommand.Open(target, herdr, agent)
}

private fun parseStatus(rest: List<String>): CliCommand.Status {
    var json = false
    rest.forEach { argument ->
        when {
            argument == "--json" -> json = true
            argument.startsWith("-") -> throw UsageException("unknown option '$argument' for 'status'")
            else -> throw UsageException("'status' takes no arguments, got '$argument'")
        }
    }
    return CliCommand.Status(json)
}

private fun parseProvision(rest: List<String>): CliCommand.Provision {
    val check = "--check" in rest
    val others = rest.filter { it != "--check" }
    others.firstOrNull { it.startsWith("-") }?.let { throw UsageException("unknown option '$it' for 'provision'") }
    if (others.size > 1) throw UsageException("'provision' takes at most one path")
    return CliCommand.Provision(others.firstOrNull(), check)
}

private fun parseTrust(rest: List<String>): CliCommand.Trust {
    val yes = "--yes" in rest || "-y" in rest
    val revoke = "--revoke" in rest
    val others = rest.filter { it != "--yes" && it != "-y" && it != "--revoke" }
    others.firstOrNull { it.startsWith("-") }?.let { throw UsageException("unknown option '$it' for 'trust'") }
    if (others.size > 1) throw UsageException("'trust' takes at most one path")
    if (yes && revoke) throw UsageException("--yes and --revoke can't be combined")
    return CliCommand.Trust(others.firstOrNull(), yes, revoke)
}

private fun parseClean(rest: List<String>): CliCommand.Clean {
    var confirmed = false
    var deleteBranches = false
    var stopGradleDaemons = false
    var stopEmulators = false
    var deleteSimulators = false
    var deleteDerivedData = false
    rest.forEach { argument ->
        when (argument) {
            "--yes", "-y" -> confirmed = true
            "--delete-branches" -> deleteBranches = true
            "--stop-gradle-daemons" -> stopGradleDaemons = true
            "--stop-emulators" -> stopEmulators = true
            "--delete-simulators" -> deleteSimulators = true
            "--delete-derived-data" -> deleteDerivedData = true
            else -> throw UsageException("unknown argument '$argument' for 'clean'")
        }
    }
    return CliCommand.Clean(
        confirmed = confirmed,
        deleteBranches = deleteBranches,
        stopGradleDaemons = stopGradleDaemons,
        stopEmulators = stopEmulators,
        deleteSimulators = deleteSimulators,
        deleteDerivedData = deleteDerivedData,
    )
}

private fun parseXcode(rest: List<String>): CliCommand {
    val subcommand = rest.firstOrNull() ?: return CliCommand.Xcode()
    if (subcommand != "clean") throw UsageException("unknown subcommand '$subcommand' for 'xcode', expected clean")
    rest.drop(1).firstOrNull()?.let { throw UsageException("'xcode clean' takes no arguments, got '$it'") }
    return CliCommand.XcodeClean()
}

private fun parseGradle(rest: List<String>): CliCommand {
    val subcommand = rest.firstOrNull() ?: return CliCommand.Gradle()
    if (subcommand != "stop") throw UsageException("unknown subcommand '$subcommand' for 'gradle', expected stop")
    val pids = rest.drop(1).map { argument ->
        argument.toLongOrNull()?.takeIf { it > 0 }
            ?: throw UsageException("'gradle stop' takes process ids, got '$argument'")
    }
    return CliCommand.GradleStop(pids)
}

enum class PoolAction { STATUS, TRIM }

private fun parsePool(rest: List<String>): CliCommand {
    val name = rest.firstOrNull() ?: throw UsageException("'pool' needs an action: status or trim")
    val action = PoolAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'pool', expected status or trim")
    rest.drop(1).firstOrNull()?.let { throw UsageException("'pool $name' takes no arguments, got '$it'") }
    return CliCommand.Pool(action)
}

private fun parseHook(rest: List<String>): CliCommand {
    val event = rest.firstOrNull() ?: throw UsageException("'hook' needs an event: $HOOK_EVENTS")
    val command = when (event) {
        "worktree-create" -> CliCommand.HookWorktreeCreate
        "worktree-remove" -> CliCommand.HookWorktreeRemove
        "session-start" -> CliCommand.HookSessionStart
        "herdr" -> CliCommand.HookHerdr
        else -> throw UsageException("unknown hook '$event', expected $HOOK_EVENTS")
    }
    return noArguments("hook $event", rest.drop(1), command)
}

private const val HOOK_EVENTS = "worktree-create, worktree-remove, session-start or herdr"

enum class EmulatorAction { START, STOP, STATUS }

enum class SimulatorAction { START, STOP, STATUS }

private fun parseSimulator(rest: List<String>): CliCommand {
    val name = rest.firstOrNull() ?: throw UsageException("'simulator' needs an action: start, stop, status or gradle-init")
    if (name == "gradle-init") return parseSimulatorGradleInit(rest.drop(1))
    val action = SimulatorAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'simulator', expected start, stop, status or gradle-init")
    var path: String? = null
    var device: String? = null
    var runtime: String? = null
    val remaining = ArrayDeque(rest.drop(1))
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--device" -> device = remaining.valueOf(argument)
            "--runtime" -> runtime = remaining.valueOf(argument)
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'simulator'")
                if (path != null) throw UsageException("'simulator $name' takes at most one path")
                path = argument
            }
        }
    }
    if (action != SimulatorAction.START && (device != null || runtime != null)) {
        throw UsageException("--device and --runtime only apply to 'simulator start'")
    }
    return CliCommand.Simulator(action, path, device, runtime)
}

private fun parseSimulatorGradleInit(rest: List<String>): CliCommand.SimulatorGradleInit {
    val install = when (rest.firstOrNull()) {
        "install" -> true
        "uninstall" -> false
        null -> throw UsageException("'simulator gradle-init' needs install or uninstall")
        else -> throw UsageException("unknown action '${rest.first()}' for 'simulator gradle-init', expected install or uninstall")
    }
    var confirmed = false
    rest.drop(1).forEach { argument ->
        when (argument) {
            "--yes", "-y" -> confirmed = true
            else -> throw UsageException("unknown argument '$argument' for 'simulator gradle-init'")
        }
    }
    return CliCommand.SimulatorGradleInit(install, confirmed)
}

private fun parseEmulator(rest: List<String>): CliCommand.Emulator {
    val name = rest.firstOrNull() ?: throw UsageException("'emulator' needs an action: start, stop or status")
    val action = EmulatorAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'emulator', expected start, stop or status")
    var path: String? = null
    var avd: String? = null
    var noWindow = false
    val remaining = ArrayDeque(rest.drop(1))
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--avd" -> avd = remaining.valueOf(argument)
            "--no-window" -> noWindow = true
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'emulator'")
                if (path != null) throw UsageException("'emulator $name' takes at most one path")
                path = argument
            }
        }
    }
    if (action != EmulatorAction.START && (avd != null || noWindow)) {
        throw UsageException("--avd and --no-window only apply to 'emulator start'")
    }
    return CliCommand.Emulator(action, path, avd, noWindow)
}

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
