# Tasks

Prerequisite: the separate change that upgrades ShogunAi to Compose Multiplatform 1.12 is merged into `develop`, and this branch contains it, before group 1 starts.

## 1. Verify the open points on a real app

- [x] 1.1 In a throwaway worktree of ShogunAi on Compose 1.12, check whether Hot Reload offers an asynchronous run task that returns once the app is up, and whether the MCP's `restart` keeps system properties given through an init script; record both answers and the chosen launch form in this change's `design.md`
- [x] 1.2 In the same worktree, check that `NSRunningApplication.hide` through `osascript -l JavaScript` hides an app launched with `apple.awt.UIElement=true`, keeps the MCP's tree and click working and needs no Accessibility permission; otherwise confirm the AX fallback, and record the result in `design.md`
- [x] 1.3 Check whether ShogunAi's `focusMainWindow` activates the app under `apple.awt.UIElement=true` by sampling the frontmost app every second during a launch, and record whether the skip in design is needed

## 2. Provider declaration

- [x] 2.1 Parse and validate `providers.desktop-app` (`module` as a Gradle project path, `isolatedHome` boolean defaulting to `true`) in `.shogunai.json`, with tests for the declared, missing and invalid scenarios of the `headless-desktop-app` spec
- [x] 2.2 Show the provider in `docs/guide/providers.md` and `docs/guide/configuration.md` and their Spanish translations, with the declaration example, and verify `mkdocs build --strict` passes

## 3. Headless app use cases

- [x] 3.1 Add the `HeadlessAppLauncher` and `AppWindowService` ports, the new `WorktreeError` cases, and `WorktreeHeadlessAppUseCases` (start, stop, status, capture) with the state folder under the worktree's Git administrative folder; cover with fakes every scenario of start (already running, window timeout, early exit with log tail, `--visible`, `--fresh`), stop, status (crashed process reported as stopped), capture (not running, output path) and isolated home (kept, fresh, off), and verify `./gradlew :core:test` passes
- [x] 3.2 Return the not-supported error on every platform but macOS, with a test using a non-macOS platform fake
- [x] 3.3 Stop the worktree's headless app in `RemoveWorktreeUseCase` before deleting the folder, reporting a failure as a warning, with tests for removal while running and a failed stop

## 4. macOS implementations

- [x] 4.1 Write the init script that adds `apple.awt.UIElement=true` and, when asked, `user.home` to the module's run task, and the launcher that runs Gradle detached as argv with its log; test the generated script and the argv with a fake executor
- [x] 4.2 Implement `AppWindowService` on macOS with one JavaScript for Automation helper (window lookup by pid and layer 0, hide, show, Screen Recording preflight) and `screencapture -x -o -l`, passing the pid and paths as arguments; test the argv and the parsing of the helper's output with a fake executor
- [x] 4.3 Map denied permissions to the errors and warnings of "Say which permission is missing", with tests for the capture and hide cases

## 5. Command line

- [x] 5.1 Add `shogun headless start|stop|status|capture` with `--visible`, `--fresh`, `--output` and `--json`, help text and the provider-missing and outside-a-worktree errors; tests for every scenario of "Manage a worktree's headless app from the command line" and "Headless status as JSON", and verify `./gradlew :cli:test` passes
- [x] 5.2 Add the desktop app line to the session-start context on macOS, kept within the short context limit, with tests for the provider, no provider and Linux scenarios
- [x] 5.3 Document the commands in `docs/guide/cli.md` and `docs/architecture/cli.md` and their Spanish translations, and verify `mkdocs build --strict` passes

## 6. ShogunAi as the first user

- [x] 6.1 Declare `"desktop-app": { "module": ":desktopApp" }` in ShogunAi's `.shogunai.json` and the Hot Reload MCP server (`./gradlew :desktopApp:hotMcpServer`) in the repository's project MCP configuration; make ShogunAi leave out its menu bar status item when `shogunai.headless` is `true`, with a test, and verify the app still opens with its status item and takes focus normally with `./gradlew :desktopApp:run`
- [x] 6.2 Write the guide page on checking a desktop app in the background (`docs/guide/headless-app.md` and `.es.md`, in `mkdocs.yml`): what the Hot Reload MCP drives, `shogun headless capture` instead of its screenshot, permissions to grant to the terminal, re-reading the tree after each view change, and what still needs the desktop; verify `mkdocs build --strict` passes

## 7. Integration checks

- [x] 7.1 On macOS, with the person working in another app sampled every second as frontmost, start the headless apps of two ShogunAi worktrees, drive each with its own Hot Reload MCP (tree, click, `type_text`), capture both while hidden and covered, stop one and remove the other worktree: the frontmost app never changes, each capture shows its own app, and the removed worktree's app is stopped
- [x] 7.2 Run a headless ShogunAi next to the person's running ShogunAi and check the person's `~/.shogunai` is untouched (projects, settings and `app.lock` unchanged)
- [x] 7.3 Run `./gradlew test` and verify it passes with no compiler warnings

## Workflow follow-up

- Commit the implementation once the group above is validated, then archive the change (`/opsx:archive`) in this branch before opening the pull request.
- On archive, update `docs/roadmap.md` and `docs/roadmap.es.md`: remove the "Checking a running app without taking over the desktop" entry from "Under exploration", list in the desktop turn (Planned item 1) what still needs the desktop (keyboard shortcuts, native menus, the menu bar item, system dialogs, hover and hit-testing), and add follow-ups: Windows and Linux support, headless apps in the desktop app's worktree views, and the keyboard gap (cmp-bridge exploration, JetBrains feature request).
