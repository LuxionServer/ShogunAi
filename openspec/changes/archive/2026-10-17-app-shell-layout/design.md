# Design

## Context

`AppRoot` holds `var screen by remember { mutableStateOf<Screen>(...) }` and switches between `ProjectList`, `ProjectConfigForm` and `WorktreeManagement`. Above them there's a row with `LanguageToggle` and `ThemeModeToggle`, and below them a row with the version. `ProjectListViewModel` holds the catalog with search and sort. Theme and language are stored in `~/.shogunai/settings.json` through `JsonSettingsFile`, which already saves each key without touching the others. `main.kt` opens one `Window` with no menu bar. Depends on `ui-design-foundations` (tokens, `IconAction`, `Modifier.interactive`, `DangerButton`).

## Goals / Non-Goals

**Goals:**
- One window frame that the rest of the epic fills: sidebar sections, a content area, menus, Preferences.
- Desktop conventions on each platform without per-platform UI code beyond the menu placement.

**Non-Goals:**
- The cross-project views (`all-worktrees-view`, `active-worktrees-view`) and the machine view (`machine-resources-view`). This change only reserves their sidebar sections.
- Redesigning the worktree or configuration content. The current screens are hosted as they are, minus their own navigation buttons.
- A system tray icon.

## Decisions

### Navigation as a persisted selection, not a screen stack
`Screen` is replaced by `ShellDestination`, a sealed type: `Project(projectId)`, `ProjectSettings(projectId)`, `AddProject`, and later `Active`, `All(projectFilter)`, `Machine(section)`. A `ShellViewModel` owns the current destination, the project catalog (moved from `ProjectListViewModel`) and the sidebar state. The destination is stored as a small serializable value (`lastDestination`) in `settings.json`. Only `Project`, `Active`, `All` and `Machine` are restored: a form is never reopened on startup. *Alternative considered:* `navigation-compose` with a back stack. Rejected because a sidebar app has no meaningful back stack, and the architecture doc already explains why the app avoids it.

### Sidebar layout
Fixed width of 240 dp when expanded, 64 dp as a rail. Sections from top to bottom: a views section (reserved, empty until `all-worktrees-view` and `active-worktrees-view`), "Projects" (alphabetical, with "Add project" at the end), and "Machine" (reserved for `machine-resources-view`). An empty section isn't drawn. Each project row uses `Modifier.interactive`, shows the selection with `secondaryContainer`, and opens a `ContextMenuArea` with Edit project and Remove project. The rail shows the first letter of each project in a rounded square, with a tooltip. The rail threshold is a window width of 900 dp. The manual toggle (`sidebarCollapsed`) is stored in settings and wins over the automatic threshold only when the window is wide.

### Forms in the content area
`AddProject` and `ProjectSettings` show the existing `ProjectConfigScreen` in the content area. Saving selects `Project(id)`, as today's navigation goes to the worktree screen. Cancel returns to the destination that was selected before, which `ShellViewModel` remembers. `project-settings-view` redesigns the form later.

### Menu and platform conventions
The `Window` gets a Compose `MenuBar` with File, View and Help. On macOS, `java.awt.Desktop` handlers (`setAboutHandler`, `setPreferencesHandler`) put About and Settings in the app menu, and the File menu drops them. The "Quit" entry is only shown off macOS. Menu labels come from `Strings`, so the menu follows the language. Shortcuts use `KeyShortcut(..., meta = isMac, ctrl = !isMac)`.

### Preferences and About as separate windows
Preferences is a small non-resizable `Window` (theme mode as a segmented selector, language as a dropdown). Only one instance is kept open, and opening it again brings it to the front. About is a `DialogWindow` with icon, name, `BuildInfo.VERSION` and the repository link opened with `Desktop.browse`. Both read and write through the same state holders the main window uses, so changes apply live.

### Window state
`rememberWindowState` is initialized from `windowBounds` in `settings.json` (x, y, width, height, maximized) and saved when the window closes. On startup, the saved bounds are checked against `GraphicsEnvironment.screenDevices`: if they don't intersect any display, the window is centered. The minimum size is 720×480.

## Risks / Trade-offs

- [Removing search and sort leaves no way to find a project by name until `command-palette` lands] → Alphabetical order covers the typical count. The palette is planned in the same epic, before the epic reaches `develop`.
- [Compose `MenuBar` on macOS renders in the system menu bar, but shortcuts can clash with focused text fields (Cmd+R, Cmd+N)] → Menu shortcuts are only bound to actions that are safe from text fields, and the tasks include a check with a focused field.
- [Multi-window state (Preferences) shares state holders with the main window] → They're plain `mutableStateOf` holders created in `AppContainer` scope, as the theme and language already are.

## Migration Plan

`settings.json` gains optional keys (`lastDestination`, `windowBounds`, `sidebarCollapsed`). Older files without them open on the first project with default bounds. Older app versions ignore unknown keys, because `JsonSettingsFile` already preserves keys it doesn't know.
