package app.luxion.shogunai.cli

sealed interface ParsedArguments {
    data class Command(val command: CliCommand) : ParsedArguments
    data class UsageError(val message: String) : ParsedArguments
}

fun parseArguments(args: List<String>): ParsedArguments {
    val name = args.firstOrNull() ?: return ParsedArguments.Command(CliCommand.Help)
    val rest = args.drop(1)
    val options = rest.takeWhile { it != ARGUMENTS_SEPARATOR }
    val passedThrough = rest.drop(options.size)
    if (name in CliHelp.commandNames && options.any { it == "--help" || it == "-h" }) {
        return ParsedArguments.Command(CliCommand.CommandHelp(name))
    }
    val json = name in JSON_COMMANDS && !isSimulatorGradleInit(name, rest) && !isGuideReading(name, rest) && JSON_FLAG in options
    val arguments = if (json) options.filter { it != JSON_FLAG } + passedThrough else rest
    return try {
        ParsedArguments.Command(
            when (name) {
                "help", "-h", "--help" -> noArguments(name, rest, CliCommand.Help)
                "version", "-v", "--version" -> noArguments(name, rest, CliCommand.Version)
                "new" -> parseNew(arguments)
                "multirepo" -> parseMultirepo(arguments)
                "provision" -> parseProvision(arguments)
                "trust" -> parseTrust(arguments)
                "remove" -> parseRemove(arguments)
                "repair" -> noArguments(name, arguments, CliCommand.Repair())
                "open" -> parseOpen(arguments)
                "status" -> parseStatus(arguments)
                "base" -> parseBase(arguments)
                "overlap" -> parseOverlap(arguments)
                "clean" -> parseClean(arguments)
                "hook" -> parseHook(arguments)
                "hooks" -> parseHooks(arguments)
                "guide" -> parseGuide(arguments)
                "gradle" -> parseGradle(arguments)
                "stacks" -> noArguments(name, arguments, CliCommand.Stacks())
                "doctor" -> noArguments(name, arguments, CliCommand.Doctor())
                "maven" -> parseMaven(arguments)
                "compose" -> parseCompose(arguments)
                "pool" -> parsePool(arguments)
                "project" -> parseProject(arguments)
                "app" -> parseApp(arguments)
                "xcode" -> parseXcode(arguments)
                "env" -> parseEnv(arguments)
                "emulator" -> parseEmulator(arguments)
                "simulator" -> parseSimulator(arguments)
                "headless" -> parseHeadless(arguments)
                "desktop" -> parseDesktop(arguments)
                "build" -> parseBuild(arguments)
                "device" -> parseDevice(arguments)
                else -> throw UsageException("unknown command '$name'")
            }.withJsonOutput(json),
        )
    } catch (error: UsageException) {
        ParsedArguments.UsageError(error.message)
    }
}

private const val JSON_FLAG = "--json"

internal const val ARGUMENTS_SEPARATOR = "--"

private val JSON_COMMANDS = setOf("new", "base", "multirepo", "provision", "trust", "remove", "repair", "open", "clean", "gradle", "stacks", "maven", "compose", "pool", "project", "xcode", "emulator", "simulator", "headless", "desktop", "device", "hooks", "guide", "doctor")

private fun isSimulatorGradleInit(name: String, rest: List<String>) = name == "simulator" && rest.firstOrNull() == "gradle-init"

private fun isGuideReading(name: String, rest: List<String>) = name == "guide" && rest.firstOrNull() !in GUIDE_ACTIONS

private fun CliCommand.withJsonOutput(json: Boolean): CliCommand = if (json) (this as JsonOutput).withJson() else this

internal class UsageException(override val message: String) : Exception(message)

internal fun noArguments(name: String, rest: List<String>, command: CliCommand): CliCommand {
    rest.firstOrNull()?.let {
        if (it.startsWith("-")) throw UsageException("unknown option '$it' for '$name'")
        throw UsageException("'$name' takes no arguments, got '$it'")
    }
    return command
}

internal fun ArrayDeque<String>.valueOf(option: String): String {
    val value = removeFirstOrNull()
    if (value == null || value.startsWith("-")) throw UsageException("$option needs a value")
    return value
}
