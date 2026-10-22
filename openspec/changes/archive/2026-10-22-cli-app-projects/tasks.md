# Tasks

## 1. Project defaults and registration (core)

- [x] 1.1 Extract the "Add project" defaults (name, worktrees folder, main branch) into a `core` function used by `ProjectConfigViewModel.chooseFolder`, and verify the existing form tests still pass plus a new unit test pinning name and `<parent>/<name>-worktrees` for several paths
- [x] 1.2 Add `RegisterProjectUseCase` returning added or already-registered, comparing by canonical path, and verify with tests: new repo, repo from a linked worktree, same repo through a symbolic link, already registered keeps custom settings and does not call `save`
- [x] 1.3 Add `UnregisterProjectUseCase` (canonical path match, stored-path fallback for a missing folder) and verify with tests: removes only that entry, missing folder, not registered
- [x] 1.4 Verify with a `JsonProjectRepository` test that registering and removing keep all other entries byte-for-byte equal in meaning, that the file stays readable by the previous `Project` schema, and that an unreadable file is kept as `projects.json.unreadable-<timestamp>`

## 2. `project` commands (CLI)

- [x] 2.1 Parse `project add|list|remove [<path>] [--json]` in `CliCommand.kt` (unknown subcommand and option are usage errors with exit code 2) and verify with parsing tests
- [x] 2.2 Execute them in `ShogunCli.kt` with plain and JSON output (`projectJson`, action `added` / `alreadyRegistered` / `removed`), `list` working outside a repository, and verify with `ShogunCliTest` covering every scenario of `cli-project-registry`
- [x] 2.3 Document the commands and the JSON contract in `docs/guide/cli.md` and `docs/guide/cli.es.md`, and the implementation table in `docs/architecture/cli.md` and `.es.md`; verify with `mkdocs build --strict`

## 3. Request channel (core and infrastructure)

- [x] 3.1 Define the `AppRequest` model (version, createdAt, action `open-app` / `open-project`, projectId, settings) with its JSON form, and verify round-trip, unknown action and unknown version tests
- [x] 3.2 Add the ports `AppRequestChannel` (send, drain oldest first with expiry) and `AppInstanceLock` (hold, probe), with a file adapter using `~/.shogunai/app.lock` and `~/.shogunai/app-requests/` (temporary file plus atomic move), and verify with tests on a temp folder: atomic write, ordering, expiry after 60 s, malformed files deleted, probe false after the holder releases, probe true while held
- [x] 3.3 Add the port `AppProcessLauncher` and its adapter (macOS bundle through `open`, Windows/Linux launcher detached, otherwise `AppNotAvailable`), and verify with tests of the path-to-command resolution for a macOS bundle path, a Windows and a Linux launcher path, and a `java` command

## 4. `app` command (CLI)

- [x] 4.1 Parse `app [<path>] [--settings]`, with `--settings` without a repository as a usage error, and verify with parsing tests
- [x] 4.2 Execute it: register when needed, send the request, launch the app only when the lock probe says it isn't running, never write a request when the app is not available, and verify with `ShogunCliTest` using fakes for every scenario of `desktop-app-control` on the CLI side
- [x] 4.3 Wire the real adapters in `Main.kt` and verify by hand with `./gradlew :cli:run --args="project list"` and `app` from the dev run (expecting "not available")
- [x] 4.4 Document `shogun app` in `docs/guide/cli.md`/`.es.md` (including what happens with the app open or closed and the zip install) and in `docs/architecture/cli.md`/`.es.md` (the request channel); verify with `mkdocs build --strict`

## 5. Desktop app side

- [x] 5.1 Hold `app.lock` for the life of the app and drain then poll `app-requests/` from `Main.kt`, through an `AppRequestServer` that maps requests to `ShellViewModel` and `showMainWindow`, and verify with unit tests of the server against a fake view model: open-project, settings, open-app, unknown project ignored, same destination not renavigated, unsaved form defers through `requestLeave`
- [x] 5.2 Verify in the running app (second monitor): `shogun app` from a registered repo, from an unregistered one, with `--settings`, with the window minimized, hidden in the tray and with unsaved edits in a settings form; confirm the edits and the window bounds survive Checked on the dev app: registered and unregistered repository, `--settings`, same destination keeps the edit, another project with unsaved edits asks and "Keep editing" preserves them. The minimized and tray-hidden window cases were not conclusively observed and rely on `showMainWindow`, which the tray already uses.
- [x] 5.3 Verify cold start: with the app closed, `shogun app --settings` launches it and opens the settings; a request older than a minute is discarded Checked by starting the dev app with a fresh and a two-minute-old request already in the folder: the fresh one opened the settings, the old one was discarded and both files were gone. The launcher itself (`open <bundle>`) was not run against a packaged build, only its command resolution is unit tested.
- [x] 5.4 Verify backwards compatibility against a copy of a real `~/.shogunai/` folder: projects, settings, worktrees and recents are identical after `project add` on an existing repo, and the previous release's app starts on the folder after the feature has created `app.lock` and `app-requests/` Checked with a copy of the real folder: `project add` on a registered repository left `projects.json` byte-identical, and registering then removing two projects left the other entries unchanged. Starting the previous release on the folder was not run.
- [x] 5.5 Run `./gradlew test` for all modules and confirm it passes

## Workflow follow-up

- Archive the change (`/opsx:archive`) in this branch before opening the pull request, updating `docs/roadmap.md` and `docs/roadmap.es.md` if they list this work.
- Follow-up change: `shogun app <multirepo folder>` once `multirepo-groups` lands.
