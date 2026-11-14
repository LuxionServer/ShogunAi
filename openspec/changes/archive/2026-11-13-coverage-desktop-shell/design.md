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

`openInBrowser`, `installMacAppMenuHandlers`, `isOnAnyDisplay` and `trayIconImage` talked to `java.awt.Desktop`, `GraphicsEnvironment` or `isMacOs` directly, so a test only reached the path of the machine it runs on. Following `AGENTS.md` ("code that depends on the operating system takes it as a parameter"), they now take what they need as a parameter defaulting to the real one:

- `AppDesktop` (new, in `ui/shell`) is the port over the parts of `java.awt.Desktop` the shell uses: browsing and the app menu's about, settings, quit and reopen handlers. `systemDesktop()` returns the real one where the system supports it. `openInBrowser` and `installMacAppMenuHandlers` take an `AppDesktop?`, and the tests pass a fake that records the handlers and calls them with real `java.awt.desktop` events.
- `isOnAnyDisplay` takes the screens' bounds and `screenBounds` takes the `GraphicsEnvironment`, so a headless system and a window off every screen are tested.
- `trayIconImage` and `AppMenuBar` take `mac`, like `isCloseWindowShortcut` already did, so both systems' icon, menus and shortcuts are tested on the macOS job.

### The menu bar, tested in an invisible window

`AppMenuBar` only composes inside a window's `MenuBar`. `AppMenuBarTest` composes it in a `ComposeWindow` that gets a native peer (`addNotify`) but is never shown, reads the Swing menus Compose builds and clicks their items. Tests that need a display (this one and the real `AppDesktop`) skip themselves with `assumeTrue`/`assumeFalse` on a headless JVM, as the Linux job is; coverage is verified on macOS, where they run.

### Logic out of window-only lambdas

What only ran inside a `Window` or `DialogWindow` content lambda, which no test composes without showing a window, moved to composables a test can render: `InstallationCheckPanel` (runs the check, checks again and copies the report), `PreferencesContent` (now also reads the tools, the hooks and the Herdr plugin when it opens) and `AboutContent` (takes the function that opens the repository link). The windows only wrap them.

`AppShell` kept code that only ran from its effects, its drop target or the palette. The pure parts moved to `ShellActions.kt` with unit tests: `runPaletteAction`, `sidebarMultirepos`, `tourProjectShown`, `openDroppedFolder`, `openProjectSettings`, `runnerInUse`, `declaresMobileProviders` and `loadedBranches`. The rest is covered by `ShellE2eTest`, which renders the whole shell over a real `AppContainer` in a temporary home, like the other end-to-end tests, with a recording clipboard so no test writes to the system's.

`FolderDrop`'s drop target became a class, `FolderDropTarget`, that takes how to read an event's transferable; its tests build the drag events by reflection, since Compose doesn't expose their constructor.

### Branches no input reaches

As in the earlier coverage changes, a branch the compiler generates but no input reaches is not excluded: the code is rewritten in an equivalent form whose branches all run. The rewrites made:

- `ClaudeHooksState.toolNeedsAttention` checked `!toolReady || state == BROKEN`, but a broken tool is never usable by the hooks, so it is `!toolReady`.
- `TourState.showsNotice` checked `step == null`, but a step is only set after the offer is remembered, so it is `hasProjects && !offered`.
- Chained safe calls on values that are never `null` became one safe call or an `if`: the Herdr plugin's action, the installed runners (`DetectedTools.runners` always has a value), a folder's sidebar keys, the pending multirepo removal, the moved project's previous worktrees folder, a workspace repository's alias in the sidebar labels, the selected worktree's path in the palette and the storage key of the view of all worktrees.
- `ShellViewModel.openForm` sets the destination through `select`, which already saves nothing for a form, so the case of a destination without a storage key is reached.
- `AppMessages.show` handled a message with and without an action through `?.` on the action, although only a message with an action can have its action chosen; the two cases are now separate.
- `ToolsPreferences.NONE` and `ResourcePreferences.NONE` built their repositories as anonymous objects; they are now the `internal` objects `NoToolPreferences`, `NoToolDetector`, `NoResourceLimits` and `NoMachineCapacity`, tested directly, which also removes a duplicated repository.
- The tour dialog's `when` over its step moved from the dialog's slot lambdas to two small composables (`TourConfirm`, `TourStepMessage`), as `coverage-desktop-worktrees` did, so the compiler's default branch isn't counted.

### What is left

8 lines and 4 branches of the package stay uncovered:

- Window-only lines: the content lambdas of the preferences, installation check and about windows, and the preferences window's `toFront`. Composing them needs a window on screen.
- Opening a link: `openInBrowser`'s default desktop and `AppDesktop.browse` open the system's browser.
- In `AppShell`: the update notice, which needs a real update check over the network; the machine view's opening of a worktree, which needs a running resource; saving project settings that write the repository's file; and the palette copying a path with no worktree selected, which the palette never offers.
- In `Sidebar`: one branch the compiler generates on the line that builds a project's row (no condition of ShogunAi's is on that line).

They are left for the change that covers the app's wiring, which owns the windows and the update check.

### Floors

Measured on macOS after this change, with the end-to-end tests:

| Scope | Lines | Branches |
|---|---|---|
| `ui/shell` and `ui/tray` | 99.38% (8 uncovered of 1,298) | 99.47% (4 uncovered of 749) |
| whole desktop app outside composables | 96.22% (482 uncovered of 12,759) | 90.80% (718 uncovered of 7,804) |

The floors go from 94 to 96 (lines) and from 87 to 90 (branches). Measured again after bringing in `develop` at f4224d22: 96.22% of lines (483 uncovered of 12,766) and 90.78% of branches (723 uncovered of 7,838), so the floors stay at 96 and 90.

## Risks / Trade-offs

- [UI tests of windows (`Window`, `DialogWindow`, `Tray`) need a display or can't run headless] → Test the window's content composable instead of the window, and move logic out of window-only lambdas into functions with unit tests; the menu bar's test uses an invisible window and skips itself on a headless JVM.
- [A test that only executes a lambda without asserting anything] → Every test asserts on the holder's state, on a recorded call or on the text shown.
- [Rewriting code for coverage] → Only equivalent rewrites, with the tests of both sides running, and listed here.
- [Parallel changes touch the roadmap or move the measured figures] → Roadmap lines are merged and the floors re-measured when bringing in `develop` before the pull request.
