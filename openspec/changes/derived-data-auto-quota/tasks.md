# Tasks

## 1. Quota mode and Automatic quota (core)

- [ ] 1.1 Add `DerivedDataQuotaMode` and `derivedDataQuotaMode` to `ResourceSettings` with the effective-mode rule; verify with `JsonResourceLimitsRepositoryTest` cases for a file without mode and a fixed size (fixed), without mode and without size (Automatic), and a round trip of each mode
- [ ] 1.2 Add `DerivedDataSource.freeBytes()`, implemented in `MacDerivedDataSource` on the DerivedData location's file store and in `FakeDerivedDataSource`; verify with a `MacDerivedDataSourceTest` case on a temporary location and one for a missing location falling back to its existing parent
- [ ] 1.3 Add `ResolveDerivedDataQuotaUseCase` and `DerivedDataQuota` (mode, bytes or unknown) and carry the mode in `DerivedDataTrim`; verify with unit tests for every scenario of "Automatic DerivedData quota from the disk" (typical, minimum, maximum, unknown free space), fixed and none

## 2. Remembered sizes and parallel measurement (core)

- [ ] 2.1 Add the `DerivedDataSizeStore` port and `JsonDerivedDataSizeStore` (`~/.shogunai/derived-data-sizes.json`, atomic write), plus a fake in test fixtures; verify with tests for round trip, missing file and unreadable file returning an empty map
- [ ] 2.2 Add `MeasureDerivedDataUseCase` emitting remembered sizes first and measuring the rest at most four at once, saving only listed folders on completion and nothing on cancellation; verify with tests for unchanged, used-again, deleted and no-last-access folders, the four-at-once bound (fake source counting concurrent calls), one failing folder, `remeasure = true` and cancellation
- [ ] 2.3 Make `ListDerivedDataUseCase` measure through `MeasureDerivedDataUseCase` for `ORPHANS` and `ALL`; verify `ListDerivedDataUseCaseTest` still passes and a new case shows a remembered folder isn't measured

## 3. Command line

- [ ] 3.1 Resolve the effective quota in `shogun xcode trim` (no-quota message only for "No quota", unknown-automatic message, "(automatic)" in text, `quotaMode` in JSON) and wire the sizes store into the CLI's DerivedData source; verify with `ShogunCliTest` cases for automatic over the quota, automatic unknown, no quota, fixed unchanged and the JSON field
- [ ] 3.2 Update `CliHelp` for `xcode trim` and `docs/guide/cli.md` / `cli.es.md`; verify the help test and that both pages describe the automatic quota

## 4. Desktop app

- [ ] 4.1 Measure in `MachineResources` through `MeasureDerivedDataUseCase` (remembered sizes shown first, `remeasure` on Reload, compilation cache concurrently) and wire it in `AppContainer`; verify with `MachineResourcesTest` cases for remembered sizes shown at once, reload keeping provisional sizes, and cancellation on provider removal
- [ ] 4.2 Resolve the quota (free space plus measured usage) before trimming and expose its mode to the view; show "of N (automatic)" in the DerivedData view and sidebar summary, and only usage with "No quota"; verify with `MachineResourcesTest` and `XcodeDerivedDataViewUiTest` cases for automatic, fixed and none
- [ ] 4.3 Replace the quota row in Preferences > Resources with Automatic / Fixed / No quota (`ResourcePreferences.chooseQuotaMode`), explanation under Automatic, English and Spanish strings; verify with `ResourcesSectionUiTest` cases for each scenario of "Edit the quota in Preferences"
- [ ] 4.4 Update `docs/guide/providers.md`, `docs/guide/desktop-app.md` and their Spanish pages for the automatic default, the opt-out and remembered sizes; verify both languages say the same

## 5. Integration

- [ ] 5.1 Run `./gradlew test` and verify every module passes
- [ ] 5.2 Run the app on this Mac against the real DerivedData: check the first measurement, then restart and check remembered sizes appear at once, that Reload re-measures, and that Preferences shows the three modes; run `./gradlew :cli:run --args="xcode trim --dry-run"` from an Xcode repository and check it reports the automatic quota

## Workflow follow-up

- Archive the change with `/opsx:archive` and update roadmap item 4 in `docs/roadmap.md` and `docs/roadmap.es.md`, removing only its two DerivedData points.
- Open the pull request against `develop`.
