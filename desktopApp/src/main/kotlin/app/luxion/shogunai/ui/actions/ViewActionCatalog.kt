package app.luxion.shogunai.ui.actions

import app.luxion.shogunai.ui.i18n.Strings

data class ViewActionContext(
    val isMultirepo: Boolean,
    val isProjectView: Boolean,
    val createBlocked: String?,
    val cleanUpBlocked: String?,
    val isOpeningMultirepoAgent: Boolean = false,
    val multirepoNeedsReview: String? = null,
)

data class ViewCatalog(val actions: List<ActionSpec>) {
    fun find(id: ActionId): ActionSpec? = actions.firstOrNull { it.id == id }

    val primary: ActionSpec? get() = actions.firstOrNull { it.id == ActionId.NewWorktree || it.id == ActionId.NewChange }?.takeIf { it.isVisible }

    val overflow: List<ActionSpec>
        get() = actions.filter { it.isVisible && (it.id == ActionId.OpenMultirepoAgent || it.id == ActionId.CleanUp) }
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
            ActionSpec(
                ActionId.OpenMultirepoAgent,
                ActionScope.MULTIREPO,
                ActionGroup.OPEN,
                strings.openMultirepoAgent,
                ActionIcon.RUNNER,
                when {
                    !context.isMultirepo -> Availability.Hidden
                    context.multirepoNeedsReview != null -> Availability.Disabled(context.multirepoNeedsReview)
                    context.isOpeningMultirepoAgent -> Availability.Disabled(strings.openingReason)
                    else -> Availability.Available
                },
            ),
            ActionSpec(
                ActionId.OpenAllInHerdr,
                ActionScope.MULTIREPO,
                ActionGroup.OPEN,
                strings.openAllInHerdr,
                ActionIcon.RUNNER,
                if (context.isMultirepo) Availability.Available else Availability.Hidden,
            ),
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

private fun availableUnless(reason: String?): Availability =
    reason?.let(Availability::Disabled) ?: Availability.Available
