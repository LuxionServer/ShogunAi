# Tasks

## 1. Long paths on Windows

- [x] 1.1 Add `GitLongPaths.prepare(command, osName)` in `ProcessBuilderShellCommandExecutor.kt` and apply it before `WindowsArguments.prepare`, keeping the caller's command in `CommandResult`; cover Windows Git, non-Git and other-OS commands in a unit test and verify `./gradlew :core:test` passes
- [x] 1.2 Add `isPathTooLongFailure` to `GitFailureCause.kt` and route it in `WorktreeFolderRemoval` (first attempt and retry) to `finishLeftover`; add removal tests with a fake executor and file manager for: folder deleted and removal succeeds, base repository or own `.git` refused, deletion failing reports folder in use, and a cleanup with one too-long worktree reports no failure; verify `./gradlew :core:test` passes
- [x] 1.3 Document both in `docs/architecture/infrastructure.md`, `domain.md` and their `.es.md` translations

## 2. Machine section off the interface thread

- [x] 2.1 Add `ioDispatcher` to `MachineResources` and run every suspend `MachineUseCases` call (and the DerivedData size flow) on it; pass the test dispatcher in existing tests; add a `MachineResourcesTest` case that fails if the summary refresh or a shown view calls a use case outside the IO dispatcher; verify `./gradlew :desktopApp:test` passes
- [x] 2.2 Switch `DesktopTurnWatch`'s `read` and `forceRelease` to `Dispatchers.IO` in `AppContainer` and verify the desktop app compiles
- [x] 2.3 Update the machine paragraph in `docs/architecture/ui.md` and `ui.es.md`

## 3. Shown errors recorded once

- [x] 3.1 Add `ShownErrorLog` (bounded identity memory) and `LocalShownErrorLog` replacing `LocalDiagnosticsLog`, owned by `AppContainer` and provided in `main.kt`; make `RecordShownError` record through it; add a unit test (same instance once, a new equal instance again, bound) and adapt `ErrorNoticeDiagnosticsUiTest` with a case that removes and re-adds the notice; verify `./gradlew :desktopApp:test` passes
- [x] 3.2 Mention it in the error rendering paragraph of `docs/architecture/ui.md` and `ui.es.md`

## 4. Validation

- [x] 4.1 Run `./gradlew test` and verify every module passes without warnings
- [x] 4.2 Run the desktop app on macOS, open the Machine section and verify its summaries and the Gradle view still load

## Workflow follow-up

- Archive the change before opening its pull request, updating `docs/roadmap.md` and `roadmap.es.md` if affected.
