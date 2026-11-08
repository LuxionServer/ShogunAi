package app.luxion.shogunai.ui.actions

import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.ui.i18n.Strings

data class ViewActionContext(
    val isMultirepo: Boolean,
    val isProjectView: Boolean,
    val createBlocked: String?,
    val cleanUpBlocked: String?,
    val offersCleanUp: Boolean = true,
    val cleanUpCount: Int = 0,
    val isOpeningMultirepoAgent: Boolean = false,
    val multirepoNeedsReview: String? = null,
    val orcaInstalled: Boolean = false,
    val defaultRunner: RunnerId = RunnerId.HERDR,
)

data class GroupActionContext(
    val orcaInstalled: Boolean,
    val isOpeningInOrca: Boolean,
    val defaultRunner: RunnerId = RunnerId.HERDR,
    val addableAliases: List<String> = emptyList(),
    val isAdding: Boolean = false,
    val cleanUpBlocked: String? = null,
)

data class ViewCatalog(val actions: List<ActionSpec>, val defaultRunner: RunnerId = RunnerId.HERDR) {
    fun find(id: ActionId): ActionSpec? = actions.firstOrNull { it.id == id }

    val primary: ActionSpec? get() = actions.firstOrNull { it.id == ActionId.NewWorktree || it.id == ActionId.NewChange }?.takeIf { it.isVisible }

    val cleanUp: ActionSpec? get() = find(ActionId.CleanUp)?.takeIf { it.isVisible }

    val overflow: List<ActionSpec>
        get() = actions.filter { it.isVisible && it.id is ActionId.OpenMultirepoAgent }
}

fun viewOverflow(catalog: ViewCatalog, strings: Strings): List<ActionEntry> =
    withDefaultRunner(catalog.overflow, catalog.defaultRunner, strings.openMultirepoAgentWith) { (it.id as? ActionId.OpenMultirepoAgent)?.target }

data class GroupCatalog(val context: GroupActionContext, val actions: List<ActionSpec>) {
    fun find(id: ActionId): ActionSpec? = actions.firstOrNull { it.id == id }

    val opens: List<ActionSpec> get() = actions.filter { it.isVisible && it.id is ActionId.OpenAll }

    val primary: ActionSpec?
        get() = opens.firstOrNull { (it.id as ActionId.OpenAll).target == context.defaultRunner.openTarget() } ?: opens.firstOrNull()

    val isBusy: Boolean get() = context.isAdding || opens.any { it.disabledReason != null }

    fun overflowEntries(strings: Strings): List<ActionEntry> = buildList {
        val others = opens.filter { it != primary }
        if (others.isNotEmpty()) add(ActionEntry.Nested(strings.openAllWith, others.map { ActionEntry.Run(it, label = it.runnerName()) }))
        val additions = actions.filter { it.isVisible && it.id is ActionId.AddRepository }
        if (additions.isNotEmpty()) add(ActionEntry.Nested(strings.addRepositoryToGroup, additions.map { ActionEntry.Run(it) }))
        find(ActionId.CleanUpGroup)?.let { cleanUp ->
            if (isNotEmpty()) add(ActionEntry.Divider)
            add(ActionEntry.Run(cleanUp))
        }
    }
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
                if (context.cleanUpCount > 0) strings.cleanUpCount(context.cleanUpCount) else strings.cleanUp,
                ActionIcon.CLEAN,
                if (context.offersCleanUp) availableUnless(context.cleanUpBlocked) else Availability.Hidden,
                confirm = ConfirmKind.CLEAN_UP,
            ),
            create,
        ),
        context.defaultRunner,
    )
}

fun groupCatalog(context: GroupActionContext, strings: Strings): GroupCatalog = GroupCatalog(
    context,
    listOf(
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
    ) + context.addableAliases.map { alias ->
        ActionSpec(
            ActionId.AddRepository(alias),
            ActionScope.MULTIREPO,
            ActionGroup.MANAGE,
            alias,
            ActionIcon.ADD,
        )
    } + ActionSpec(
        ActionId.CleanUpGroup,
        ActionScope.MULTIREPO,
        ActionGroup.DESTRUCTIVE,
        strings.cleanUpGroup,
        ActionIcon.CLEAN,
        availableUnless(context.cleanUpBlocked),
        confirm = ConfirmKind.CLEAN_UP,
    ),
)

fun projectGroupOverflow(cleanUpBlocked: String?, strings: Strings): List<ActionEntry> = listOf(
    ActionEntry.Run(
        ActionSpec(
            ActionId.CleanUp,
            ActionScope.PROJECT,
            ActionGroup.DESTRUCTIVE,
            strings.cleanUp,
            ActionIcon.CLEAN,
            availableUnless(cleanUpBlocked),
            confirm = ConfirmKind.CLEAN_UP,
        ),
    ),
)

private fun withDefaultRunner(
    specs: List<ActionSpec>,
    defaultRunner: RunnerId,
    otherRunnersLabel: String,
    targetOf: (ActionSpec) -> OpenTarget?,
): List<ActionEntry> {
    val (runners, rest) = specs.partition { targetOf(it) != null }
    val primary = runners.firstOrNull { targetOf(it) == defaultRunner.openTarget() } ?: runners.firstOrNull()
    val others = runners.filter { it != primary }
    return listOfNotNull(
        primary?.let { ActionEntry.Run(it) },
        others.takeIf { it.isNotEmpty() }?.let { specs -> ActionEntry.Nested(otherRunnersLabel, specs.map { ActionEntry.Run(it, label = it.runnerName()) }) },
    ) + rest.map { ActionEntry.Run(it) }
}

private fun ActionSpec.runnerName(): String {
    val target = when (val id = id) {
        is ActionId.OpenAll -> id.target
        is ActionId.OpenMultirepoAgent -> id.target
        else -> null
    }
    return target?.runnerName() ?: label
}

private fun OpenTarget.runnerName(): String? = RunnerId.entries.firstOrNull { it.openTarget() == this }?.displayName

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
