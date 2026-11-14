package app.luxion.shogunai.cli

import java.time.Duration

enum class BuildAction { RUN, STATUS }

data class BuildCommand(
    val action: BuildAction,
    val command: List<String> = emptyList(),
    val wait: Duration? = null,
    val json: Boolean = false,
) : CliCommand

data object HookBuildTurn : CliCommand

internal fun parseBuild(rest: List<String>): BuildCommand {
    val name = rest.firstOrNull() ?: throw UsageException("'build' needs an action: run or status")
    val action = BuildAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'build', expected run or status")
    return if (action == BuildAction.RUN) parseBuildRun(rest.drop(1)) else parseBuildStatus(rest.drop(1))
}

private fun parseBuildRun(rest: List<String>): BuildCommand {
    val separator = rest.indexOf(BUILD_SEPARATOR)
    if (separator < 0) throw UsageException("'build run' needs the command after '--', e.g. shogun build run -- ./gradlew test")
    val command = rest.drop(separator + 1)
    if (command.isEmpty()) throw UsageException("'build run' needs a command after '--'")
    val wait = DeviceWaitOptions()
    val remaining = ArrayDeque(rest.take(separator))
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--wait-timeout" -> wait.timeout(remaining.valueOf(argument))
            else -> throw UsageException("unknown option '$argument' for 'build run'")
        }
    }
    return BuildCommand(BuildAction.RUN, command, wait.duration)
}

private fun parseBuildStatus(rest: List<String>): BuildCommand {
    rest.firstOrNull { it != JSON_OPTION }?.let { throw UsageException("'build status' takes only --json, got '$it'") }
    return BuildCommand(BuildAction.STATUS, json = JSON_OPTION in rest)
}

private const val BUILD_SEPARATOR = "--"

private const val JSON_OPTION = "--json"
