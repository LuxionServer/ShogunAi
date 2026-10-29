package app.luxion.shogunai.ui.actions

import app.luxion.shogunai.ui.i18n.Strings

data class ViewActionContext(
    val isMultirepo: Boolean,
    val isProjectView: Boolean,
    val createBlocked: String?,
    val cleanUpBlocked: String?,
    val isOpeningMultirepoAgent: Boolean = false,
    val multirepoNeedsReview: String? = null,
    val orcaInstalled: Boolean = false,
)

data class GroupActionContext(
    val orcaInstalled: Boolean,
    val isOpeningInOrca: Boolean,
)

data class ViewCatalog(val actions: List<ActionSpec>) {
    fun find(id: ActionId): ActionSpec? = actions.firstOrNull { it.id == id }

    val primary: ActionSpec? get() = actions.firstOrNull { it.id == ActionId.NewWorktree || it.id == ActionId.NewChange }?.takeIf { it.isVisible }

    val overflow: List<ActionSpec>
        get() = actions.filter { it.isVisible && (it.id is ActionId.OpenMultirepoAgent || it.id == ActionId.CleanUp) }
}

fun viewOverflow(catalog: ViewCatalog): List<ActionEntry> = buildList {
    val (destructive, others) = catalog.overflow.partition { it.isDestructive }
    others.forEach { add(ActionEntry.Run(it)) }
    if (others.isNotEmpty() && destructive.isNotEmpty()) add(ActionEntry.Divider)
    destructive.forEach { add(ActionEntry.Run(it)) }
}

fun viewCatalog(context: ViewActionContext, strings: Strings): ViewCatalog {
    val multirepoScope = if (context.isMultirepo) ActionScope.MULTIREPO else ActionScope.PROJECT
    val create = if (context.isMultirepo) {
        ActionSpec(ActionId.NewChange, ActionScope.MULTIREPO, ActionGroup.MANAGE, strings.newChange, ActionIcon.ADD, availableUnless(context.multirepoNeedsReview ?: context.createBlocked))
    } else {
        ActionSpec(ActionId.NewWorktree, ActionScope.PROJECT, ActionGroup.MANAGE, strings.newWorktree, ActionIcon.ADD, availableUnless(context.createBlocked), shortcut = ActionShortcut("N"))
    }
    return ViewCatalog(
        listOf(
            ActionSpec(ActionId.Reload, ActionScope.APP, ActionGroup.MANAGE, strings.reload, ActionIcon.RELOAD, shortcut = ActionShortcut("R")),
            ActionSpec(
                ActionId.ProjectSettings,
                ActionScope.PROJECT,
                ActionGroup.MANAGE,
                strings.projectConfigTitle,
                ActionIcon.SETTINGS,
                if (context.isProjectView) Availability.Available else Availability.Hidden,
            ),
            multirepoAgent(OpenTarget.HERDR, strings.openMultirepoAgent, context, strings, installed = true),
            multirepoAgent(OpenTarget.ORCA, strings.openMultirepoAgentInOrca, context, strings, installed = context.orcaInstalled),
            ActionSpec(
                ActionId.CleanUp,
                multirepoScope,
                ActionGroup.DESTRUCTIVE,
                strings.cleanUp,
                ActionIcon.CLEAN,
                context.cleanUpBlocked?.let(Availability::Disabled) ?: Availability.Available,
                confirm = ConfirmKind.CLEAN_UP,
            ),
            create,
        ),
    )
}

fun groupCatalog(context: GroupActionContext, strings: Strings): List<ActionSpec> = listOf(
    ActionSpec(ActionId.OpenAll(OpenTarget.HERDR), ActionScope.MULTIREPO, ActionGroup.OPEN, strings.openAllInHerdr, ActionIcon.RUNNER),
    ActionSpec(
        ActionId.OpenAll(OpenTarget.ORCA),
        ActionScope.MULTIREPO,
        ActionGroup.OPEN,
        strings.openAllInOrca,
        ActionIcon.RUNNER,
        when {
            !context.orcaInstalled -> Availability.Hidden
            context.isOpeningInOrca -> Availability.Disabled(strings.openingReason)
            else -> Availability.Available
        },
    ),
)

private fun multirepoAgent(target: OpenTarget, label: String, context: ViewActionContext, strings: Strings, installed: Boolean): ActionSpec =
    ActionSpec(
        ActionId.OpenMultirepoAgent(target),
        ActionScope.MULTIREPO,
        ActionGroup.OPEN,
        label,
        ActionIcon.RUNNER,
        when {
            !context.isMultirepo || !installed -> Availability.Hidden
            context.multirepoNeedsReview != null -> Availability.Disabled(context.multirepoNeedsReview)
            context.isOpeningMultirepoAgent -> Availability.Disabled(strings.openingReason)
            else -> Availability.Available
        },
    )

private fun availableUnless(reason: String?): Availability =
    reason?.let(Availability::Disabled) ?: Availability.Available
