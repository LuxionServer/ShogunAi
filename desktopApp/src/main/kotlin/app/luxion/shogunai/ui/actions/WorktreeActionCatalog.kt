package app.luxion.shogunai.ui.actions

import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.ui.i18n.Strings

data class ActionTools(
    val runners: List<RunnerId>,
    val defaultRunner: RunnerId?,
    val terminal: String?,
    val editor: String?,
    val claudeDesktop: Boolean,
)

data class WorktreeActionContext(
    val tools: ActionTools,
    val isMain: Boolean,
    val isFolderMissing: Boolean,
    val agents: List<String>,
    val agent: String?,
    val opening: Set<OpenTarget> = emptySet(),
) {
    val hasAgentChoice: Boolean get() = agents.size > 1
}

data class WorktreeCatalog(val context: WorktreeActionContext, val actions: List<ActionSpec>) {
    fun find(id: ActionId): ActionSpec? = actions.firstOrNull { it.id == id }

    val visible: List<ActionSpec> get() = actions.filter { it.isVisible }

    val opens: List<ActionSpec> get() = visible.filter { it.id is ActionId.Open }

    val primary: ActionSpec?
        get() = opens.firstOrNull { (it.id as ActionId.Open).target != OpenTarget.CLAUDE_DESKTOP }

    val agentChoices: List<ActionSpec> get() = visible.filter { it.id is ActionId.ChooseAgent }
}

fun RunnerId.openTarget(): OpenTarget = when (this) {
    RunnerId.HERDR -> OpenTarget.HERDR
    RunnerId.ORCA -> OpenTarget.ORCA
}

fun runnerLabel(runner: RunnerId, strings: Strings): String = when (runner) {
    RunnerId.HERDR -> strings.openInHerdr
    RunnerId.ORCA -> strings.openInOrca
}

fun worktreeCatalog(context: WorktreeActionContext, strings: Strings): WorktreeCatalog {
    val tools = context.tools
    val runnerOrder = listOfNotNull(tools.defaultRunner) + RunnerId.entries.filter { it != tools.defaultRunner }
    val runners = runnerOrder.map { runner ->
        open(
            target = runner.openTarget(),
            label = runnerLabel(runner, strings),
            icon = ActionIcon.RUNNER,
            installed = runner in tools.runners,
            context = context,
            strings = strings,
            agentAware = true,
            shortcut = OpenShortcut.takeIf { runner == tools.defaultRunner },
        )
    }
    val others = listOf(
        open(OpenTarget.TERMINAL, tools.terminal?.let(strings::openInTool).orEmpty(), ActionIcon.TERMINAL, tools.terminal != null, context, strings, agentAware = true),
        open(OpenTarget.EDITOR, tools.editor?.let(strings::openInTool).orEmpty(), ActionIcon.EDITOR, tools.editor != null, context, strings),
        open(OpenTarget.CLAUDE_DESKTOP, strings.openInTool(strings.claudeDesktopName), ActionIcon.DESKTOP, tools.claudeDesktop, context, strings),
        open(OpenTarget.FOLDER, strings.openFolder, ActionIcon.FOLDER, true, context, strings),
    )
    val manage = listOf(
        ActionSpec(
            id = ActionId.OpenWith,
            scope = ActionScope.WORKTREE,
            group = ActionGroup.OPEN,
            label = strings.openWith,
            icon = ActionIcon.RUNNER,
            shortcut = OpenWithShortcut,
        ),
        ActionSpec(
            id = ActionId.CopyLaunchCommand,
            scope = ActionScope.WORKTREE,
            group = ActionGroup.COPY,
            label = strings.copyLaunchCommand,
            icon = ActionIcon.COPY,
            shortcut = CopyLaunchCommandShortcut,
            agentAware = true,
        ),
        ActionSpec(
            id = ActionId.CopyPath,
            scope = ActionScope.WORKTREE,
            group = ActionGroup.COPY,
            label = strings.copyPath,
            icon = ActionIcon.PATH,
        ),
        ActionSpec(
            id = ActionId.SetDefaultRunner,
            scope = ActionScope.APP,
            group = ActionGroup.MANAGE,
            label = strings.setDefaultRunner,
            icon = ActionIcon.SETTINGS,
        ),
    )
    val agents = context.agents.map { agent ->
        ActionSpec(
            id = ActionId.ChooseAgent(agent),
            scope = ActionScope.WORKTREE,
            group = ActionGroup.MANAGE,
            label = agent,
            icon = ActionIcon.AGENT,
            availability = if (context.hasAgentChoice) Availability.Available else Availability.Hidden,
        )
    }
    val remove = ActionSpec(
        id = ActionId.RemoveWorktree,
        scope = ActionScope.WORKTREE,
        group = ActionGroup.DESTRUCTIVE,
        label = strings.deleteWorktreeAction,
        icon = ActionIcon.DELETE,
        availability = if (context.isMain) Availability.Hidden else Availability.Available,
        shortcut = RemoveWorktreeShortcut,
        confirm = ConfirmKind.REMOVE_WORKTREE,
    )
    return WorktreeCatalog(context, runners + others + manage + agents + remove)
}

private fun open(
    target: OpenTarget,
    label: String,
    icon: ActionIcon,
    installed: Boolean,
    context: WorktreeActionContext,
    strings: Strings,
    agentAware: Boolean = false,
    shortcut: ActionShortcut? = null,
): ActionSpec = ActionSpec(
    id = ActionId.Open(target),
    scope = ActionScope.WORKTREE,
    group = ActionGroup.OPEN,
    label = label,
    icon = icon,
    availability = when {
        !installed -> Availability.Hidden
        context.isFolderMissing -> Availability.Disabled(strings.folderMissingReason)
        target in context.opening -> Availability.Disabled(strings.openingReason)
        else -> Availability.Available
    },
    shortcut = shortcut,
    agentAware = agentAware,
)
