# Proposal

## Why

To configure a repository in the desktop app, the user has to open the app, find "Add project", browse to the folder and fill in the form. People who live in the terminal (and agents working in a worktree) already stand inside the repository, yet the CLI can't register it nor take them to its settings. `shogun` should be able to register repositories and open the app straight on a project.

## What Changes

- `shogun project add [<path>] [--json]` registers the repository containing `<path>` (default: the current directory) in `~/.shogunai/projects.json` with the same defaults as the app's "Add project" form. A repository that is already registered is left untouched and reported as such.
- `shogun project list [--json]` prints the registered projects, and `shogun project remove [<path>] [--json]` unregisters one, never touching its repository or worktrees (as in the app).
- `shogun app [<path>] [--settings]` opens the desktop app on the project of that repository, registering it first when needed. `--settings` opens the project's settings instead of its worktrees. Without `<path>` it uses the repository of the current directory, and outside a repository it just opens or focuses the app.
- The order reaches a running app through a small request channel in `~/.shogunai/` (a lock file held by the running app and a spool folder of request files), not through a port, a URL scheme or a command-line argument. If the app isn't running, the CLI leaves the request and launches it; the app serves the pending request on start.
- A running app serves the request through the navigation it already has for the sidebar, so its window, current view and unsaved edits are kept: a form with unsaved changes asks the usual "leave without saving?" instead of being discarded.
- Backwards compatible: `projects.json` keeps its current schema (the CLI writes the same `Project` entries the app writes), new files are only added next to it, and an older version that reads those files keeps working.
- Platforms: macOS, Windows and Linux with the same mechanism; only the way the app is launched differs. Installs without the desktop app (the standalone zip) get a clear error from `shogun app` and keep `project` commands.

## Capabilities

### New Capabilities

- `cli-project-registry`: registering, listing and removing projects from the command line, with defaults shared with the app and idempotent behavior for repositories already registered.
- `desktop-app-control`: the `shogun app` command and the request channel by which the CLI makes the desktop app open a project or its settings, launching the app when it isn't running and keeping a running app's state intact.

### Modified Capabilities

- `command-line-tool`: the repository/project resolution requirement gains the commands that work on the project catalog and on the app (`project`, `app`), including their exit codes and `--json` output.

## Impact

- `core`: a use case that builds a project's defaults from a repository (extracted from what `ProjectConfigViewModel.chooseFolder` computes today, so the form and the CLI share it), `RegisterProjectUseCase` / `UnregisterProjectUseCase`, the request model and its ports (`AppRequestChannel`, `AppProcessLauncher`), and a file-based adapter in `infrastructure`.
- `cli`: parsing and execution of `project add|list|remove` and `app` in `CliCommand.kt` / `ShogunCli.kt`; `Main.kt` wires the real adapters.
- `desktopApp`: holds the app lock, watches the spool folder and routes requests to `ShellViewModel` (`navigate`, `editProject`, `refresh`) and to `showMainWindow`; `ProjectConfigViewModel` uses the shared defaults. No new visible text, so no new translations.
- Docs (English and Spanish): `docs/guide/cli`, `docs/architecture/cli`, and the roadmap if it lists this.
- Files on disk: `~/.shogunai/app.lock` and `~/.shogunai/app-requests/` are new; `projects.json` is unchanged in shape.
- Relationship with `multirepo-groups`: independent. `shogun app <multirepo folder>` is left as a follow-up; the request has an `action` field so that change can add one without altering this protocol.
