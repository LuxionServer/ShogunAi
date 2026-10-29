package app.luxion.shogunai.ui.actions

import app.luxion.shogunai.ui.i18n.Strings

sealed interface ActionEntry {
    data class Run(
        val spec: ActionSpec,
        val agent: String? = null,
        val label: String = spec.label,
        val checked: Boolean = false,
        val marked: Boolean = false,
    ) : ActionEntry

    data class Nested(val label: String, val entries: List<ActionEntry>, val run: Run? = null) : ActionEntry

    data class Heading(val title: String) : ActionEntry

    data object Divider : ActionEntry
}

fun WorktreeCatalog.withAgents(spec: ActionSpec, marked: Boolean = false): ActionEntry {
    val run = ActionEntry.Run(spec, marked = marked)
    if (!spec.agentAware || !context.hasAgentChoice || !spec.isAvailable) return run
    val agents = context.agents.map { ActionEntry.Run(spec, agent = it, label = it, checked = it == context.agent) }
    return ActionEntry.Nested(spec.label, agents, run)
}

fun WorktreeCatalog.openWithEntries(strings: Strings): List<ActionEntry> = buildList {
    val defaultTarget = context.tools.defaultRunner?.openTarget()
    opens.forEach { add(withAgents(it, marked = (it.id as ActionId.Open).target == defaultTarget)) }
    add(ActionEntry.Divider)
    find(ActionId.CopyLaunchCommand)?.let { add(withAgents(it)) }
    if (context.tools.runners.isNotEmpty()) find(ActionId.SetDefaultRunner)?.let { add(ActionEntry.Run(it)) }
    if (agentChoices.isNotEmpty()) {
        add(ActionEntry.Divider)
        add(ActionEntry.Heading(strings.withAgent))
        agentChoices.forEach { add(ActionEntry.Run(it, checked = it.label == context.agent)) }
    }
}

fun WorktreeCatalog.overflowEntries(): List<ActionEntry> = buildList {
    find(ActionId.CopyPath)?.let { add(ActionEntry.Run(it)) }
    destructive().takeIf { it.isNotEmpty() }?.let {
        add(ActionEntry.Divider)
        addAll(it)
    }
}

fun WorktreeCatalog.contextMenuEntries(strings: Strings): List<ActionEntry> = buildList {
    primary?.let { add(ActionEntry.Run(it)) }
    add(ActionEntry.Nested(strings.openWithMenu, openWithEntries(strings)))
    find(ActionId.CopyLaunchCommand)?.let { add(ActionEntry.Run(it)) }
    find(ActionId.CopyPath)?.let { add(ActionEntry.Run(it)) }
    destructive().takeIf { it.isNotEmpty() }?.let {
        add(ActionEntry.Divider)
        addAll(it)
    }
}

val WorktreeCatalog.iconActions: List<ActionSpec>
    get() = listOf(ActionId.CopyLaunchCommand, ActionId.Open(OpenTarget.TERMINAL), ActionId.Open(OpenTarget.FOLDER))
        .mapNotNull { id -> find(id)?.takeIf { it.isVisible } }

data class PaletteEntry(val spec: ActionSpec, val agent: String?, val title: String) {
    val key: String get() = spec.id.key + (agent?.let { ":$it" } ?: "")
}

fun WorktreeCatalog.paletteEntries(strings: Strings): List<PaletteEntry> =
    visible.filter { it.isAvailable && it.inPalette() }.flatMap { spec ->
        val plain = PaletteEntry(spec, null, spec.label)
        if (!spec.agentAware || !context.hasAgentChoice) {
            listOf(plain)
        } else {
            listOf(plain) + context.agents.filter { it != context.agent }.map { PaletteEntry(spec, it, strings.actionWithAgent(spec.label, it)) }
        }
    }

data class TrayActionEntries(val primary: ActionSpec?, val openWith: List<ActionSpec>, val copyLaunchCommand: ActionSpec?)

fun WorktreeCatalog.trayEntries(): TrayActionEntries {
    val primary = primary?.takeIf { it.isAvailable }
    return TrayActionEntries(
        primary = primary,
        openWith = opens.filter { it.isAvailable && it != primary },
        copyLaunchCommand = find(ActionId.CopyLaunchCommand),
    )
}

fun WorktreeCatalog.menuBarEntries(): List<ActionSpec> = listOfNotNull(
    primary,
    find(ActionId.OpenWith),
    find(ActionId.CopyLaunchCommand),
    find(ActionId.RemoveWorktree)?.takeIf { it.isVisible },
)

private fun WorktreeCatalog.destructive(): List<ActionEntry> =
    visible.filter { it.isDestructive }.map { ActionEntry.Run(it) }

private fun ActionSpec.inPalette(): Boolean =
    !isDestructive && id !is ActionId.ChooseAgent && id != ActionId.OpenWith && id != ActionId.SetDefaultRunner
