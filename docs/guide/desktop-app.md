# Desktop app

The desktop app manages a catalog of projects and, for each one, its worktrees. Everything it does is also available from the [`shogun` command](cli.md).

## Guided tour

The first launch, with no projects yet, offers **Take the tour** next to "Add project", and Help > **ShogunAi Tour** starts it at any time. If you already had projects when you upgraded, a notice offers it once.

The tour:

1. Offers to install the [`shogun` command](installation.md#the-shogun-command) when it isn't on your `PATH` yet, since agents and Claude Code's hooks use it. You can skip it.
2. Creates a throwaway sample repository in `~/.shogunai/tour`, with a local remote, a [`.shogunai.json`](configuration.md#repository-configuration-shogunaijson) and worktrees in every state the list shows ("Needs you", "New", "Stopped", "Merged", "Inactive", one locked and "Folder missing"), and registers it as the project "ShogunAi tour".
3. Opens the tour's agent in the sample's main worktree, in the terminal, Herdr or Claude Desktop, or copies its launch command. The agent is Claude Code (Codex from the agent menu), started with "Start the ShogunAi tour"; the script it follows is the sample's `AGENTS.md`, and it speaks the app's language. It goes through status, cleanup, provisioning, the command line, hooks, environment and providers one step at a time, waiting for you between steps, and changes nothing outside `~/.shogunai/tour`. Before starting, the agent asks whether you trust the sample's folder: accept it ("Yes, I trust this folder" in Claude Code).

While the sample exists, its project shows a banner to open the agent again or **End tour**, which deletes the project, its worktrees and `~/.shogunai/tour` after asking. Starting the tour again offers to open the existing sample or restart it from scratch.

## Projects

The first time you open the app you'll see an empty project catalog. Click "New project" (Cmd+N on macOS, Ctrl+N elsewhere) and fill in:

- **Name** and **base repository**: the Git repository the worktrees come from.
- **Worktrees folder**: where new worktrees are created.
- **Main branch** (optional): the branch status and merged checks compare against; detected when empty.
- **Local files**: files Git doesn't track that each new worktree needs on this machine (e.g. `local.properties`): paths of files or folders, or patterns such as `*.jks` (relative to the repository, and matching only untracked files). Each entry is copied, or linked when you turn on its Link switch; a copied folder says how many symbolic links inside it are skipped. They're stored only on this machine; to share them with everyone who clones the repository, declare them in [`.shogunai.json`](configuration.md#repository-configuration-shogunaijson) instead. "Move to .shogunai.json" moves the ones inside the repository there for you, linked entries into `link`.
- **Secrets found in this repository**: the `local.properties`, keystores and Firebase files (`google-services.json`, `GoogleService-Info.plist`) the repository has untracked and nothing provisions yet, each with "Link from .shogunai.json", which adds an anchored `link` pattern written when you save; and a warning for each keystore path in those properties files that leaves the repository and won't resolve from a worktree. See [detecting stacks](providers.md#detecting-stacks).
- **Agents**: each with an optional name, its **command** with default arguments and its **Headroom** option (`headroom wrap`, off by default; agents saved by earlier versions keep the setting they had). The first one is the default and the one "Copy command", "Open in Herdr" and "Open in terminal" run in a worktree (`claude` by default). Add more to switch between agents, for example when one runs out of quota; names must be different, and "Make default", the move buttons and "Remove agent" reorder them (a project keeps at least one).
- **IDE on this machine** (optional): the IDE this project's worktrees open in, when it isn't the default one from Preferences (e.g. Android Studio for an Android repository). It's stored only on this machine.

The form also shows, read-only, what the repository declares in `.shogunai.json` and `.worktreeinclude`, and the per-machine settings of the [emulator](providers.md#android-emulator) and [iOS simulator](providers.md#ios-simulator) providers when the repository uses them.

The sidebar lists projects and multirepos alphabetically until you reorder them: drag an entry up or down within its level (the top level, or the projects inside a multirepo folder), or use "Move up" and "Move down" in its context menu, also in the compact sidebar. A project never moves into or out of a multirepo this way. The order is kept across restarts, new projects and multirepos appear at the end of their level, and "Sort alphabetically" in the context menu goes back to the default order for that level.

The catalog can be searched by name and sorted; each project can be opened, edited or deleted. If you move a repository on disk, edit its path in the project: the app moves the worktrees folder with it when it was next to the repository, and re-links the worktrees (`git worktree repair`).

## Multirepos

A [multirepo](multirepo.md) groups the repositories of one product. Drop a folder that isn't a Git repository on the window, or click "Add multirepo" in the sidebar and choose one. Before anything is written, the dialog lists the Git repositories directly inside the folder with a proposed alias each (the folder name without the multirepo's prefix, so `product-app` becomes `app`), marks the ones that are already projects, proposes `<worktreesRoot>/<alias>` as each one's worktrees folder (checked for new projects, unchecked for existing ones, which stay as they are unless you check it) and lists the groups already formed by matching branches. "Add a repository from elsewhere…" adds a repository outside the folder. A folder inside a repository can't hold a multirepo, and the dialog says so. A folder that already has a declaration, for example a multirepo whose folder moved, is reviewed as described below; confirming approves it and registers a project for each declared repository without one, as `shogun multirepo init` does there. The app also forgets the multirepos whose folder no longer exists. Confirming writes the same `.shogunai-multirepo.json` as `shogun multirepo init` and `add-repo`, with every chosen repository as a default.

In the sidebar, a multirepo is a folder named with its display name (its folder's name unless the settings give it one) with its projects inside; it shows the sum of their active worktrees and the agent indicator, collapses with its arrow and remembers it. Selecting it opens the multirepo view:

- **By change** (the default): one block per group, titled with the branch without its prefix (`TASK-12`), with the full branch, its repositories and a combined state, "Merged" only when every worktree is, otherwise the most urgent state among them. Its header offers "Open all in Herdr", "Add repository" (a worktree on the group's branch in a repository not yet in it, for `feature/` and `fix/` branches) and "Clean up group", enabled only when every worktree is clean and merged with no agent running; its tooltip says which repository holds it back. "Ungrouped" lists the remaining worktrees. Every row starts with its repository's alias.
- **By state**: the multirepo's worktrees grouped by state, still with their aliases.

The header has "New change", which creates the group in the checked repositories (the defaults first) as `shogun new <id> --repos` does and reports each repository's result, "Open multirepo agent in Herdr" and the multirepo settings. In every other view, a worktree of a group shows the tag `TASK-12 · +api` before its folder; clicking it opens the multirepo view on that group. When grouped by project, a project's header reads `product / app`.

When the multirepo folder is a Git repository it is the multirepo's workspace repository: its worktrees are listed under the multirepo (not as a loose project, and ShogunAi registers its project once for you), "New change" lists it as `workspace` and "Add project" or dropping the folder explains that it already belongs to the multirepo instead of adding it. The multirepo view shows the multirepo agent, the one running in the folder, with its state next to "Open multirepo agent in Herdr", and the multirepo's sidebar row reflects its activity; an agent running in a member repository shows under that repository's worktree instead.

The settings have a "Workspace repository" section when the folder is a Git repository: it is marked as detected until you declare it, with a checkbox to declare it, a field for its alias, a "Turn off" button to stop treating the folder as a repository of the multirepo, and it appears among the default repositories. They also set the multirepo's name, add, rename and remove repositories, choose the default repositories, set the worktrees folder proposed to the repositories added later, edit the multirepo agent with the same fields as a project's agent, and delete the multirepo, which removes only the declaration. Changes made with `shogun multirepo` show up at the app's next refresh.

ShogunAi only uses a multirepo approved on this machine (see [Approving a multirepo](multirepo.md#approving-a-multirepo)). When the folder you add already has a declaration, for example a cloned workspace repository, the dialog also lists its repositories with the path each one resolves to, its worktrees folder and its workspace repository, marking any path outside the folder's parent; confirming approves it. A multirepo whose declaration changed outside ShogunAi, or that was registered by an earlier version, stays in the sidebar with a warning mark and its repositories show as loose projects. Its view shows the same review with "Trust" instead of its groups, and "New change", "Open multirepo agent in Herdr", "Clean up" and saving its settings are disabled until you trust it. The multirepo agent you edit in the settings is kept on this machine, never in the declaration; an `agent` left in the declaration by an earlier version is ignored, and the settings say so.

## Creating a worktree

Open a project and, under "New worktree", choose:

- **New branch**: enter the task id (e.g. `TASK-123`) and the branch type, `feature` or `fix`. The app creates `feature/TASK-123` at the commit chosen in **Start from**: the project's main branch (`origin/develop` when it exists) by default, the base repository's current branch, or any local or `origin/` branch; when no main branch resolves, the default is the current branch. To start from a tag or a commit, use [`shogun new --from`](cli.md#new). Spaces in the id become `-`, and ids Git wouldn't accept as a branch name are rejected.
- **Existing branch**: pick a branch from the list, grouped by prefix. Branches that already have a worktree can't be picked. Branches that exist only on `origin` are listed too, marked as remote; picking one creates a local branch with that name tracking it.

When the repository has an `origin` remote, **Fetch from origin first** fetches before creating, in either mode, so the chosen branch is current. It's off by default and each project remembers its last value on this machine. The fetch never waits for credentials: if it fails, the dialog shows Git's error with "Try again" and "Create without fetching", and nothing is created until you choose one.

"Create worktree" (or Cmd+N / Ctrl+N) creates it and copies the files the repository and the project declare. If a copy fails, the worktree is removed again so nothing is left half-made.

## The worktree list

Each row is two lines with a fixed place for every piece of information, so rows line up and can be compared at a glance:

- A **state badge**: a colored circle with an icon that is different for each [state](#states), so it can be told without color. Hovering it shows the state's name.
- The **execution** column: the agent on the first line (its name and, with Herdr running, its state: working, blocked, done or idle) and, on the second, whether the worktree has the [desktop turn](cli.md#desktop) or waits for it (with its place in line), the emulator or simulator while it boots or runs, and its [app running in the background](headless-app.md). With several agents, or several things on the second line, it shows the first one with a "+N" and the tooltip lists them all. In a narrow list it collapses to icons that keep their colors; hover them for the labels.
- The **name**: the branch on the first line; on the second, the folder when it differs from the branch's last segment, the **origin** (ShogunAi, Claude Code, Herdr or another location) and, in views that span several projects and aren't grouped by project, the project.
- The **Git** column: uncommitted changes on the first line and, on the second, the commits ahead of and behind the main branch, or "merged". A dash means the status couldn't be read.
- The **time** column: when the worktree was last active, saying what it measures ("commit 3 h ago" when its branch has commits of its own, "created 2 d ago" otherwise), and the "main" and "locked" marks below.

The detail shows more:

- Its **status**: uncommitted changes, ahead/behind its upstream and the main branch, the last commit date, the creation date, and "merged into <main branch>" or "not started" when its branch never had commits of its own. "Folder missing" when its folder was deleted by hand.
- Its **provisioned files**: whether the files and links declared for new worktrees match the base repository's, listing the missing, differing and not-linked ones. For a worktree made by another tool, or whose copies are stale, **Provision** copies them, reserves its ports and writes its environment file, as `shogun provision` does.
- Its **environment**: the ports and every variable from `.shogunai.json` `"env"` with its value, in the order `shogun env` prints them. **Copy as .env** puts them on the clipboard in dotenv format. Values aren't masked, since they come from templates in the repository.
- Its **size** and the **ports** reserved for it (see [per-worktree environment](../agents.md#per-worktree-environment)).
- The emulator and simulator lines with Start and Stop, for repositories that use those [providers](providers.md).

The list refreshes by itself: changes made from a terminal or by an agent show up within a few seconds, and edits that aren't staged yet show up when the window regains focus. Statuses and sizes stay on screen while they are read again. Sizes are measured when a worktree first appears; "Reload" measures them all again, for example after a build.

"Active" and "All worktrees" span every project; their project filter narrows the current view to one project without leaving it. Selecting a project in the sidebar opens that project's own view instead, which has no project filter.

Every view has a sort selector next to the grouping one: **Name** (the default; branches in natural order, so `TASK-9` comes before `TASK-10`) or **Last activity** (most recent first). The main worktree always comes first in its group. Each view remembers its own choice.

## States

Each worktree is in the first state that applies. Grouped by state, each group's header shows a short hint next to its title, and hovering the title shows its full criterion. "Active" shows the first four groups; "All worktrees" shows all of them.

| Group | Hint | Criterion |
|---|---|---|
| Needs you | agent waiting, uncommitted or unpushed | An agent is blocked or done, or no agent is working and there are uncommitted changes or commits not pushed to its upstream (or to the main branch when it has none). |
| Agent working | agent running | An AI agent is working in the worktree, or runs without a known state. An agent that is idle doesn't decide the state. |
| New | no commits yet | No commits of its own, no uncommitted changes and no agent running. |
| Stopped | pushed, waiting | Commits of its own, all pushed, nothing uncommitted, not merged and no agent working. |
| Merged | in main branch | No commits of its own ahead of the main branch, after having had some. Can be cleaned up. A branch that never had commits and has no upstream reads as "new" instead and is never offered for cleanup. |
| Inactive | no activity for 3+ days | Clean, no agent running and no activity in the last 3 days (see below). |
| Folder missing | folder deleted | Git still lists it but its folder no longer exists. Can be cleaned up. |
| Other | main or not read yet | The main worktree when it's clean, or a worktree whose status isn't read yet. |

A worktree's activity is the date of its last commit when its branch has commits of its own, and the date it was created otherwise, so a new worktree isn't dated by the old commit it started from. A worktree without commits of its own is never inactive. The inactivity threshold is 3 days by default; change it in [Preferences](#preferences) (from 1 to 90 days) and every view updates at once.

## Working in a worktree

- **Copy command** copies `cd "<worktree>" && <agent command>` to paste in any terminal.
- **Open in Herdr** opens the worktree in [Herdr](../agents.md#herdr) with its environment and runs the agent command there; if the worktree already has an agent in Herdr, it focuses that agent's pane instead.
- **Open in terminal** opens a new window or tab of your terminal in the worktree's folder, with the worktree's environment variables set, and runs the agent command; the shell stays open when the agent exits. Terminal and iTerm2 (macOS), Windows Terminal (Windows), and tmux (3.0 or later) and Zellij (macOS and Linux) are supported; tmux and Zellij open a new window or tab in the session you used last, so they're only offered while one is running. The first time, the app asks which terminal to use. On macOS, Terminal and iTerm2 ask your permission the first time ShogunAi controls them; if you decline, allow it in System Settings > Privacy & Security > Automation.
- **Open in IDE** opens the worktree's folder in VS Code, IntelliJ IDEA or Android Studio, or its Xcode workspace in Xcode (macOS only), whichever is the default IDE or the one chosen for the project. For Xcode it looks for a `.xcworkspace`, then an `.xcodeproj`, in the worktree and its subfolders (e.g. `iosApp/`), and opens the folder if there is none.
- **Open folder** shows the worktree's folder in Finder, Explorer or the file manager.

With several agents, the worktree's detail shows an **Agent** field and the three launch actions name the agent they use ("Copy command · Codex"). Pick another agent in the field, or with the arrow next to each action, the worktree's context menu or the command palette; launching with another agent makes it the worktree's agent, and its next launches continue with it. The choice is remembered on this machine only, and choosing the default agent or removing the chosen one from the project goes back to the default. Running agents are recognized for every agent command of the project.

"Copy command" stays the main button; the other actions sit next to it, in the worktree's context menu and in the command palette (for the selected worktree). They are offered only for tools found on this machine, and "Open in IDE" and "Open folder" disappear when the worktree's folder is gone.

## Removing and cleaning up

- **Remove** removes one worktree, optionally deleting its local branch, which is deleted only if it's merged into the project's main branch. If it isn't (for example after a squash merge), the worktree is still removed and a notice says the branch was kept, with a **Delete branch anyway** action that asks for confirmation first. The main worktree can't be removed.
- **Clean up**, next to the "Worktrees" title, lists the worktrees that are merged or whose folder is gone and removes them in one go, optionally deleting their branches. Depending on the repository's providers it also offers to stop their idle Gradle daemons and their emulators, and to delete their iOS simulators and orphaned Xcode DerivedData.

Removing a worktree that's open in Herdr also closes its Herdr workspace; the dialogs say so beforehand. Removing or cleaning up a worktree also stops its app running in the background and releases its desktop turn; if the app can't be stopped, the worktree is still removed and a notice says so until you dismiss it.

## The desktop turn

Agents that check something with computer use take turns to drive the desktop ([`shogun desktop`](cli.md#desktop)). While a worktree has the turn or any waits for it, the sidebar's **Machine** section shows a **Desktop** entry with the holder and how many wait, also when no project declares a provider. Its view shows the holder, since when and until when, and the worktrees waiting in order; activate one to go to its worktree. The worktree's detail shows its turn or its place in line too. **Release** frees the desktop whoever holds it, after asking, and the first worktree waiting gets it; use it when an agent is stuck with the turn or you need the desktop yourself.

## Provider sections

For repositories that declare them in `.shogunai.json`, the worktree screen adds a "Gradle daemons" section and an "Xcode DerivedData" section. The DerivedData folders are listed right away and show "Calculating…" for their sizes until they are measured in the background, except those whose size is remembered from a previous run. Unless "No quota" is chosen for DerivedData in Preferences > Resources (the default is an automatic quota computed from the free disk space), the view also shows the usage against the quota and releases the least recently used folders of worktrees that are not in use when it is exceeded. See [Providers](providers.md).

## Preferences

Preferences (Cmd+Comma on macOS, Ctrl+Comma elsewhere) holds the theme, the language, the **inactivity threshold** in days, the **Tools** section, where you choose the terminal and the default IDE among the ones ShogunAi found on this machine (if none is found, the section says so and the matching actions aren't offered), and **Check for updates automatically**, on by default. See [Updates](#updates).

## Menu bar and system tray icon

While ShogunAi runs, an icon in the macOS menu bar (or the system tray on Windows and Linux desktops that have one) opens a menu with:

- **Show ShogunAi** and **New worktree…**, which bring the window forward.
- **Recent projects**: up to 5, the ones whose view you opened most recently.
- **Recent worktrees**: up to 8, the ones you selected, created or launched most recently. Each one is a submenu with **Show in ShogunAi**, **Copy launch command** and, as available, **Open in <terminal>**, **Open in <IDE>** and **Open folder**, which behave as in the worktree's detail. The terminal, IDE and folder actions run without bringing the window forward; if one fails, the window comes forward and shows the error.
- **Preferences** and **Quit**.

A worktree's label reads `<branch> — <project>`, prefixed with `●` when an agent is working in it and `!` when it needs you (uncommitted changes, unpushed commits, or an agent that is blocked or done). Recents are remembered between restarts, and removed projects and worktrees disappear from them.

Preferences has two options for it, shown only where the system has a tray:

- **Show icon in the menu bar** (system tray on Windows and Linux), on by default.
- **Keep running when the window is closed**, off by default and available only while the icon is shown. With it on, closing the window hides it and the app keeps running in the icon; **Show ShogunAi** brings the window back and **Quit** (or Cmd/Ctrl+Q) quits. Unsaved project settings still ask before the window closes. With it off, closing the window quits, as before.

## Theme and language

The top bar has a theme selector (light, dark, or automatic to follow the system) and a language selector (English or Español). Both choices are remembered between restarts.

## Updates

Every time it starts, and then once a day while it stays open, the app asks GitHub for the latest published release. If it is newer than the version you run, a short notice appears at the top of the window ("ShogunAi 1.3.0 is out! You're on 1.2.0.") with a **What's new** link to the release notes. The notice never interrupts your work, and it reminds you how to update for the way ShogunAi was installed:

| Installed with | The notice |
|---|---|
| Homebrew | `brew upgrade --cask shogunai`, with a **Copy** button |
| Scoop | `scoop update shogunai`, with a **Copy** button |
| winget | `winget upgrade LuxionServer.ShogunAi`, with a **Copy** button |
| The `.dmg`, `.msi` or `.deb` | A **Download** button for the new installer (for the `.deb`, also the `sudo apt install ./<file>` command to run afterwards) |
| Anything else | A link to the release page |

The app never downloads, installs or runs anything by itself. **Skip this version** hides the notice until a newer version comes out; closing it hides it until the next start. A release is sometimes published a few minutes before the Homebrew, Scoop or winget package follows, so the command may need a moment.

**Check for Updates…** in the Help menu checks right away, ignoring the schedule and skipped versions, and tells you whether ShogunAi is up to date, which version is available or why the check failed. Development builds don't check on their own.

The only request the app makes on its own is an anonymous `GET` to `https://api.github.com/repos/LuxionServer/ShogunAi/releases/latest`, which carries the app version in its `User-Agent` header and nothing else. Turn **Check for updates automatically** off in Preferences and the app contacts GitHub only when you choose **Check for Updates…**.

## Diagnostics

The app keeps a local diagnostics log in `~/.shogunai/logs/diagnostics.log` (rotated at 1 MB, three files at most). It records errors nothing handled, errors shown in the app and freezes of the interface (when it stops responding for 2 seconds or more, with how long it lasted and what it was doing). Before anything is written, absolute paths, your user name, the names of your saved projects and the branches of their worktrees are replaced by placeholders such as `<path>` or `<project>`.

If the Preferences or About window hits an unexpected error, only that window closes: the error is recorded in the log and the main window shows a message, so the rest of the app and your work keep running.

**Copy Diagnostic Report** in the Help menu copies to the clipboard the app version, your operating system and architecture, the Java runtime and the most recent entries of the log, ready to paste into a bug report. Nothing is ever sent anywhere: the log stays on your machine and the report only reaches the clipboard when you ask for it.
