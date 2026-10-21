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
  status [--json]             List the repository's worktrees with their state
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
""".trimIndent(),
        "repair" to """
  repair [--json]             Re-link the worktrees after the repository or the worktrees folder moved
""".trimIndent(),
        "clean" to """
  clean [--yes] [--delete-branches] [--stop-gradle-daemons] [--stop-emulators] [--delete-simulators]
        [--delete-derived-data] [--json]
                              List merged or missing worktrees; remove them with --yes
                              (--stop-gradle-daemons / --stop-emulators: also stop their idle
                              Gradle daemons / their emulators; --delete-simulators: also delete
                              their iOS simulators and orphaned ShogunAi simulators;
                              --delete-derived-data: also delete orphaned Xcode DerivedData;
                              --json: print one JSON object instead of text)
""".trimIndent(),
        "gradle" to """
  gradle [--json]             List the Gradle and Kotlin daemons with their memory, state and worktree
  gradle stop [<pid>...] [--json]
                              Stop idle daemons (all of them, or only the given ones); busy ones are kept
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
                              Release DerivedData folders until it is within the quota in Preferences
""".trimIndent(),
        "hook" to """
  hook worktree-create        Claude Code WorktreeCreate hook: create and provision the worktree, print its path
  hook worktree-remove        Claude Code WorktreeRemove hook: remove the worktree, delete its branch if merged
  hook session-start          Claude Code SessionStart hook: export the worktree's environment to the session
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
        "simulator" to """
  simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>] [--json]
                              Boot (and wait for), shut down or show the worktree's iOS simulator; the first
                              start creates it, releasing the least recently used idle one if the pool is full
                              (repositories with "providers": { "ios-simulator": {} } in .shogunai.json)
  simulator gradle-init install|uninstall [--yes]
                              Make every project's KMP iOS simulator tests use IOS_SIMULATOR_UDID
""".trimIndent(),
        "emulator" to """
  emulator start|stop|status [<path>] [--avd <name>] [--no-window] [--json]
                              Start (and wait for), stop or show the worktree's Android emulator; the first
                              start reserves its console port, releasing the least recently used idle one if
                              the pool is full
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
