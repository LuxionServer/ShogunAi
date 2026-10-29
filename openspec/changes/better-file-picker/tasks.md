# Tasks

## 1. Native folder picker

- [ ] 1.1 Add `io.github.vinceglb:filekit-dialogs` 0.17.0 to the version catalog and `desktopApp`, and verify `./gradlew :desktopApp:compileKotlin` builds without warnings
- [ ] 1.2 Make `PathPicker.pickDirectory` suspending and remove `pickFiles`; implement `NativeFolderPicker` with FileKit (owner window, starting folder only when it exists, title, one dialog at a time) and `SwingFolderPicker` as its fallback on failure; verify with unit tests of the fallback and the starting folder through injected seams
- [ ] 1.3 Provide `LocalPathPicker` from the main window with it as owner, make `pickFolder` in `AddMultirepoDialog` and `MultirepoSettingsView` suspending and default to `LocalPathPicker.current`, and launch every folder choice (3 in `ProjectSettingsView`, 2 in `AddMultirepoDialog`, 2 in `MultirepoSettingsView`) in a coroutine scope; verify `MultirepoSetupTest`, the project settings tests and `AddProjectE2eTest` pass with `AnsweringPathPicker` updated
- [ ] 1.4 Add ProGuard keep rules for JNA, FileKit and dbus-java and the runtime modules `suggestRuntimeModules` reports; verify the release distributable (`./gradlew :desktopApp:runReleaseDistributable`) opens the native folder dialog on macOS and fills the field
- [ ] 1.5 Update the `FilePicker` section of `docs/architecture/ui.md` and `ui.es.md` for the native dialog, the fallback and the suspending port, and verify both pages say the same

## 2. Local file candidates in core

- [ ] 2.1 Move the mobile secret name classification and the generated folders set to one shared place used by `DetectMobileSecretsUseCase`; verify its existing tests still pass unchanged
- [ ] 2.2 Add `LocalFileCandidate` and `ListLocalFileCandidatesUseCase` (untracked and ignored entries with `--directory`, folders, generated folders dropped, secret kind, provided by the repository, Git failure as `WorktreeError.GitCommandFailed`); verify with unit tests covering every scenario of "List the untracked and ignored entries", "Suggest the detected mobile secrets" and "Files the repository already provides"
- [ ] 2.3 Wire it in `AppContainer` and inject it into `ProjectConfigViewModel`; verify the app compiles and the view model tests pass

## 3. In-app local file picker

- [ ] 3.1 Add `LocalFilePickerState` (load states, checked set from the current entries, filter, glob matching, pattern offer, sorting, result to apply) and the view model's open/apply/cancel; verify with unit tests for "Declared entries start selected", "Filter the entries", "Add the filter as a pattern", "Apply the selection" and the glob matcher
- [ ] 3.2 Add `LocalFilePickerDialog` and replace "Browse…" under "Local files" with "Choose…", disabled without a base repository; add the strings in English and Spanish; verify with Compose UI tests for opening, the suggested and provided rows, filtering, applying, cancelling and the loading, error and empty states
- [ ] 3.3 Add an end-to-end test that opens the picker over a real repository with an ignored `.env`, checks it, applies and saves, and verify `./gradlew :desktopApp:desktopE2eTest` passes
- [ ] 3.4 Document the local file picker in `docs/architecture/ui.md` and `ui.es.md`, and verify both pages say the same

## 4. Integration

- [ ] 4.1 Run `./gradlew test` and verify every module passes with no warnings
- [ ] 4.2 Check the result visually with `shogun headless start` and `shogun headless capture`: the local file picker over a repository with suggested, provided and plain entries, filtered and with a pattern, and the settings list after applying
- [ ] 4.3 Check the native folder dialog in the running app on macOS (desktop turn taken with `shogun desktop acquire --wait`) for a project's base repository and a multirepo's folder, cancelling and choosing

## Workflow follow-up

- Archive the change with `/opsx:archive` and update `docs/roadmap.md` and `docs/roadmap.es.md` if an item is affected.
- Open the pull request against `develop`, assigned to CiroFeliu.
