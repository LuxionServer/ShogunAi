# Proposal

## Why

Most of what users do in ShogunAi during the day is jump back into a worktree they were already working on: copy its launch command, open it in the terminal or the IDE. That means bringing the main window forward and finding the row. A menu bar (or system tray) icon with the recent projects and worktrees puts those actions one click away from any app. This is the roadmap's "Desktop ergonomics" item.

## What Changes

- While the app runs, an icon in the macOS menu bar or the Windows and Linux system tray opens a menu with: show ShogunAi, new worktree, the recent projects, the recent worktrees, Preferences and Quit.
- Each recent worktree has a submenu with "Show in ShogunAi", "Copy launch command", "Open in <terminal>", "Open in <IDE>" and "Open folder", as available, and its label says when an agent is working in it or it needs the user.
- Recent projects and worktrees are the ones the user selected, created or launched most recently, remembered across restarts.
- Preferences can hide the icon, and can keep the app running in the menu bar or tray when the main window is closed (off by default).

## Capabilities

### New Capabilities
- `tray-menu`: the menu bar or system tray icon, its menu, the recent items it lists and its options.

### Modified Capabilities

## Impact

- `desktopApp`: a `Tray` in `main.kt` built with Compose Desktop's tray API, a recents history in `ShellViewModel`, the close behavior of the main window, Preferences options, tray icons for light and dark menu bars, strings.
- `core`: settings gain `recentProjects`, `recentWorktrees`, `showTrayIcon` and `keepRunningInTray`, read backward compatibly.
- Docs: `docs/guide/desktop-app.md` and `desktop-app.es.md`.
- Builds on `direct-launch` (terminal, IDE and folder actions) and `herdr-agent-status` (agent states), both merged.
