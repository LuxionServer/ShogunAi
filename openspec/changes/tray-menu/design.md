# Design

## Context

See proposal.md for motivation. `main.kt` runs a Compose `application` with one main `Window` whose close request quits the app, plus Preferences and About windows. The launch actions (`CopyWorktreeLaunchCommandUseCase`, `OpenWorktreeInTerminalUseCase`, the IDE and folder launchers) and the worktree activity (`worktreeActivity`, with Herdr states) exist and are used by the detail and the palette. The palette already remembers its own recent items in `settings.json`.

## Goals / Non-Goals

**Goals:**
- The menu reflects the worktrees' state the app already knows, without extra Git or process reads of its own.
- With the default options, the only visible difference is the icon: closing the window still quits.

**Non-Goals:**
- A full worktree browser or status dashboard in the menu; it lists recents only.
- Badges or counters on the icon.
- Agent selection in the menu; launches use the worktree's agent as the detail does.

## Decisions

**Compose `Tray`.** Compose Desktop's `Tray(icon, tooltip, menu)` over AWT `SystemTray`, with nested `Menu` items for each worktree. Shown only when `SystemTray.isSupported()`; on Linux desktops without a tray the icon and its options are absent. Alternative: a native macOS `NSStatusItem` through JNA; rejected for the platform-specific code it adds for a small gain (template images).

**Icon.** A monochrome 22 pt icon in two variants, picked from the system's dark mode on macOS (AWT can't mark it as a template image) and a coloured 16/32 px one on Windows and Linux. The variant follows changes of the system appearance.

**Recents.** `ShellViewModel` records a project when its view is opened and a worktree when it's selected, created or launched (copy, terminal, IDE, Herdr). `Settings.recentProjects` keeps 5 ids and `Settings.recentWorktrees` 8 `(projectId, path)` pairs, most recent first, pruned of removed projects and worktrees whenever a project's list is loaded. Separate from the palette's recents, which record palette runs only.

**Labels.** A worktree item reads `<branch or folder> — <project>`, prefixed with "● " when an agent is working and "! " when it needs the user (uncommitted changes, unpushed commits, or a blocked or done agent), from the activity the board already computed. The menu is rebuilt when that state changes; while the board hasn't loaded a project, its worktrees show without a prefix.

**Actions.** "Show ShogunAi" brings the main window forward (reopening it when kept in the tray). "New worktree…" opens the window and the create dialog for the last project used for creation. "Show in ShogunAi" opens "All worktrees" with the worktree selected. Launch actions call the same use cases as the detail, and their errors open the window and show the usual error notice.

**Close behavior.** `Settings.showTrayIcon` (default `true`) and `Settings.keepRunningInTray` (default `false`, enabled only with the icon shown). When keeping running, closing the main window hides it instead of quitting, unsaved settings still ask first through the leave guard, and Quit in the tray menu or Cmd/Ctrl+Q quits. On macOS the Dock icon stays, since the app keeps its Dock presence while running.

## Risks / Trade-offs

- [AWT tray menus look dated next to native ones] → Accepted for portability; revisit with a native status item if it proves a problem.
- [Keeping running in the tray keeps the watcher alive] → The background refresh already pauses while the window isn't focused, so a hidden window costs no polling.
- [Linux trays vary] → The feature is simply absent where `SystemTray` isn't supported.
