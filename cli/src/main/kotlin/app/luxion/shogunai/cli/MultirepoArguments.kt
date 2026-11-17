package app.luxion.shogunai.cli

sealed interface MultirepoAction {
    data class Init(
        val worktreesRoot: String? = null,
        val name: String? = null,
        val useWorktreesRoot: Boolean? = null,
        val yes: Boolean = false,
    ) : MultirepoAction
    data class Name(val name: String?) : MultirepoAction
    data object Show : MultirepoAction
    data class AddRepo(val alias: String, val path: String, val useWorktreesRoot: Boolean? = null) : MultirepoAction
    data class RemoveRepo(val alias: String) : MultirepoAction
    data class Defaults(val aliases: List<String>) : MultirepoAction
    data class Agent(val command: String, val name: String? = null, val useHeadroom: Boolean = false) : MultirepoAction
    data class WorktreesRoot(val folder: String) : MultirepoAction
    data class WorkspaceSet(val alias: String? = null, val default: Boolean = false) : MultirepoAction
    data object WorkspaceClear : MultirepoAction
    data object WorkspaceDetect : MultirepoAction
    data object EnvList : MultirepoAction
    data class EnvSet(val alias: String, val name: String, val template: String) : MultirepoAction
    data class EnvUnset(val alias: String, val name: String) : MultirepoAction
    data object Delete : MultirepoAction
    data class Trust(val folder: String? = null, val yes: Boolean = false, val revoke: Boolean = false) : MultirepoAction
}

private fun multirepoName(name: String): String =
    name.trim().ifEmpty { throw UsageException("the multirepo name can't be blank") }

internal fun parseMultirepo(rest: List<String>): CliCommand.Multirepo {
    val action = rest.firstOrNull() ?: throw UsageException("'multirepo' needs an action: init, show, trust, name, add-repo, remove-repo, defaults, agent, workspace, env, worktrees-root or delete")
    val remaining = ArrayDeque(rest.drop(1))
    val positional = mutableListOf<String>()
    val options = mutableMapOf<String, String?>()
    while (remaining.isNotEmpty()) {
        val argument = remaining.removeFirst()
        when (argument) {
            "--worktrees-root", "--name", "--alias" -> options[argument] = remaining.valueOf(argument)
            "--use-worktrees-root", "--keep-worktrees-folder", "--headroom", "--no-headroom", "--clear", "--default", "--yes", "--revoke" -> options[argument] = null
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'multirepo $action'")
                positional += argument
            }
        }
    }
    fun allow(vararg names: String) {
        options.keys.firstOrNull { it !in names }?.let { throw UsageException("unknown option '$it' for 'multirepo $action'") }
    }
    fun arguments(count: Int, usage: String): List<String> {
        if (positional.size != count) throw UsageException("usage: shogun multirepo $usage")
        return positional
    }
    fun worktreesFolderChoice(): Boolean? {
        val use = "--use-worktrees-root" in options
        val keep = "--keep-worktrees-folder" in options
        if (use && keep) throw UsageException("--use-worktrees-root and --keep-worktrees-folder can't be combined")
        return if (use) true else if (keep) false else null
    }
    return CliCommand.Multirepo(
        when (action) {
            "init" -> {
                allow("--worktrees-root", "--name", "--use-worktrees-root", "--keep-worktrees-folder", "--yes")
                arguments(0, "init [--worktrees-root <dir>] [--name <name>] [--use-worktrees-root | --keep-worktrees-folder] [--yes]")
                MultirepoAction.Init(options["--worktrees-root"], options["--name"]?.let(::multirepoName), worktreesFolderChoice(), yes = "--yes" in options)
            }
            "show" -> { allow(); arguments(0, "show"); MultirepoAction.Show }
            "name" -> {
                allow("--clear")
                if ("--clear" in options) {
                    arguments(0, "name <name> | --clear")
                    MultirepoAction.Name(null)
                } else {
                    MultirepoAction.Name(multirepoName(arguments(1, "name <name> | --clear").single()))
                }
            }
            "add-repo" -> {
                allow("--use-worktrees-root", "--keep-worktrees-folder")
                val (alias, path) = arguments(2, "add-repo <alias> <path> [--use-worktrees-root | --keep-worktrees-folder]")
                MultirepoAction.AddRepo(alias, path, worktreesFolderChoice())
            }
            "remove-repo" -> { allow(); MultirepoAction.RemoveRepo(arguments(1, "remove-repo <alias>").single()) }
            "defaults" -> {
                allow("--clear")
                if ("--clear" in options) {
                    arguments(0, "defaults --clear")
                    MultirepoAction.Defaults(emptyList())
                } else {
                    MultirepoAction.Defaults(arguments(1, "defaults <alias>[,<alias>...] | --clear").single().split(',').map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
            "agent" -> {
                allow("--name", "--headroom", "--no-headroom")
                val headroom = "--headroom" in options
                if (headroom && "--no-headroom" in options) throw UsageException("--headroom and --no-headroom can't be combined")
                MultirepoAction.Agent(arguments(1, "agent <command> [--name <name>] [--headroom]").single(), options["--name"], headroom)
            }
            "workspace" -> {
                val usage = "usage: shogun multirepo workspace set [--alias <alias>] [--default] | clear | detect"
                val what = positional.firstOrNull() ?: throw UsageException(usage)
                when (what) {
                    "set" -> { allow("--alias", "--default"); arguments(1, "workspace set [--alias <alias>] [--default]"); MultirepoAction.WorkspaceSet(options["--alias"], "--default" in options) }
                    "clear" -> { allow(); arguments(1, "workspace clear"); MultirepoAction.WorkspaceClear }
                    "detect" -> { allow(); arguments(1, "workspace detect"); MultirepoAction.WorkspaceDetect }
                    else -> throw UsageException(usage)
                }
            }
            "env" -> {
                allow()
                when (positional.firstOrNull()) {
                    null -> MultirepoAction.EnvList
                    "set" -> {
                        val (_, alias, name, template) = arguments(4, "env set <alias> <name> <template>")
                        MultirepoAction.EnvSet(alias, name, template)
                    }
                    "unset" -> {
                        val (_, alias, name) = arguments(3, "env unset <alias> <name>")
                        MultirepoAction.EnvUnset(alias, name)
                    }
                    else -> throw UsageException("usage: shogun multirepo env [set <alias> <name> <template> | unset <alias> <name>]")
                }
            }
            "worktrees-root" -> { allow(); MultirepoAction.WorktreesRoot(arguments(1, "worktrees-root <dir>").single()) }
            "delete" -> { allow(); arguments(0, "delete"); MultirepoAction.Delete }
            "trust" -> {
                allow("--yes", "--revoke")
                if ("--yes" in options && "--revoke" in options) throw UsageException("--yes and --revoke can't be combined")
                if (positional.size > 1) throw UsageException("usage: shogun multirepo trust [<folder>] [--yes | --revoke]")
                MultirepoAction.Trust(positional.firstOrNull(), yes = "--yes" in options, revoke = "--revoke" in options)
            }
            else -> throw UsageException("unknown multirepo action '$action'")
        },
    )
}
