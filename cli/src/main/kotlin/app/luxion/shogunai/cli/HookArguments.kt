package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.WorkflowTopic

internal fun parseHook(rest: List<String>): CliCommand {
    val event = rest.firstOrNull() ?: throw UsageException("'hook' needs an event: $HOOK_EVENTS")
    if (event == "orca") return parseHookOrca(rest.drop(1))
    val command = when (event) {
        "worktree-create" -> CliCommand.HookWorktreeCreate
        "worktree-remove" -> CliCommand.HookWorktreeRemove
        "session-start" -> CliCommand.HookSessionStart
        "herdr" -> CliCommand.HookHerdr
        "agent-state" -> CliCommand.HookAgentState
        "notify-pending" -> CliCommand.HookNotifyPending
        "desktop-turn" -> CliCommand.HookDesktopTurn
        "build-turn" -> HookBuildTurn
        else -> throw UsageException("unknown hook '$event', expected $HOOK_EVENTS")
    }
    return noArguments("hook $event", rest.drop(1), command)
}

private const val HOOK_EVENTS = "worktree-create, worktree-remove, session-start, agent-state, desktop-turn, build-turn, herdr or orca"

enum class OrcaHookAction { SETUP, ARCHIVE }

private fun parseHookOrca(rest: List<String>): CliCommand {
    val name = rest.firstOrNull() ?: throw UsageException("'hook orca' needs an action: setup or archive")
    val action = OrcaHookAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'hook orca', expected setup or archive")
    return noArguments("hook orca $name", rest.drop(1), CliCommand.HookOrca(action))
}

enum class HooksAction { INSTALL, UNINSTALL, STATUS }

internal fun parseHooks(rest: List<String>): CliCommand {
    val name = rest.firstOrNull() ?: throw UsageException("'hooks' needs an action: install, uninstall or status")
    val action = HooksAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'hooks', expected install, uninstall or status")
    var project = false
    var yes = false
    var herdr = false
    rest.drop(1).forEach { argument ->
        when {
            argument == "--project" -> project = true
            argument == "--yes" || argument == "-y" -> yes = true
            argument == "--herdr" -> herdr = true
            argument.startsWith("-") -> throw UsageException("unknown option '$argument' for 'hooks'")
            else -> throw UsageException("'hooks $name' takes no arguments, got '$argument'")
        }
    }
    if (herdr && action != HooksAction.INSTALL) throw UsageException("'--herdr' only applies to 'hooks install'")
    if (herdr && project) throw UsageException("'--herdr' can't be combined with '--project'")
    return CliCommand.Hooks(action, project, yes, herdr = herdr)
}

enum class GuideAction { INSTALL, UNINSTALL, STATUS }

internal val GUIDE_ACTIONS = GuideAction.entries.map { it.name.lowercase() }.toSet()

val GUIDE_AGENTS = listOf("codex", "gemini")

internal fun parseGuide(rest: List<String>): CliCommand {
    val action = rest.firstOrNull()?.let { name -> GuideAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    return if (action != null) parseGuideBlock(action, rest.drop(1)) else parseGuideReading(rest)
}

private fun parseGuideReading(rest: List<String>): CliCommand.Guide {
    var topic: String? = null
    var plain = false
    var migrate = false
    var file: String? = null
    val remaining = ArrayDeque(rest)
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--plain" -> plain = true
            "--migrate" -> migrate = true
            "--file" -> file = remaining.valueOf(argument)
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'guide'")
                if (topic != null) throw UsageException("'guide' takes a single topic, got '$topic' and '$argument'")
                if (WorkflowTopic.parse(argument) == null) throw UsageException("unknown topic '$argument' for 'guide', expected ${guideTopics()}")
                topic = argument
            }
        }
    }
    if (file != null && !migrate) throw UsageException("--file only applies to 'guide --migrate'")
    if (migrate && (plain || topic != null)) throw UsageException("--migrate can't be combined with a topic or --plain")
    return CliCommand.Guide(topic, plain, migrate, file)
}

private fun guideTopics() = WorkflowTopic.entries.joinToString(", ") { it.id }

private fun parseGuideBlock(action: GuideAction, rest: List<String>): CliCommand.GuideBlock {
    var agent: String? = null
    var project = false
    var file: String? = null
    var yes = false
    val remaining = ArrayDeque(rest)
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--agent" -> agent = remaining.valueOf(argument)
            "--project" -> project = true
            "--file" -> file = remaining.valueOf(argument)
            "--yes", "-y" -> yes = true
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'guide ${action.name.lowercase()}'")
                throw UsageException("'guide ${action.name.lowercase()}' takes no arguments, got '$argument'")
            }
        }
    }
    if (agent == "cursor") {
        throw UsageException("Cursor has no user-level instructions file; use --project (its AGENTS.md) or --file")
    }
    if (agent != null && agent !in GUIDE_AGENTS) {
        throw UsageException("unknown agent '$agent' for 'guide', expected ${GUIDE_AGENTS.joinToString(", ")}")
    }
    val targets = listOfNotNull(agent, "project".takeIf { project }, file)
    if (action != GuideAction.STATUS && targets.isEmpty()) {
        throw UsageException("'guide ${action.name.lowercase()}' needs where: --agent <${GUIDE_AGENTS.joinToString("|")}>, --project or --file <path>")
    }
    if (targets.size > 1) throw UsageException("--agent, --project and --file can't be combined")
    return CliCommand.GuideBlock(action, agent, project, file, yes)
}
