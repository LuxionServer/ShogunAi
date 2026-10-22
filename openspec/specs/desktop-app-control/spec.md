# desktop-app-control Specification

## Purpose
Lets the command line open the desktop app on a project or its settings, whether the app is running or not, without disturbing the state of an app that is already open.

## Requirements

### Requirement: Open the app on a project from the command line
`shogun app [<path>]` SHALL open the desktop app showing the worktrees of the project of the repository containing `<path>` (the current directory by default), registering the repository first with the same defaults as `shogun project add` when it has no project. It SHALL return as soon as the request is delivered or the app is launched, without waiting for the app to close.

#### Scenario: Project already registered
- **WHEN** the user runs `shogun app` inside a registered repository
- **THEN** the app comes to the front showing that project's worktrees

#### Scenario: Repository not registered yet
- **WHEN** the user runs `shogun app` inside a repository with no project
- **THEN** the repository is registered, the app opens on the new project and the command says it was added

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun app` outside a Git repository without a path
- **THEN** the app is opened or brought to the front on whatever it was showing, and the command exits with code 0

#### Scenario: Path that isn't a repository
- **WHEN** the user runs `shogun app ~/projects/plain-folder` and it isn't inside a Git repository
- **THEN** nothing is registered, the app isn't asked to open anything, and the command exits with code 1

### Requirement: Open a project's settings from the command line
`shogun app --settings [<path>]` SHALL open the app on the settings of the project of that repository, instead of its worktrees. It SHALL require a repository, registering it first when needed.

#### Scenario: Settings of a registered project
- **WHEN** the user runs `shogun app --settings` inside a registered repository
- **THEN** the app shows that project's settings form with its current values

#### Scenario: Settings without a repository
- **WHEN** the user runs `shogun app --settings` outside a Git repository
- **THEN** the command says that `--settings` needs a repository and exits with code 2

### Requirement: Reach a running app
When the desktop app is running, `shogun app` SHALL deliver the order to that instance instead of starting another one, and the app SHALL serve it within a second or two. The app SHALL be considered running while it holds its lock in `~/.shogunai/`; a stale lock left by a process that died SHALL NOT count.

#### Scenario: App already open
- **WHEN** the app is running and the user runs `shogun app` inside a registered repository
- **THEN** no second app process starts and the running app shows the project

#### Scenario: App crashed earlier
- **WHEN** the previous app process was killed and left its files behind
- **THEN** the next `shogun app` treats the app as not running and launches it

### Requirement: Launch the app when it isn't running
When the desktop app isn't running, `shogun app` SHALL launch it, and the launched app SHALL serve the pending request once it has started. If the desktop app can't be found, because `shogun` runs from an install without it, the command SHALL say so, leave no request behind and exit with code 1.

#### Scenario: Cold start
- **WHEN** the app is closed and the user runs `shogun app --settings` inside a registered repository
- **THEN** the app starts and shows that project's settings

#### Scenario: Install without the desktop app
- **WHEN** `shogun` runs from the standalone zip, with no desktop app installed, and the user runs `shogun app`
- **THEN** the command says the desktop app isn't available from this install and exits with code 1

#### Scenario: Request that was never served
- **WHEN** a request was left more than a minute ago and the app then starts by itself
- **THEN** the app discards the request instead of navigating to it

### Requirement: A running app keeps its state
Serving a request SHALL NOT reset the app: it SHALL keep the window's size and position, the sidebar state and the open view, and SHALL go through the same guard as navigating from the sidebar, so a form with unsaved changes asks whether to leave instead of being discarded. Showing a window that is minimized or hidden in the tray SHALL restore it.

#### Scenario: Unsaved settings
- **WHEN** the app shows a project's settings with unsaved edits and a request to open another project arrives
- **THEN** the app brings its window forward and asks whether to leave without saving; the edits are still there if the user keeps editing

#### Scenario: Window hidden in the tray
- **WHEN** the window is hidden because it was closed to the tray and a request arrives
- **THEN** the window is shown again at its previous size and position

#### Scenario: Already showing the target
- **WHEN** the app already shows the requested project's settings and the same request arrives
- **THEN** the view is not reloaded and the edits in it are kept

#### Scenario: Project added from the command line while the app is open
- **WHEN** the CLI registers a project while the app is running
- **THEN** the project appears in the sidebar without restarting the app and the selection of the other projects is unchanged

### Requirement: Requests are safe to receive
The app SHALL ignore, and delete, a request that is malformed, of an unknown version or action, or that names a project that no longer exists, without showing an error or changing its view. Requests SHALL only be accepted from the user's own `~/.shogunai/` folder, and SHALL NOT be able to run commands.

#### Scenario: Garbage in the request folder
- **WHEN** a file that isn't a valid request is left in the request folder
- **THEN** the app deletes it and carries on as before

#### Scenario: Project removed before the app serves the request
- **WHEN** the request names a project that was removed in the meantime
- **THEN** the app ignores the request and keeps its current view

### Requirement: Older versions keep working
The files this feature adds to `~/.shogunai/` SHALL NOT be read by versions without it, and the existing files SHALL keep their format, so an app or CLI from before this change keeps working on the same folder.

#### Scenario: Older app with a newer CLI folder
- **WHEN** an older app starts on a `~/.shogunai/` folder that contains the lock and request folder
- **THEN** it starts normally and its projects, settings and worktrees are unchanged
