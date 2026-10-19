# Desktop app

The desktop app manages a catalog of projects and, for each one, its worktrees. Everything it does is also available from the [`shogun` command](cli.md).

## Projects

The first time you open the app you'll see an empty project catalog. Click "New project" (Cmd+N on macOS, Ctrl+N elsewhere) and fill in:

- **Name** and **base repository**: the Git repository the worktrees come from.
- **Worktrees folder**: where new worktrees are created.
- **Main branch** (optional): the branch status and merged checks compare against; detected when empty.
- **Local files**: files Git doesn't track that each new worktree needs on this machine (e.g. `local.properties`). They're stored only on this machine; to share them with everyone who clones the repository, declare them in [`.shogunai.json`](configuration.md#repository-configuration-shogunaijson) instead. "Move to .shogunai.json" moves the ones inside the repository there for you.
- **Agent command** and **Headroom**: the command "Copy command", "Open in Herdr" and "Open in terminal" run in a worktree (`claude` by default).
- **IDE on this machine** (optional): the IDE this project's worktrees open in, when it isn't the default one from Preferences (e.g. Android Studio for an Android repository). It's stored only on this machine.

The form also shows, read-only, what the repository declares in `.shogunai.json` and `.worktreeinclude`, and the per-machine settings of the [emulator](providers.md#android-emulator) and [iOS simulator](providers.md#ios-simulator) providers when the repository uses them.

The catalog can be searched by name and sorted; each project can be opened, edited or deleted. If you move a repository on disk, edit its path in the project: the app moves the worktrees folder with it when it was next to the repository, and re-links the worktrees (`git worktree repair`).

## Creating a worktree

Open a project and, under "New worktree", choose:

- **New branch**: enter the task id (e.g. `TASK-123`) and the branch type, `feature` or `fix`. The app creates `feature/TASK-123` at the commit chosen in **Start from**: the project's main branch (`origin/develop` when it exists) by default, the base repository's current branch, or any local or `origin/` branch; when no main branch resolves, the default is the current branch. To start from a tag or a commit, use [`shogun new --from`](cli.md#new). Spaces in the id become `-`, and ids Git wouldn't accept as a branch name are rejected.
- **Existing branch**: pick a branch from the list, grouped by prefix. Branches that already have a worktree can't be picked. Branches that exist only on `origin` are listed too, marked as remote; picking one creates a local branch with that name tracking it.

When the repository has an `origin` remote, **Fetch from origin first** fetches before creating, in either mode, so the chosen branch is current. It's off by default and each project remembers its last value on this machine. The fetch never waits for credentials: if it fails, the dialog shows Git's error with "Try again" and "Create without fetching", and nothing is created until you choose one.

"Create worktree" (or Cmd+N / Ctrl+N) creates it and copies the files the repository and the project declare. If a copy fails, the worktree is removed again so nothing is left half-made.

## The worktree list

Each row shows a worktree with:

- Its **branch**, a "locked" label when an agent session holds it, its **origin** (ShogunAi, Claude Code, Herdr or another location) and the **agents running** in it. With Herdr running, each agent also shows its state (working, blocked, done or idle).
- Its **status**: uncommitted changes, ahead/behind its upstream and the main branch, the last commit date, "merged into <main branch>", or "not started" when its branch never had commits of its own. "Folder missing" when its folder was deleted by hand.
- Its **provisioned files**: whether the files and links declared for new worktrees match the base repository's, listing the missing, differing and not-linked ones. For a worktree made by another tool, or whose copies are stale, **Provision** copies them, reserves its ports and writes its environment file, as `shogun provision` does.
- Its **environment**: the ports and every variable from `.shogunai.json` `"env"` with its value, in the order `shogun env` prints them. **Copy as .env** puts them on the clipboard in dotenv format. Values aren't masked, since they come from templates in the repository.
- Its **size**, the **ports** reserved for it (see [per-worktree environment](../agents.md#per-worktree-environment)) and an "inactive" label when it has no uncommitted changes, no agent running and no commit in the last 14 days.
- The emulator and simulator lines with Start and Stop, for repositories that use those [providers](providers.md).

The list refreshes by itself: changes made from a terminal or by an agent show up within a few seconds, and edits that aren't staged yet show up when the window regains focus. Statuses and sizes stay on screen while they are read again. Sizes are measured when a worktree first appears; "Reload" measures them all again, for example after a build.

"Active" and "All worktrees" span every project; their project filter narrows the current view to one project without leaving it. Selecting a project in the sidebar opens that project's own view instead, which has no project filter.

## States

Grouped by state, each group's header shows a short hint next to its title, and hovering the title shows its full criterion. "Active" shows the first three groups; "All worktrees" shows all of them.

| Group | Hint | Criterion |
|---|---|---|
| Agent working | agent running | An AI agent is running in the worktree. |
| Needs you | agent waiting, uncommitted or unpushed | An agent blocked or done in Herdr, uncommitted changes, or commits not pushed to its upstream (or to the main branch when it has none). |
| In progress | pushed, not merged | Clean, pushed, not merged and with a commit in the last 14 days, or a branch that never had commits of its own ("not started"). |
| Merged | in main branch | No commits of its own ahead of the main branch, after having had some. Can be cleaned up. A branch that never had commits and has no upstream reads as "not started" instead and is never offered for cleanup. |
| Inactive | idle for 14+ days | Clean, no agent and no commit in the last 14 days. |
| Folder missing | folder deleted | Git still lists it but its folder no longer exists. Can be cleaned up. |
| Other | main or not read yet | The main worktree when it's clean, or a worktree whose status isn't read yet. |

## Working in a worktree

- **Copy command** copies `cd "<worktree>" && <agent command>` to paste in any terminal.
- **Open in Herdr** opens the worktree in [Herdr](../agents.md#herdr) with its environment and runs the agent command there; if the worktree already has an agent in Herdr, it focuses that agent's pane instead.
- **Open in terminal** opens a new window or tab of your terminal in the worktree's folder, with the worktree's environment variables set, and runs the agent command; the shell stays open when the agent exits. Terminal and iTerm2 (macOS), Windows Terminal (Windows), and tmux (3.0 or later) and Zellij (macOS and Linux) are supported; tmux and Zellij open a new window or tab in the session you used last, so they're only offered while one is running. The first time, the app asks which terminal to use. On macOS, Terminal and iTerm2 ask your permission the first time ShogunAi controls them; if you decline, allow it in System Settings > Privacy & Security > Automation.
- **Open in IDE** opens the worktree's folder in VS Code, IntelliJ IDEA or Android Studio, whichever is the default IDE or the one chosen for the project.
- **Open folder** shows the worktree's folder in Finder, Explorer or the file manager.

"Copy command" stays the main button; the other actions sit next to it, in the worktree's context menu and in the command palette (for the selected worktree). They are offered only for tools found on this machine, and "Open in IDE" and "Open folder" disappear when the worktree's folder is gone.

## Removing and cleaning up

- **Remove** removes one worktree, optionally deleting its local branch. The main worktree can't be removed.
- **Clean up**, next to the "Worktrees" title, lists the worktrees that are merged or whose folder is gone and removes them in one go, optionally deleting their branches. Depending on the repository's providers it also offers to stop their idle Gradle daemons and their emulators, and to delete their iOS simulators and orphaned Xcode DerivedData.

Removing a worktree that's open in Herdr also closes its Herdr workspace; the dialogs say so beforehand.

## Provider sections

For repositories that declare them in `.shogunai.json`, the worktree screen adds a "Gradle daemons" section and an "Xcode DerivedData" section. See [Providers](providers.md).

## Preferences

Preferences (Cmd+Comma on macOS, Ctrl+Comma elsewhere) holds the theme, the language and the **Tools** section, where you choose the terminal and the default IDE among the ones ShogunAi found on this machine. If none is found, the section says so and the matching actions aren't offered.

## Theme and language

The top bar has a theme selector (light, dark, or automatic to follow the system) and a language selector (English or Español). Both choices are remembered between restarts.
