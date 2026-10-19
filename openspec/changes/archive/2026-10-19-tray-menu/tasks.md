# Tasks

## 1. Recents and settings

- [x] 1.1 Add `recentProjects`, `recentWorktrees`, `showTrayIcon` and `keepRunningInTray` to the settings with backward-compatible reading; add storage tests and verify they pass
- [x] 1.2 Record recents in `ShellViewModel` on opening a project and selecting, creating or launching a worktree, with the limits and pruning; add view-model tests for each spec scenario and verify they pass

## 2. Tray

- [x] 2.1 Add the tray icons (macOS light and dark variants, Windows and Linux) and the `Tray` in `main.kt`, shown when supported and enabled, with "Show ShogunAi", "New worktree…", recents, Preferences and Quit; add a test for the menu model built from recents and verify it passes
- [x] 2.2 Add the worktree submenus with the launch actions from the detail and the activity marks, and route launch errors to the main window; add menu model tests (available actions, marks, removed worktree) and verify they pass
- [x] 2.3 Add the Preferences options and the hide-on-close behavior through the leave guard; add tests for the default close, keep running and unsaved settings, and verify they pass

## 3. Docs and strings

- [x] 3.1 Add the strings in English and Spanish, document the icon and its options in `docs/guide/desktop-app.md` and `desktop-app.es.md`, and verify `StringsTest` and `mkdocs build --strict` pass

## 4. Validation

- [x] 4.1 Run `./gradlew test` and verify every module passes
- [x] 4.2 In the running app on macOS (secondary display), check the icon in light and dark menu bars, a recent worktree's actions, an agent mark, and closing the window with "Keep running" on and off

## Workflow follow-up

- Archive the change before opening its pull request.
