# Proposal

## Why

The roadmap's "Reliability on any machine" asks for the desktop app's coverage outside its composables to reach 100%, one package per change. After the worktrees and their inspector, the next package is the shell and the menu bar icon (`ui/shell` and `ui/tray`): the window, its sidebar and destinations, the menus and shortcuts, the preferences window and its sections, the guided tour, the installation check, update notices, Claude Code hooks and the Herdr plugin, and the menu bar icon with its menu. Today 283 of its 1,328 lines and 231 of its 803 branches run in no test, so a regression in how the app is navigated, configured or reached from the menu bar goes unnoticed.

## What Changes

- Unit tests for the shell's state holders and models (`ShellViewModel`, `ShellDestination`, `TourState`, `ClaudeHooksState`, `UpdateState`, `ResourcePreferences`, `ToolsPreferences`, `HerdrPluginSettings`, `SidebarReorder`, `SidebarOrdering`, `RailInitials`, `FolderDrop`, `WindowShortcuts`, `WindowBoundsState`, `AppMessages`, `AppRequestHandler`) and the menu bar's (`TrayMenuModel`, `TrayPreferences`, `TrayIcons`) cover the paths no test reaches today.
- Compose UI tests (`runComposeUiTest`, like the existing `*UiTest` classes) cover the lambdas the compiler moves out of the package's composables, which Kover counts: the shell, sidebar, menu bar, preferences and installation check windows, the tour, the machine status bar and the hooks views.
- Code that only talks to the operating system (`Desktop`, `GraphicsEnvironment`) takes it as a parameter where a test can't reach a path otherwise, as `AGENTS.md` asks for code that depends on the system.
- Branches no input reaches are rewritten in an equivalent form instead of being excluded, and listed with their reason in design.md.
- The desktop floors in `desktopApp/build.gradle.kts` go up to the whole percentages measured after this change.
- The roadmap item names what is left after this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None: the tests check the behavior the existing shell, preferences and menu bar specs already require. If they find a defect, its fix and the requirement it breaks are added to this change's specs before it is fixed.

## Impact

- Tests: new and extended tests under `desktopApp/src/test/kotlin/app/luxion/shogunai/ui/shell/` and `.../ui/tray/`.
- Production code: only in `ui/shell` and `ui/tray`: equivalent rewrites of unreachable branches, small seams where a path can't be reached from a test otherwise, and fixes for defects the tests find, if any.
- Build: `desktopApp/build.gradle.kts` (floors).
- Docs: `docs/roadmap.md` and `docs/roadmap.es.md`.
- Parallel work: `doctor-gradle-subfolder`, `swift-build-packages`, `multirepo-forget`, `device-lock-scope` and `gradle-daemon-build-view` branch from `develop` at the same time and stay out of `ui/shell` and `ui/tray`; this change is the only one raising the desktop floors. Any of them may touch the roadmap or change the measured figures, so the floors are re-measured and conflicts resolved when bringing in `develop` before the pull request.
