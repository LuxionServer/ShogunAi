package app.luxion.shogunai.ui.actions

enum class ActionScope { APP, PROJECT, MULTIREPO, WORKTREE, MACHINE }

enum class ActionGroup { OPEN, COPY, PROVISION, RESOURCES, MANAGE, DESTRUCTIVE }

enum class OpenTarget { HERDR, ORCA, TERMINAL, EDITOR, CLAUDE_DESKTOP, FOLDER }

enum class ActionIcon { RUNNER, TERMINAL, EDITOR, DESKTOP, FOLDER, COPY, PATH, DELETE, ADD, SETTINGS, RELOAD, CLEAN, AGENT, MORE }

enum class ConfirmKind { REMOVE_WORKTREE, CLEAN_UP }

sealed interface ActionId {
    val key: String

    data class Open(val target: OpenTarget) : ActionId {
        override val key get() = "worktree.open.${target.name.lowercase().replace('_', '-')}"
    }

    data object OpenWith : ActionId {
        override val key = "worktree.open-with"
    }

    data object CopyLaunchCommand : ActionId {
        override val key = "worktree.copy.launch-command"
    }

    data object CopyPath : ActionId {
        override val key = "worktree.copy.path"
    }

    data class ChooseAgent(val agent: String) : ActionId {
        override val key get() = "worktree.agent($agent)"
    }

    data object RemoveWorktree : ActionId {
        override val key = "worktree.remove"
    }

    data object SetDefaultRunner : ActionId {
        override val key = "app.default-runner"
    }

    data object NewWorktree : ActionId {
        override val key = "project.new-worktree"
    }

    data object NewChange : ActionId {
        override val key = "multirepo.new-change"
    }

    data object ProjectSettings : ActionId {
        override val key = "project.settings"
    }

    data object Reload : ActionId {
        override val key = "app.reload"
    }

    data object CleanUp : ActionId {
        override val key = "project.clean-up"
    }

    data object OpenMultirepoAgent : ActionId {
        override val key = "multirepo.open-agent.herdr"
    }

    data object OpenAllInHerdr : ActionId {
        override val key = "multirepo.open-all.herdr"
    }
}

sealed interface Availability {
    data object Available : Availability
    data object Hidden : Availability
    data class Disabled(val reason: String) : Availability
}

data class ActionShortcut(val key: String, val shift: Boolean = false)

data class ActionSpec(
    val id: ActionId,
    val scope: ActionScope,
    val group: ActionGroup,
    val label: String,
    val icon: ActionIcon,
    val availability: Availability = Availability.Available,
    val shortcut: ActionShortcut? = null,
    val agentAware: Boolean = false,
    val confirm: ConfirmKind? = null,
) {
    val isVisible: Boolean get() = availability != Availability.Hidden
    val isAvailable: Boolean get() = availability == Availability.Available
    val disabledReason: String? get() = (availability as? Availability.Disabled)?.reason
    val isDestructive: Boolean get() = group == ActionGroup.DESTRUCTIVE
}

val OpenShortcut = ActionShortcut("O")
val OpenWithShortcut = ActionShortcut("O", shift = true)
val CopyLaunchCommandShortcut = ActionShortcut("C", shift = true)
val RemoveWorktreeShortcut = ActionShortcut("⌫")
