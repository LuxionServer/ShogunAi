# Proposal

## Why

The desktop app's UI tests render components with fakes, so they never catch what broke on another Mac: a flow that works with fakes and fails against a real repository, a real `~/.shogunai` or a real Claude Code settings file. The roadmap's "Reliability on any machine" item asks for end-to-end tests that drive the app's interface through its main flows, the way a new user does, and run in CI on every operating system.

## What Changes

- End-to-end tests of the desktop app that build the real `AppContainer`, render the same shell the main window shows and drive it through Compose UI test actions, against real Git repositories created in temporary folders:
  - The guided tour: start it from the first-launch state, create the sample project, see its worktrees on the board and end it, which removes the sample.
  - Adding a project: choose a repository folder, declare a local file and save, which lists the project and stores it.
  - Creating a worktree from the board's dialog for a project with local files, which creates the branch and the worktree folder with the files copied.
  - The Claude Code hooks section: install the hooks, which writes them into Claude Code's user settings, and remove them.
- An isolated environment for those tests: a dedicated Gradle test task in the desktop app with its own home folder (`user.home`, `HOME`, `USERPROFILE`), its own temporary folder, no `CLAUDE_CONFIG_DIR`, no login shell and a `PATH` without `shogun`, so the tests never read or write the developer's `~/.shogunai`, `~/.claude` or Git configuration. A guard fails the tests if they run outside it.
- `./gradlew test` runs that task, so CI runs it on Linux, macOS and Windows.
- A seam for the native folder and file pickers (a composition local defaulting to the current `JFileChooser` pickers), so a test can answer them.
- Any bug the tests uncover is fixed with its regression test.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `desktop-ui-testing`: adds end-to-end tests of the tour, adding a project, creating a worktree with local files and the Claude Code hooks section, run in an isolated home by `./gradlew test`.

## Impact

- `desktopApp/build.gradle.kts`: a `desktopE2eTest` task and the `test` task's dependency on it.
- `desktopApp/src/test/kotlin/app/luxion/shogunai/e2e/`: the tests and their harness.
- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/FilePicker.kt` and `ui/projectconfig/ProjectSettingsView.kt`: the picker seam.
- `main.kt`: the shell view model's construction moves to a function the tests reuse.
- No change to `core`, `cli` or the CI workflows.
