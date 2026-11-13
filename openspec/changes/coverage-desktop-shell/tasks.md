# Tasks

## 1. State holders and models

- [ ] 1.1 Cover the uncovered lines and branches of `ShellViewModel`, `ShellDestination`, `AppRequestHandler` and `SidebarReorder`/`SidebarOrdering`/`RailInitials` with unit tests; verify with `./gradlew :desktopApp:koverXmlReport` that the files have no missed lines or branches
- [ ] 1.2 Cover `TourState`, `ClaudeHooksState`, `UpdateState`, `HerdrPluginSettings`, `InstallationCheckState`, `ResourcePreferences`, `ToolsPreferences`, `Appearance`, `AppMessages`, `FolderDrop` and `WindowShortcuts` with unit tests; verify the same way
- [ ] 1.3 Cover `ui/tray` (`TrayMenuModel`, `TrayPreferences`, `TrayIcons`) with unit tests, taking the system as a parameter where needed; verify the same way
- [ ] 1.4 Take the operating system as a parameter in `Browser`, `MacAppMenu` and `WindowBoundsState` and cover them; verify the same way
- [ ] 1.5 Rewrite any branch no input reaches in an equivalent form and record it in design.md; verify the existing tests still pass

## 2. Composables' lambdas

- [ ] 2.1 Add or extend UI tests for `AppShell`, `Sidebar` and `MachineStatusBar`; verify they have no missed lines or branches, or only what design.md lists as left
- [ ] 2.2 Add or extend UI tests for `AppMenuBar`, `PreferencesWindow`, `InstallationCheckWindow`, `AboutWindow`, `TourViews` and `ClaudeHooksViews`; verify the same way

## 3. Floors and docs

- [ ] 3.1 Measure `ui/shell` and `ui/tray`, raise the desktop floors in `desktopApp/build.gradle.kts` to the measured whole percentages and record the figures and what is left in design.md
- [ ] 3.2 Update the roadmap item in `docs/roadmap.md` and `docs/roadmap.es.md` with the desktop figures after this change and the packages left
- [ ] 3.3 Run `./gradlew test koverVerify` and verify it passes

## Workflow follow-up

- Archive the change, bring in `origin/develop`, re-measure the floors and open the pull request against `develop`, assigned to CiroFeliu.
