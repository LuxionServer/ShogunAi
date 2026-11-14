package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.usecase.EnvironmentFormat

internal fun parseNew(rest: List<String>): CliCommand.New {
    var taskId: String? = null
    var branch: String? = null
    var fix = false
    var worktreesRoot: String? = null
    var from: String? = null
    var fetch = false
    var repos: List<String>? = null
    val remaining = ArrayDeque(rest)
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--repos" -> repos = remaining.valueOf(argument).split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { throw UsageException("--repos needs at least one repository alias") }
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
    if (repos != null) {
        listOfNotNull("--branch".takeIf { branch != null }, "--from".takeIf { from != null }, "--worktrees-root".takeIf { worktreesRoot != null })
            .firstOrNull()?.let { throw UsageException("$it doesn't apply to a group created with --repos") }
    }
    return CliCommand.New(
        taskId = taskId,
        branch = branch,
        branchType = if (fix) BranchType.FIX else BranchType.FEATURE,
        worktreesRoot = worktreesRoot,
        from = from,
        fetch = fetch,
        repos = repos,
    )
}

internal fun parseRemove(rest: List<String>): CliCommand.Remove {
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

internal fun parseOpen(rest: List<String>): CliCommand.Open {
    var target: String? = null
    var herdr = false
    var orca = false
    var agent: String? = null
    val remaining = ArrayDeque(rest)
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--herdr" -> herdr = true
            "--orca" -> orca = true
            "--agent" -> agent = remaining.valueOf(argument)
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'open'")
                if (target != null) throw UsageException("'open' takes a single target, got '$target' and '$argument'")
                target = argument
            }
        }
    }
    if (herdr && orca) throw UsageException("'open' opens in one place at a time: pass either --herdr or --orca")
    val runner = when {
        herdr -> RunnerId.HERDR
        orca -> RunnerId.ORCA
        else -> null
    }
    return CliCommand.Open(target, runner, agent)
}

internal fun parseStatus(rest: List<String>): CliCommand.Status {
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

internal fun parseBase(rest: List<String>): CliCommand.Base {
    var target: String? = null
    var set: String? = null
    val remaining = ArrayDeque(rest)
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--set" -> set = remaining.valueOf(argument)
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'base'")
                if (target != null) throw UsageException("'base' takes a single target, got '$target' and '$argument'")
                target = argument
            }
        }
    }
    return CliCommand.Base(target, set)
}

internal fun parseProvision(rest: List<String>): CliCommand.Provision {
    val check = "--check" in rest
    val others = rest.filter { it != "--check" }
    others.firstOrNull { it.startsWith("-") }?.let { throw UsageException("unknown option '$it' for 'provision'") }
    if (others.size > 1) throw UsageException("'provision' takes at most one path")
    return CliCommand.Provision(others.firstOrNull(), check)
}

internal fun parseTrust(rest: List<String>): CliCommand.Trust {
    val yes = "--yes" in rest || "-y" in rest
    val revoke = "--revoke" in rest
    val others = rest.filter { it != "--yes" && it != "-y" && it != "--revoke" }
    others.firstOrNull { it.startsWith("-") }?.let { throw UsageException("unknown option '$it' for 'trust'") }
    if (others.size > 1) throw UsageException("'trust' takes at most one path")
    if (yes && revoke) throw UsageException("--yes and --revoke can't be combined")
    return CliCommand.Trust(others.firstOrNull(), yes, revoke)
}

internal fun parseClean(rest: List<String>): CliCommand.Clean {
    var confirmed = false
    var deleteBranches = false
    var stopGradleDaemons = false
    var stopMavenDaemons = false
    var stopEmulators = false
    var deleteSimulators = false
    var deleteDerivedData = false
    var downComposeProjects = false
    var removeComposeVolumes = false
    rest.forEach { argument ->
        when (argument) {
            "--yes", "-y" -> confirmed = true
            "--delete-branches" -> deleteBranches = true
            "--stop-gradle-daemons" -> stopGradleDaemons = true
            "--stop-maven-daemons" -> stopMavenDaemons = true
            "--stop-emulators" -> stopEmulators = true
            "--delete-simulators" -> deleteSimulators = true
            "--delete-derived-data" -> deleteDerivedData = true
            "--down-compose-projects" -> downComposeProjects = true
            "--compose-volumes" -> removeComposeVolumes = true
            else -> throw UsageException("unknown argument '$argument' for 'clean'")
        }
    }
    if (removeComposeVolumes && !downComposeProjects) throw UsageException("--compose-volumes needs --down-compose-projects")
    return CliCommand.Clean(
        confirmed = confirmed,
        deleteBranches = deleteBranches,
        stopGradleDaemons = stopGradleDaemons,
        stopMavenDaemons = stopMavenDaemons,
        stopEmulators = stopEmulators,
        deleteSimulators = deleteSimulators,
        deleteDerivedData = deleteDerivedData,
        downComposeProjects = downComposeProjects,
        removeComposeVolumes = removeComposeVolumes,
    )
}

internal fun parseEnv(rest: List<String>): CliCommand.Env {
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
