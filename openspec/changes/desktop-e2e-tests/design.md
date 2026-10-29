# Design

## Context

- `AppContainer` builds every adapter with defaults read at construction: `System.getProperty("user.home")` for `~/.shogunai` (projects, settings, trust, logs, tour), `CLAUDE_CONFIG_DIR` and `user.home` for Claude Code's settings, `SHELL` and `PATH` for the login shell's `PATH`, and `ToolCatalog.userBinFolders` (which on macOS and Linux includes `/opt/homebrew/bin`, `/usr/local/bin` and `~/.local/bin`) to find `shogun`.
- The main window's content is `AppearanceScope { AppShell(appContainer, shellViewModel, …) }` inside `main.kt`'s `application {}`; `ShellViewModel` is built inline there. The Preferences window renders `ClaudeHooksSection(appContainer.claudeHooks)`.
- Adding a project and choosing local files open `JFileChooser` through `FilePicker`, which a Compose UI test can't answer.
- The host-environment change, running in parallel, will resolve the home folder, the shell and the `PATH` in one place. This change must not depend on it nor touch that wiring.

## Goals / Non-Goals

**Goals:**
- Drive the real wiring (`AppContainer`) through the real shell composables, so a regression anywhere between the UI and Git shows up.
- Keep the developer's machine untouched and the tests deterministic enough to run on Linux, macOS and Windows CI.

**Non-Goals:**
- Native windows, the menu bar, the tray and system dialogs: the tests render the window's content with `runComposeUiTest`, not an AWT window.
- Kover coverage (coverage-kover), the CLI's end-to-end tests (cli-e2e-tests) and the packaged app (packaged-smoke-test).
- Launching agents, terminals, Herdr or Orca: the flows stop before anything leaves the app.

## Decisions

### A dedicated Gradle task owns the isolation

`desktopE2eTest` is a `Test` task over the desktop app's test classes that includes only `app.luxion.shogunai.e2e`, and `test` excludes that package and depends on the new task, so `./gradlew test` (what CI runs) runs both. The task sets, for its JVM: `user.home`, `HOME` and `USERPROFILE` to `build/e2e/home`; `java.io.tmpdir` to `build/e2e/tmp`; `SHELL` to empty, so no login shell is read; `PATH` filtered to the folders that don't contain a `shogun` executable, with `build/e2e/bin` first; `CLAUDE_CONFIG_DIR` removed; `GIT_CONFIG_NOSYSTEM=1`; and a system property `shogunai.e2e.home` with the same home. It wipes `build/e2e` before running and isn't cacheable.

Alternatives: setting `user.home` from the tests in the regular `test` JVM would leak into the other tests and can't change `HOME` for the Git processes; a separate source set duplicates the dependencies for no gain; injecting a home into `AppContainer` is what host-environment does, and doing it here would collide with it.

### One home per test inside the task's home

The harness (`E2eEnvironment`) checks that `shogunai.e2e.home` is set and equals `user.home`, failing with "run ./gradlew :desktopApp:desktopE2eTest" otherwise. Before each test it empties the home, writes a `.gitconfig` with a generic identity (`ShogunAi Test <test@example.com>`, `init.defaultBranch=main`), and creates the test's repositories under a fresh temporary folder. Tests run sequentially in one JVM, so emptying the shared home is enough; every test builds its own `AppContainer`.

### The tests render the window's content, built the way `main.kt` builds it

`main.kt`'s inline `ShellViewModel(…)` construction moves to `shellViewModelOf(appContainer)` in the `shogunai` package, used by `main.kt` and by the harness, so both build the same view model. The harness renders `CompositionLocalProvider(LocalShownErrorLog, LocalViewModelStoreOwner, LocalFilePicker) { AppearanceScope(appearance) { AppShell(…) } }` with an `InactivityPreferences` and `AppMessages` like `main.kt`. The hooks test renders `ClaudeHooksSection(appContainer.claudeHooks)`, the composable the Preferences window shows, after its `refresh()`.

### A composition local for the pickers

`LocalFilePicker` provides an interface with `pickDirectory` and `pickFiles`, defaulting to the current `JFileChooser` implementation. `ProjectSettingsView`'s four direct `FilePicker` calls go through it. The add-project test provides a picker that answers with the test repository. The multirepo dialogs keep their existing `pickFolder` parameters.

### Environment-dependent branches are asserted, not forced

On macOS `ToolCatalog.userBinFolders` always includes `/opt/homebrew/bin`, so on a developer's Mac with Homebrew's `shogun` the command is found even with the filtered `PATH`, while on CI it isn't. Forcing one answer needs the host-environment seam, so:
- The tour test follows whichever step the app shows: on the "command" step it continues without it; either way it then checks the sample project.
- The hooks test puts a fake `shogun` launcher in `build/e2e/bin` (first on the `PATH`, so it's found first on every OS) and asserts the hooks it writes run a `shogun` launcher that exists, not a specific path.
- "The command is missing" stays covered by the existing unit and UI tests with fakes.

### Waiting on real work

Real Git work runs on `Dispatchers.IO`, outside the test clock. The harness waits with `waitUntil(timeoutMillis = 30_000)` on semantic conditions (a node with a text exists, a file exists) instead of `waitForIdle` alone.

## Risks / Trade-offs

- [Windows paths and launchers differ (`shogun.cmd`, no `~/.local/bin`)] → the fake launcher is written with the OS's name for it, and CI's Windows job runs the suite; a Windows-only failure is fixed on the pull request.
- [Background work of `AppContainer` (login shell thread, update checks, refresh loops) outlives a test] → the harness doesn't start the update loop `main.kt` starts, the login shell isn't read (`SHELL` empty), and each test's home is emptied before the next one builds its container.
- [Slower `./gradlew test`] → four flows, each a few seconds of Git work; the task runs after the regular tests.
- [Developer's Homebrew `shogun` makes the tour skip its command step locally] → accepted; CI covers the other branch.

## Dependencies and follow-ups

- host-environment: once the home, shell and `PATH` are resolved in one place, the harness can inject them instead of relying on the task's JVM settings, and the tour and hooks tests can force "command missing" and "command installed" deterministically. Left as a follow-up for that change.
- coverage-kover: Kover instruments every `Test` task of the module, so `desktopE2eTest` counts towards the desktop app's coverage, including the composables it excludes from the threshold. If it only instruments `test`, it has to add `desktopE2eTest`.
- A test of installing the `shogun` command from the app needs a packaged launcher (`jpackage.app-path`), which is packaged-smoke-test's ground.
