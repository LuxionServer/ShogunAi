package app.luxion.shogunai.cli

internal fun parseCompose(rest: List<String>): CliCommand {
    val subcommand = rest.firstOrNull() ?: return CliCommand.Compose()
    return when (subcommand) {
        "down" -> parseComposeDown(rest.drop(1))
        "up", "build" -> parseComposeRun(subcommand, rest.drop(1))
        "prune" -> parseComposePrune(rest.drop(1))
        else -> throw UsageException("unknown subcommand '$subcommand' for 'compose', expected up, build, down or prune")
    }
}

private fun parseComposeRun(subcommand: String, rest: List<String>): CliCommand {
    val options = rest.takeWhile { it != ARGUMENTS_SEPARATOR }
    var build = false
    val targets = mutableListOf<String>()
    options.forEach { argument ->
        when {
            argument == "--build" && subcommand == "up" -> build = true
            argument.startsWith("-") -> throw UsageException("unknown option '$argument' for 'compose $subcommand'")
            else -> targets += argument
        }
    }
    if (targets.size > 1) throw UsageException("'compose $subcommand' takes at most one worktree; pass Compose arguments after --")
    return CliCommand.ComposeRun(
        target = targets.firstOrNull(),
        build = build,
        onlyBuild = subcommand == "build",
        extraArguments = rest.drop(options.size + 1),
    )
}

private fun parseComposePrune(rest: List<String>): CliCommand {
    var volumes = false
    var confirmed = false
    rest.forEach { argument ->
        when (argument) {
            "--volumes" -> volumes = true
            "--yes", "-y" -> confirmed = true
            else -> throw UsageException("unknown argument '$argument' for 'compose prune'")
        }
    }
    return CliCommand.ComposePrune(volumes, confirmed)
}

private fun parseComposeDown(arguments: List<String>): CliCommand {
    var volumes = false
    val targets = mutableListOf<String>()
    arguments.forEach { argument ->
        when {
            argument == "--volumes" -> volumes = true
            argument.startsWith("-") -> throw UsageException("unknown option '$argument' for 'compose down'")
            else -> targets += argument
        }
    }
    if (targets.size > 1) throw UsageException("'compose down' takes at most one worktree")
    return CliCommand.ComposeDown(targets.firstOrNull(), volumes)
}

internal fun parseXcode(rest: List<String>): CliCommand {
    val subcommand = rest.firstOrNull() ?: return CliCommand.Xcode()
    if (subcommand == "trim") {
        val flags = rest.drop(1)
        flags.firstOrNull { it != "--dry-run" }?.let { throw UsageException("unknown argument '$it' for 'xcode trim', expected --dry-run") }
        return CliCommand.XcodeTrim(dryRun = flags.isNotEmpty())
    }
    if (subcommand != "clean") throw UsageException("unknown subcommand '$subcommand' for 'xcode', expected clean or trim")
    rest.drop(1).firstOrNull { it != "--swift-build" }?.let { throw UsageException("unknown argument '$it' for 'xcode clean', expected --swift-build") }
    return CliCommand.XcodeClean(swiftBuild = rest.size > 1)
}

internal fun parseGradle(rest: List<String>): CliCommand {
    val subcommand = rest.firstOrNull() ?: return CliCommand.Gradle()
    if (subcommand != "stop") throw UsageException("unknown subcommand '$subcommand' for 'gradle', expected stop")
    val pids = rest.drop(1).map { argument ->
        argument.toLongOrNull()?.takeIf { it > 0 }
            ?: throw UsageException("'gradle stop' takes process ids, got '$argument'")
    }
    return CliCommand.GradleStop(pids)
}

internal fun parseMaven(rest: List<String>): CliCommand {
    val subcommand = rest.firstOrNull() ?: return CliCommand.Maven()
    if (subcommand != "stop") throw UsageException("unknown subcommand '$subcommand' for 'maven', expected stop")
    val pids = rest.drop(1).map { argument ->
        argument.toLongOrNull()?.takeIf { it > 0 }
            ?: throw UsageException("'maven stop' takes process ids, got '$argument'")
    }
    return CliCommand.MavenStop(pids)
}

enum class PoolAction { STATUS, TRIM }

enum class ProjectAction { ADD, LIST, REMOVE }

internal fun parseProject(rest: List<String>): CliCommand {
    val name = rest.firstOrNull() ?: throw UsageException("'project' needs an action: add, list or remove")
    val action = ProjectAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'project', expected add, list or remove")
    val arguments = rest.drop(1)
    arguments.firstOrNull { it.startsWith("-") }?.let { throw UsageException("unknown option '$it' for 'project $name'") }
    if (action == ProjectAction.LIST) {
        arguments.firstOrNull()?.let { throw UsageException("'project list' takes no arguments, got '$it'") }
        return CliCommand.Project(action)
    }
    if (arguments.size > 1) throw UsageException("'project $name' takes at most one path")
    return CliCommand.Project(action, arguments.firstOrNull())
}

internal fun parseApp(rest: List<String>): CliCommand.App {
    val settings = "--settings" in rest
    val others = rest.filter { it != "--settings" }
    others.firstOrNull { it.startsWith("-") }?.let { throw UsageException("unknown option '$it' for 'app'") }
    if (others.size > 1) throw UsageException("'app' takes at most one path")
    return CliCommand.App(others.firstOrNull(), settings)
}

internal fun parsePool(rest: List<String>): CliCommand {
    val name = rest.firstOrNull() ?: throw UsageException("'pool' needs an action: status, trim, acquire or release")
    if (name in POOL_SLOT_ACTIONS) return parsePoolSlot(rest)
    val action = PoolAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'pool', expected status, trim, acquire or release")
    rest.drop(1).firstOrNull()?.let { throw UsageException("'pool $name' takes no arguments, got '$it'") }
    return CliCommand.Pool(action)
}
