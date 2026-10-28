# Tasks

## 1. Window state

- [x] 1.1 Add `rememberAppHidden()` in the shell package, backed by `AppHiddenListener` when `APP_EVENT_HIDDEN` is supported and `false` otherwise
- [x] 1.2 In `main.kt`, compute whether the main window is shown (`windowVisible`, not minimized, app not hidden) and pass it to `AppShell`; in `AppShell`, feed it to `board.setWindowActive` instead of `isWindowFocused`, keeping the focus refresh as is
- [x] 1.3 Adapt the `WorktreeBoardTest` case for the inactive window to minimized or hidden and verify `./gradlew :desktopApp:test` passes

## 2. Docs

- [x] 2.1 Update the watcher paragraph in `docs/architecture/ui.md` and `ui.es.md`

## 3. Validation

- [x] 3.1 Run `./gradlew test` and verify every module passes
- [x] 3.2 In the running app on the secondary screen, verify that the window keeps updating while unfocused (an agent or a new file shows up without focusing it), and with `ps` sampling that no `git` process starts while it is minimized or the app is hidden

## Workflow follow-up

- Archive the change before opening its pull request.
