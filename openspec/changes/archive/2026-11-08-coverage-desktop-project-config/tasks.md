# Tasks

## 1. View model and models

- [x] 1.1 Cover the uncovered lines and branches of `ProjectConfigViewModel` (provider switches and suggestions, environment rows, approval and revocation, base path inspection, emulator and simulator section states, unsaved changes, local file picker, save outcomes) with unit tests; verify with `./gradlew :desktopApp:koverXmlReport` that the file has no missed lines or branches
- [x] 1.2 Cover `RepositoryConfigDraft`, `LocalFilePickerState`, `RepositoryProvisioningState` and `RepositoryRelocation` with unit tests; verify the same way
- [x] 1.3 Rewrite any branch no input reaches in an equivalent form and record it in design.md; verify the existing tests still pass

## 2. Composables' lambdas

- [x] 2.1 Add `EmulatorSettingsSectionUiTest` and `SimulatorSettingsSectionUiTest` covering every section state, the selectors, the no-window switch and the init script install and uninstall dialogs; verify both files have no missed lines or branches
- [x] 2.2 Add `RepositoryConfigSectionUiTest` covering the provider switches and options, the environment rows, approval and the unreadable file; verify the file has no missed lines or branches
- [x] 2.3 Add `ProjectSettingsViewUiTest` covering the save outcomes, the save bar, the section index at both widths, the preview and discard dialogs, the general, local files and agents sections and `LocalFilePickerDialog`; verify the files have no missed lines or branches

## 3. Floors and docs

- [x] 3.1 Check `ui/projectconfig` is at 100% of lines and branches, raise the desktop floors in `desktopApp/build.gradle.kts` to the measured whole percentages and record the figures in design.md
- [x] 3.2 Update the roadmap item in `docs/roadmap.md` and `docs/roadmap.es.md` with the desktop figures after this change and the packages left
- [x] 3.3 Run `./gradlew test koverVerify` and verify it passes

## Workflow follow-up

- Archive the change, bring in `origin/develop`, re-measure the floors and open the pull request against `develop`, assigned to CiroFeliu.
