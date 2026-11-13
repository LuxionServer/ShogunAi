# Design

## Context

`ui/shell` holds the app's frame: `AppShell` (the main window's layout, dialogs and effects), `ShellViewModel` (the catalog of projects and multirepos, the current destination and the sidebar's order), `ShellDestination`, the sidebar and its reordering, the menu bar (`AppMenuBar`, `MacAppMenu`), the window shortcuts and bounds, the preferences window and its sections (tools, resources, Claude Code hooks, the Herdr plugin, updates), the guided tour, the installation check window and the app's messages. `ui/tray` holds the menu bar icon: its image, its preferences and the model of its menu.

Kover excludes functions annotated `@Composable`, but the lambdas inside them are compiled into separate methods without the annotation, so it counts them. Most of what is uncovered in the composable files (`AppShell`, `Sidebar`, `AppMenuBar`, `TourViews`, `PreferencesWindow`, `InstallationCheckWindow`) is those lambdas. Most of what is uncovered in the state holders is branches.

Measured on macOS on `develop` at fa63f042, with the end-to-end tests:

| File | Lines uncovered | Branches uncovered |
|---|---|---|
| `ui/tray/TrayIcons.kt` | 54 of 54 | 8 of 8 |
| `AppShell.kt` | 48 of 151 | 36 of 57 |
| `AppMenuBar.kt` | 37 of 40 | 27 of 28 |
| `WindowBoundsState.kt` | 17 of 17 | 8 of 8 |
| `Sidebar.kt` | 14 of 147 | 12 of 46 |
| `TourViews.kt` | 13 of 62 | 11 of 40 |
| `InstallationCheckWindow.kt` | 12 of 21 | 1 of 4 |
| `MacAppMenu.kt` | 12 of 12 | 10 of 10 |
| `ResourcePreferences.kt` | 12 of 79 | 11 of 51 |
| `FolderDrop.kt` | 12 of 18 | 8 of 14 |
| `ShellViewModel.kt` | 11 of 195 | 23 of 144 |
| `PreferencesWindow.kt` | 10 of 13 | 4 of 5 |
| `ClaudeHooksState.kt` | 8 of 59 | 14 of 52 |
| `ToolsPreferences.kt`, `Appearance.kt`, `Browser.kt`, `TourState.kt`, `AppMessages.kt`, `ShellDestination.kt`, `WindowShortcuts.kt`, `AboutWindow.kt`, `InstallationCheckState.kt` | 25 | 39 |
| `ui/tray/TrayPreferences.kt`, `ui/tray/TrayMenuModel.kt`, `RailInitials.kt`, `SidebarOrdering.kt`, `ClaudeHooksViews.kt`, `UpdateState.kt`, `SidebarReorder.kt`, `AppRequestHandler.kt`, `HerdrPluginSettings.kt`, `MachineStatusBar.kt` | 0 | 19 |
| `ui/shell` and `ui/tray` | 283 of 1,328 | 231 of 803 |
| whole desktop app outside composables | 763 of 12,788 (94.03%) | 957 of 7,858 (87.82%) |

## Goals / Non-Goals

**Goals:**

- 100% of the lines and branches of `ui/shell` and `ui/tray`, or as close as the package's tests can reach, with what is left listed here.
- Raise the desktop floors to the whole percentages measured after this change.

**Non-Goals:**

- Other packages (machine views, multirepos, shared components, the app's wiring in `main.kt`); they are the next changes of the roadmap's plan.
- Changing how the shell looks or behaves, beyond fixing a defect the tests find.
- Production changes outside `ui/shell` and `ui/tray`, to stay clear of the parallel changes.
- A per-package Kover rule: Kover 0.9's `verify` rules apply to the whole module.

## Decisions

### State holders first, UI tests for the lambdas

The branches of `ShellViewModel`, `TourState`, `ClaudeHooksState`, the preferences' holders, the sidebar's ordering and the tray's model are covered with plain unit tests next to the existing ones (`ShellViewModelTest`, `TrayMenuModelTest`, `TrayPreferencesTest`…), using their fakes. They are faster and say more than a UI test.

What only runs from a composable's lambdas is covered with Compose UI tests that render the composable with a real holder in the state under test, act on it and assert on the state or on what is shown, like `SidebarFooterUiTest` and `ToolsSectionUiTest`.

### The operating system as a parameter

`openInBrowser`, `installMacAppMenuHandlers`, `isOnAnyDisplay` and `trayIconImage` talk to `java.awt.Desktop`, `GraphicsEnvironment` or `isMacOs` directly, so a test only reaches the path of the machine it runs on. Following `AGENTS.md` ("code that depends on the operating system takes it as a parameter"), they take what they need (the desktop, the screens' bounds, `mac`) as a parameter defaulting to the real one, so the tests cover every path on the macOS job without touching the user's desktop.

### Branches no input reaches

As in the earlier coverage changes, a branch the compiler generates but no input reaches is not excluded: the code is rewritten in an equivalent form whose branches all run, and each rewrite is listed here with its reason.

## Risks / Trade-offs

- [UI tests of windows (`Window`, `DialogWindow`, `Tray`) need a display or can't run headless] → Test the window's content composable instead of the window, and move logic out of window-only lambdas into functions with unit tests.
- [A test that only executes a lambda without asserting anything] → Every test asserts on the holder's state, on a recorded call or on the text shown.
- [Rewriting code for coverage] → Only equivalent rewrites, with the tests of both sides running, and listed here.
- [Parallel changes touch the roadmap or move the measured figures] → Roadmap lines are merged and the floors re-measured when bringing in `develop` before the pull request.
