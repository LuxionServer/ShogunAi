# Design

## Context

- The CLI and the app are the same binary: `desktopApp`'s `main(args)` runs `runShogun` when it gets arguments and opens the window otherwise (`docs/architecture/cli.md`, "How it's shipped"). A CLI process is therefore a different process from a running app and has to reach it from outside.
- `~/.shogunai/projects.json` is written by both through `JsonProjectRepository`, serialized by `LocalFileLock` (a `.lock` file next to the target) and written atomically. The app re-reads it on `View > Reload` and when the window regains focus.
- The app already has the navigation a request needs: `ShellViewModel.navigate`, `selectProject`, `editProject`, `addProject(folder)` and `refresh()`, all going through `requestLeave`, which defers to a "leave without saving?" confirmation when a form has unsaved changes. `Main.kt` already has `showMainWindow` (un-minimizes, re-shows a window hidden in the tray and requests foreground).
- The values "Add project" proposes live today in `ProjectConfigViewModel.chooseFolder` (name from the folder, worktrees folder `<parent>/<name>-worktrees`, main branch detected) and nothing else sets them.
- Platforms: the desktop app ships on macOS (Cask), Windows (`.msi`) and Linux (`.deb`); the standalone zip has no app.

## Goals / Non-Goals

**Goals:**
- One delivery mechanism that works the same on the three platforms, needs no registration with the OS and can be tested with fakes.
- A running app never loses state because of a CLI call.
- No change to the shape of any existing file; an older app or CLI keeps working on the same `~/.shogunai/`.

**Non-Goals:**
- Opening a multirepo folder (`multirepo-groups` follow-up), opening a specific worktree, or any other deep link.
- Making the request channel a general remote-control API: it carries "show this project / its settings" only.
- A single-instance guard for the app in general. Two app processes keep working as today; only the one holding the lock serves requests.
- Writing `.shogunai.json` or anything else inside the repository when registering.

## Decisions

### 1. Delivery: a lock file plus a spool folder in `~/.shogunai/`

- **Liveness**: the running app holds an exclusive lock on `~/.shogunai/app.lock` (`FileChannel.tryLock`) for its whole life. The CLI probes it with `tryLock`: if it gets the lock the app isn't running (and releases it at once); if not, it is. The operating system drops the lock when the process dies, so a crash leaves no stale "running" state, which a pid or socket file would.
- **Request**: the CLI writes `~/.shogunai/app-requests/<timestamp>-<uuid>.json` through a temporary file and an atomic move, so the app never reads half a file. Content: `{"version":1,"createdAt":<epoch ms>,"action":"open-project","projectId":"…","settings":false}`, or `"action":"open-app"` with no project.
- **Serving**: the app, once it owns the lock, drains the folder at start and then polls it about every 500 ms (a `LaunchedEffect` loop; `WatchService` is slow and unreliable on macOS). It reads each file oldest first, deletes it, and dispatches on `action`. Requests older than 60 seconds, malformed ones and unknown versions or actions are deleted without effect.
- **Cold start**: the CLI writes the request first and then launches the app, so a freshly started app finds it when it drains the folder; warm and cold start are the same path.

Alternatives considered:
- *Unix domain socket / local TCP port*: gives an immediate ack, but needs stale-socket cleanup, a port or a path with permissions to protect, and a connection protocol that has to be faked in tests; on cold start the CLI would still have to wait for the app to open the socket, or fall back to a file anyway.
- *`shogunai://` URL scheme*: needs registration per platform (Info.plist, the `.msi`, a `.desktop` file) and `Desktop.setOpenURIHandler` only works on macOS, so Windows and Linux would need a different mechanism anyway. Registration also doesn't exist for the zip or for development runs.
- *Passing arguments to a second process that forwards and exits (classic single instance)*: the CLI path already uses arguments to mean "run as CLI"; a second app process would have to find the first through one of the mechanisms above anyway.
- *Piggy-backing on `projects.json`* (a "requested project" field): breaks "additive for older versions" the moment an older app rewrites the file and drops the field, and mixes a transient order into durable data.

### 2. Launching the app

A port `AppProcessLauncher` resolves the app from the running launcher's own path (`ProcessHandle.current().info().command()`), which works because the CLI is the app's launcher:

| Platform | Launch |
|---|---|
| macOS | the enclosing `ShogunAi.app` bundle through `open <bundle>`, so LaunchServices handles the Dock and, if the app is running without having been detected, simply activates it instead of starting a second one |
| Windows, Linux | the launcher executable with no arguments, detached (no inherited streams, not waited for) |
| zip install or development run (`java` as the command, no launcher) | not available: `AppNotAvailable` error, exit code 1, and the request is not written |

The bundle is recognised by a `.app/Contents/MacOS/` segment in the path; Windows and Linux by a launcher named `ShogunAi`/`ShogunAi.exe`. Anything else counts as not available. Commands are argv lists, as everywhere in the CLI.

### 3. Serving a request in the app

`Main.kt` already holds `ShellViewModel`, the board and `showMainWindow`; a small `AppRequestServer` (in `desktopApp`) turns a request into calls on them:

- `open-app`: `showMainWindow()` only.
- `open-project`: `viewModel.refresh()` first (so a project the CLI just registered exists in `projects`), then `showMainWindow()`, then `selectProject(project)` or, with `settings`, `editProject(project)`. A project missing after the refresh means the request is ignored.
- Both go through `requestLeave`, so unsaved edits produce the usual confirmation and are kept if the user keeps editing. `showMainWindow` only restores and focuses; it doesn't touch `windowState` bounds.
- If the destination is already the requested one (same project filter, or the same project's settings form), nothing is navigated, so a form isn't rebuilt and its edits aren't lost.
- `refresh()` re-reads the project list and prunes recents but keeps `destination`, which is what leaves the rest of the app as it was.

### 4. Registering a project

- A pure function in `core` (`ProjectDefaults`) builds the name, the worktrees folder and the main branch from the repository's top level and detected branch. `ProjectConfigViewModel.chooseFolder` and the CLI both call it, so the two can't drift (a unit test pins the values the form used to compute).
- `RegisterProjectUseCase(projectRepository, resolveRepository, …)` resolves the base repository (`ResolveRepositoryUseCase`), looks for an existing project by canonical path with `SameDirectory` (the same comparison the CLI uses to find the project of a repository), and returns `Added(project)` or `AlreadyRegistered(project)`. It only calls `ProjectRepository.save` for a new project, so an existing one is never rewritten.
- The new project is a normal `Project` with a random UUID and a default `ProjectConfig`, saved through the existing `JsonProjectRepository`; no field is added to `projects.json`. The save happens under `LocalFileLock`, so a concurrent app write can't drop it, and the app picks it up with `refresh()` (request) or on its next focus re-read.
- `UnregisterProjectUseCase` matches by canonical path when the folder exists and by the stored path text when it doesn't, and deletes only the catalog entry.
- Project names aren't required to be unique (the app doesn't require it); `project list` shows the path to tell them apart.

### 5. CLI shape

- `project` becomes a command group like `pool` and `hook`: `project add|list|remove`. `app` is a top-level command. Both are added to `CliCommand.kt` / `ShogunCli.kt`, with `--json` on the three `project` subcommands (`projectJson`, a documented contract like the existing ones) and a plain-text line for `app`.
- Exit codes follow the existing table: `0` success (including "already registered"), `1` operational failure (not a repository, app not available, project not found for `remove`), `2` usage (`--settings` without a repository, unknown option).
- `app` without a path outside a repository sends `open-app`. With an explicit path that isn't a repository it fails: the user asked for something specific.
- `remove` has no confirmation prompt: it only edits the catalog and `project add` brings it back; the app's confirmation exists because of the sidebar's accidental clicks.

### 6. Backwards compatibility

| Concern | How it is kept |
|---|---|
| `projects.json` schema | unchanged; the CLI writes through the same repository and `Project` class |
| Existing projects and worktrees | `add` never rewrites an existing entry; `remove` deletes one entry only; nothing under the worktrees folders or the repositories is touched |
| Older app / CLI on the same folder | `app.lock` and `app-requests/` are ignored by versions that don't know them; the new `ProjectRepository` calls are the existing ones |
| Newer CLI, older app already running | the older app doesn't hold the lock, so the CLI sees "not running" and launches the app; on macOS `open` activates the running instance instead of starting a second one. On Windows/Linux this could open a second window of the old version during an update; it clears with a restart. |
| Running app | requests go through `requestLeave` and don't reset `windowState`, selection or forms (decision 3) |
| Newer protocol read by an older new-style app | requests carry `version` and `action`; unknown ones are deleted unread |

## Risks / Trade-offs

- [Polling every 500 ms costs a little while the app is open] → one `listFiles` on a normally empty folder; negligible, and only while the lock is held.
- [A request left when the app fails to start would surprise the user later] → the 60-second expiry discards it, and the CLI only leaves a request after it can find a launcher.
- [Two app processes: only one serves requests] → acceptable, they already share nothing else; documented as a non-goal.
- [The CLI can't confirm the app displayed the project] → `shogun app` reports "requested"; a closed-then-reopened app serves it or drops it by expiry. An ack would need a second channel for little gain.
- [`open <bundle>` and the Cask's `shogun` link both point inside the bundle; if the user moved the app, the link is already broken] → same failure mode as every `shogun` command today.
- [A symbolic-link install of `shogun` on Linux resolves `ProcessHandle` to the real launcher path] → what we want; verify on the `.deb` layout during implementation.

## Migration Plan

No data migration. Release as a normal version: new files appear on first use. Rolling back removes the feature; `app.lock` and `app-requests/` can be left behind harmlessly.

## Open Questions

- Whether the multirepo follow-up wants `shogun app <multirepo folder>` to use an `open-group` action or to reuse `open-project`; it only adds an action to the request, so it can be decided in that change.
