# Proposal

## Why

The app navigates linearly: project list → worktree screen → configuration screen. Every switch between projects goes back through the list, and theme, language and version take two rows of the window on every screen. The epic's direction (`docs/roadmap.md`, "Desktop app redesign") makes the app a control tower: a persistent sidebar to move between views, a content area, and the conventions of a desktop app (application menu, Preferences, About). This change builds that frame. Later changes fill it with the cross-project views, the worktree detail and the machine resources.

## What Changes

- A window shell with a persistent sidebar and a content area replaces the full-screen navigation. **BREAKING** (UI): the project list screen is removed.
- The sidebar lists the projects. Selecting one shows its worktrees in the content area. "Add project" and each project's context menu (Edit, Remove) live there. It's structured in sections, so the "Active", "All" and "Machine" entries of later changes slot in.
- The app reopens on the last selected view and restores the window's size and position.
- On narrow windows the sidebar collapses to a compact rail, and it can also be toggled from the menu or the keyboard.
- An application menu (File, View, Help) with shortcuts. On macOS, Preferences and About go in the app menu.
- A Preferences window holds the theme mode and the language. The top bar with both selectors and the version footer are removed. The version moves to About.
- Project search and sort are removed from the UI. With projects in the sidebar they aren't needed, and the command palette (`command-palette`, later in the epic) covers jumping to a project by name.

## Capabilities

### New Capabilities

- `app-shell`: the main window's structure, sidebar navigation, persistence of the last view and window bounds, compact sidebar, application menu, Preferences window and About.

### Modified Capabilities

- `project-catalog`: projects are listed, added, edited and removed from the sidebar instead of a project list screen. Search and sort are removed.
- `visual-theme`: the theme mode is chosen in Preferences instead of a control on every screen.
- `ui-localization`: the language is chosen in Preferences, and the supported-languages scenarios no longer name specific screens.
- `keyboard-shortcuts`: "new project" becomes "add project", available from anywhere with Shift+Cmd/Ctrl+N.
- `worktree-workspace`: the reload triggered by re-entering the worktree screen now follows selecting the project in the sidebar.
- `worktree-auto-refresh`: watching stops when another view is selected, instead of when going back to the project list.

## Impact

- `ui/AppRoot.kt` and `ui/Screen.kt` are replaced by the shell (`ui/shell/`). `ui/projectlist/` is removed, and its ViewModel logic moves to the sidebar's state.
- `main.kt`: application menu, window state, Preferences window, macOS `Desktop` handlers for About and Preferences.
- `core`: `JsonSettingsFile` stores the last selected view and the window bounds next to the theme and language, each saved without touching the others.
- Docs: `docs/architecture/ui.md` (navigation section) and `ui.es.md`, `README.md`/`README.es.md` screenshots or usage text if they describe the project list.
