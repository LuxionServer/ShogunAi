# Tasks

## 1. Project defaults and registration (core)

- [ ] 1.1 Extract the "Add project" defaults (name, worktrees folder, main branch) into a `core` function used by `ProjectConfigViewModel.chooseFolder`, and verify the existing form tests still pass plus a new unit test pinning name and `<parent>/<name>-worktrees` for several paths
- [ ] 1.2 Add `RegisterProjectUseCase` returning added or already-registered, comparing by canonical path, and verify with tests: new repo, repo from a linked worktree, same repo through a symbolic link, already registered keeps custom settings and does not call `save`
- [ ] 1.3 Add `UnregisterProjectUseCase` (canonical path match, stored-path fallback for a missing folder) and verify with tests: removes only that entry, missing folder, not registered
- [ ] 1.4 Verify with a `JsonProjectRepository` test that registering and removing keep all other entries byte-for-byte equal in meaning, that the file stays readable by the previous `Project` schema, and that an unreadable file is kept as `projects.json.unreadable-<timestamp>`

## 2. `project` commands (CLI)

- [ ] 2.1 Parse `project add|list|remove [<path>] [--json]` in `CliCommand.kt` (unknown subcommand and option are usage errors with exit code 2) and verify with parsing tests
- [ ] 2.2 Execute them in `ShogunCli.kt` with plain and JSON output (`projectJson`, action `added` / `alreadyRegistered` / `removed`), `list` working outside a repository, and verify with `ShogunCliTest` covering every scenario of `cli-project-registry`
- [ ] 2.3 Document the commands and the JSON contract in `docs/guide/cli.md` and `docs/guide/cli.es.md`, and the implementation table in `docs/architecture/cli.md` and `.es.md`; verify with `mkdocs build --strict`

## 3. Request channel (core and infrastructure)

- [ ] 3.1 Define the `AppRequest` model (version, createdAt, action `open-app` / `open-project`, projectId, settings) with its JSON form, and verify round-trip, unknown action and unknown version tests
- [ ] 3.2 Add the ports `AppRequestChannel` (send, drain oldest first with expiry) and `AppInstanceLock` (hold, probe), with a file adapter using `~/.shogunai/app.lock` and `~/.shogunai/app-requests/` (temporary file plus atomic move), and verify with tests on a temp folder: atomic write, ordering, expiry after 60 s, malformed files deleted, probe false after the holder releases, probe true while held
- [ ] 3.3 Add the port `AppProcessLauncher` and its adapter (macOS bundle through `open`, Windows/Linux launcher detached, otherwise `AppNotAvailable`), and verify with tests of the path-to-command resolution for a macOS bundle path, a Windows and a Linux launcher path, and a `java` command

## 4. `app` command (CLI)

- [ ] 4.1 Parse `app [<path>] [--settings]`, with `--settings` without a repository as a usage error, and verify with parsing tests
- [ ] 4.2 Execute it: register when needed, send the request, launch the app only when the lock probe says it isn't running, never write a request when the app is not available, and verify with `ShogunCliTest` using fakes for every scenario of `desktop-app-control` on the CLI side
- [ ] 4.3 Wire the real adapters in `Main.kt` and verify by hand with `./gradlew :cli:run --args="project list"` and `app` from the dev run (expecting "not available")
- [ ] 4.4 Document `shogun app` in `docs/guide/cli.md`/`.es.md` (including what happens with the app open or closed and the zip install) and in `docs/architecture/cli.md`/`.es.md` (the request channel); verify with `mkdocs build --strict`

## 5. Desktop app side

- [ ] 5.1 Hold `app.lock` for the life of the app and drain then poll `app-requests/` from `Main.kt`, through an `AppRequestServer` that maps requests to `ShellViewModel` and `showMainWindow`, and verify with unit tests of the server against a fake view model: open-project, settings, open-app, unknown project ignored, same destination not renavigated, unsaved form defers through `requestLeave`
- [ ] 5.2 Verify in the running app (second monitor): `shogun app` from a registered repo, from an unregistered one, with `--settings`, with the window minimized, hidden in the tray and with unsaved edits in a settings form; confirm the edits and the window bounds survive
- [ ] 5.3 Verify cold start: with the app closed, `shogun app --settings` launches it and opens the settings; a request older than a minute is discarded
- [ ] 5.4 Verify backwards compatibility against a copy of a real `~/.shogunai/` folder: projects, settings, worktrees and recents are identical after `project add` on an existing repo, and the previous release's app starts on the folder after the feature has created `app.lock` and `app-requests/`
- [ ] 5.5 Run `./gradlew test` for all modules and confirm it passes

## Workflow follow-up

- Archive the change (`/opsx:archive`) in this branch before opening the pull request, updating `docs/roadmap.md` and `docs/roadmap.es.md` if they list this work.
- Follow-up change: `shogun app <multirepo folder>` once `multirepo-groups` lands.
