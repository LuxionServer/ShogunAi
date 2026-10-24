package app.luxion.shogunai.cli

internal object CliHelp {

    private val sections: Map<String, String> = linkedMapOf(
        "new" to """
  new <task-id> [--fix] [--from <ref>] [--fetch] [--json]
                              Create a worktree on a new feature/ (or fix/) branch and print its path;
                              the branch starts at the current worktree's HEAD unless --from is given
  new --branch <branch> [--fetch]
                              Create a worktree for an existing branch and print its path; a branch that
                              exists only on origin gets a local branch tracking it
      --fetch                 Fetch from origin first, so the start point or branch is current
      --worktrees-root <dir>  Folder for the worktree when the repository has no ShogunAi project
      --json                  Print one JSON object instead of the path; never asks about post-creation commands
  new <task-id> [--repos <alias>[,<alias>...]] [--fix] [--fetch] [--json]
                              In a multirepo: create the group's worktrees, one per repository, each on the
                              same feature/ (or fix/) branch from its repository's origin/<base> and in its
                              project's worktrees folder; without --repos, from the multirepo folder, the
                              default repositories; repositories already on the branch are left as they are
""".trimIndent(),
        "provision" to """
  provision [<path>] [--json] Copy or link the declared files into an existing worktree and run its
                              post-creation commands (default: current directory)
  provision --check [<path>]  List the declared files a worktree is missing, has stale or doesn't link;
                              exit code 3 when there are any, and nothing is changed
                              --json prints one JSON object instead of text and never asks a question
""".trimIndent(),
        "trust" to """
  trust [<path>] [--yes] [--revoke] [--json]
                              Show the post-creation commands the repository declares and whether they are
                              approved on this machine; approve them (--yes skips the question) or revoke;
                              --json prints one JSON object and approves only with --yes
""".trimIndent(),
        "status" to """
  status [--json]             List the repository's worktrees with their state; in a multirepo folder, its
                              groups (the same branch in several repositories) and the ungrouped worktrees
""".trimIndent(),
        "remove" to """
  remove <task id | path | branch> [--force] [--delete-branch | --force-delete-branch] [--json]
                              Remove one worktree and stop its emulator, simulator and idle Gradle daemons;
                              refused while it has changes or an agent running unless --force;
                              --delete-branch also deletes its branch when it is merged into the main branch,
                              --force-delete-branch deletes it even if it is not merged;
                              --json prints one JSON object instead of text
""".trimIndent(),
        "open" to """
  open <task id | path | branch> --herdr [--agent <name>] [--json]
                              Open the worktree in Herdr with its environment and the project's agent
                              (--agent picks another of the project's agents by name), or focus its
                              workspace when Herdr already has it open;
                              --json prints one JSON object instead of text
  open <task id | branch> --herdr [--json]
                              In a multirepo folder: open every worktree of the group, each under its
                              repository's space with its environment and its project's agent
  open --herdr [--json]       In a multirepo folder: open the multirepo's agent in a workspace of its own
""".trimIndent(),
        "repair" to """
  repair [--json]             Re-link the worktrees after the repository or the worktrees folder moved
""".trimIndent(),
        "clean" to """
  clean [--yes] [--delete-branches] [--stop-gradle-daemons] [--stop-emulators] [--delete-simulators]
        [--delete-derived-data] [--down-compose-projects] [--json]
                              List merged or missing worktrees; remove them with --yes
                              (--stop-gradle-daemons / --stop-emulators: also stop their idle
                              Gradle daemons / their emulators; --delete-simulators: also delete
                              their iOS simulators and orphaned ShogunAi simulators;
                              --delete-derived-data: also delete orphaned Xcode DerivedData;
                              --down-compose-projects: also take down the Docker Compose projects of
                              the removed worktrees, keeping their volumes;
                              --json: print one JSON object instead of text);
                              in a multirepo folder, the groups whose worktrees are all merged and have no
                              agent, with the same options
""".trimIndent(),
        "multirepo" to """
  multirepo init [--worktrees-root <dir>] [--json]
                              Make the current folder a multirepo (outside any repository or at the top of a
                              workspace repository); new worktrees folders are proposed under --worktrees-root
                              (default: ../<folder>-worktrees)
  multirepo show [--json]     Show the multirepo's repositories, projects, bases, defaults and agent
  multirepo add-repo <alias> <path> [--use-worktrees-root | --keep-worktrees-folder] [--json]
                              Add a repository, reusing its project or registering one, and propose
                              <worktrees-root>/<alias> as its project's worktrees folder (asked unless given)
  multirepo remove-repo <alias> [--json]
                              Remove a repository from the multirepo; its project and worktrees stay
  multirepo defaults <alias>[,<alias>...] | --clear [--json]
                              Set the repositories 'shogun new <id>' covers in the multirepo folder
  multirepo agent <command> [--name <name>] [--no-headroom] [--json]
                              Set the agent 'shogun open --herdr' runs in the multirepo folder
  multirepo worktrees-root <dir> [--json]
                              Set the folder under which worktrees folders are proposed
  multirepo delete [--json]   Remove the declaration; projects, worktrees and branches are left as they are
""".trimIndent(),
        "project" to """
  project add [<path>] [--json]
                              Register the repository as a project of the desktop app, with the app's
                              defaults, unless it already is (default: the current directory)
  project list [--json]       List the registered projects with their repository
  project remove [<path>] [--json]
                              Unregister the repository's project; the repository and its worktrees stay
""".trimIndent(),
        "app" to """
  app [<path>] [--settings]   Open the desktop app on the repository's project, registering it first if needed
                              (--settings: open its settings); outside a repository it just opens the app
""".trimIndent(),
        "gradle" to """
  gradle [--json]             List the Gradle and Kotlin daemons with their memory, state and worktree
  gradle stop [<pid>...] [--json]
                              Stop idle daemons (all of them, or only the given ones); busy ones are kept
""".trimIndent(),
        "stacks" to """
  stacks [--json]             Show the stacks detected in the repository (Gradle, Android, Xcode, Maven,
                              Docker Compose) and the providers they suggest; nothing is changed
""".trimIndent(),
        "maven" to """
  maven [--json]              List the Maven daemons (mvnd) with their memory and worktree
""".trimIndent(),
        "compose" to """
  compose [--json]            List the Docker Compose projects of the worktrees with their status
  compose down [<worktree>] [--volumes] [--json]
                              Take down the projects of removed worktrees (or of the given worktree);
                              volumes are kept unless --volumes is given
                              (repositories with "providers": { "docker-compose": {} } in .shogunai.json)
""".trimIndent(),
        "pool" to """
  pool status [--json]        Show the emulator, iOS simulator and Gradle daemon pools: the limit of the
                              machine's capacity profile, how many are in use and which worktree holds each
  pool trim [--json]          Stop idle Gradle daemons, longest idle first, until they are within the limit
""".trimIndent(),
        "xcode" to """
  xcode [--json]              List Xcode DerivedData folders of the worktrees, orphans and compilation cache
  xcode clean [--json]        Delete orphaned Xcode DerivedData folders on the machine
  xcode trim [--dry-run] [--json]
                              Release DerivedData folders until it is within the quota in Preferences (automatic by default)
""".trimIndent(),
        "hook" to """
  hook worktree-create        Claude Code WorktreeCreate hook: create and provision the worktree, print its path
  hook worktree-remove        Claude Code WorktreeRemove hook: remove the worktree, delete its branch if merged
  hook session-start          Claude Code SessionStart hook: export the worktree's environment and give the session ShogunAi's workflow rules
  hook agent-state            Claude Code UserPromptSubmit, Notification, Stop and SessionEnd hook: record the session's state
  hook herdr                  Herdr plugin hook: provision worktrees on worktree.created, delete merged branches on worktree.removed
""".trimIndent(),
        "hooks" to """
  hooks install|uninstall [--project] [--yes] [--json]
                              Add or remove every ShogunAi hook in Claude Code's user settings
                              (--project: the repository's .claude/settings.json); shows what changes
                              and asks first unless --yes is given
  hooks status [--json]
                              Say whether the hooks are installed, outdated or missing, in the user
                              settings and in the current repository's
""".trimIndent(),
        "guide" to """
  guide [<topic>] [--plain]   Print ShogunAi's workflow rules for agents that apply here (topics: worktrees,
                              parallel, resources, cleanup); the others are listed after them, marked with
                              what they need; --plain prints every rule without looking at the directory
  guide --migrate [--file <path>]
                              Print every rule with its id and an instruction for an agent to propose,
                              without applying, the lines of an AGENTS.md that repeat them
  guide install|uninstall [--agent codex|gemini | --project | --file <path>] [--yes] [--json]
                              Add or remove a block that points to 'shogun guide' in an agent's user-level
                              instructions file, the repository's AGENTS.md or another file; shows what
                              changes and asks first unless --yes is given
  guide status [--file <path>] [--json]
                              Say whether the block is installed, outdated or missing in each known file
""".trimIndent(),
        "simulator" to """
  simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>] [--json]
                              Boot (and wait for), shut down or show the worktree's iOS simulator; the first
                              start creates it, releasing the least recently used idle one if the pool is full
  simulator start [--wait] [--wait-timeout <minutes>]
                              When the pool is full and none can be released, wait in line for one
                              (30 minutes at most by default) instead of failing
                              (repositories with "providers": { "ios-simulator": {} } in .shogunai.json)
  simulator gradle-init install|uninstall [--yes]
                              Make every project's KMP iOS simulator tests use IOS_SIMULATOR_UDID
""".trimIndent(),
        "emulator" to """
  emulator start|stop|status [<path>] [--avd <name>] [--no-window] [--json]
                              Start (and wait for), stop or show the worktree's Android emulator; the first
                              start reserves its console port, releasing the least recently used idle one if
                              the pool is full
  emulator start [--wait] [--wait-timeout <minutes>]
                              When the pool is full and none can be released, wait in line for one
                              (30 minutes at most by default) instead of failing
                              (repositories with "providers": { "emulator": {} } in .shogunai.json)
""".trimIndent(),
        "env" to """
  env [<path>] [--format sh|dotenv|json]
                              Print the worktree's environment from .shogunai.json "env"
                              (sh prints export lines for: eval "$(shogun env)")
""".trimIndent(),
        "help" to """
  help                        Show this help
""".trimIndent(),
        "version" to """
  --version                   Show the version
""".trimIndent(),
    )

    fun forCommand(name: String): String? = sections[name]?.let { section ->
        "Usage: shogun $name [options]\n\n" + section.lines().joinToString("\n") { "  $it".trimEnd() } + "\n"
    }

    val commandNames: Set<String> = sections.keys - setOf("help", "version")

    val USAGE: String = """
Usage: shogun <command> [options]

Commands:
""".trimIndent() + "\n" + sections.values.joinToString("\n") { section -> section.lines().joinToString("\n") { "  $it".trimEnd() } } + "\n\n" + """
Works on the Git repository containing the current directory. Files to copy come from the
repository's .shogunai.json and .worktreeinclude, and from the desktop app's project if there is one.
""".trimIndent() + "\n"
}
