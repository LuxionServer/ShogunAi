# Tasks

## 1. Machine entry point

- [x] 1.1 Add `ShellDestination.MachineOverview` with its storage key and its cases in the destination's `when`s, and `ShellViewModel.selectMachineOverview()`; test the storage round trip
- [x] 1.2 Add `MachineResources.overviewResources` (visible resources plus the desktop turn); test it with and without declared providers
- [x] 1.3 Add the "Machine" item before the resource items in `MachineStatusBar`, always show the bar, and wire it in `AppShell`; extend `MachineStatusBarUiTest` (only "Machine" with nothing declared, selected while the overview is shown, icon only when compact)
- [x] 1.4 Add `MachineOverviewView` (rows from `machineStatusItems`, the no-provider explanation) and route the destination to it; UI test the rows, opening a resource and the empty explanation
- [x] 1.5 Add the gear footer to `Sidebar` (expanded and rail) opening Preferences, wired from `AppShell`; UI test both layouts

## 2. Neutral missing tools

- [x] 2.1 Add `isMissingTool()` and use it in `machineStatusItems` so Android SDK not found, Docker unavailable and iOS simulators unsupported keep the reason but no attention; test each one and a real failure staying in danger

## 3. Gradle daemons view

- [x] 3.1 Add `showsDaemonState` and omit the state cell when it's false; test both cases
- [x] 3.2 Add `stopIdleReason` with the all-busy, all-unknown and mixed texts in English and Spanish, shown next to the disabled "Stop idle daemons"; test each text
- [x] 3.3 Show a dimmed "unknown" owner when the owner is null, and use ghost buttons for each row's Stop
- [x] 3.4 Add `buildCacheNotice` grouping the projects with the build cache off into one notice with details; test one and several projects, and draw it in the view

## 4. Validation

- [x] 4.1 Run `./gradlew test koverVerify` and raise any floor that passes a whole percentage
- [x] 4.2 Check the result in the app running hidden (`shogun headless start --fresh`, an empty isolated home): the bar with only "Machine", the overview with the free desktop and the no-provider explanation, its restore on relaunch, and the gear. The neutral Compose item and the Gradle view are checked with sample data in UI tests, since the app would list this machine's real daemons

## Workflow follow-up

- Archive the change and update `docs/roadmap.md` and `docs/roadmap.es.md`, removing the "The machine's entry point and its alarms" bullet.
- Merge `origin/develop`, re-run the tests and open the pull request with `--base develop --assignee CiroFeliu`.
