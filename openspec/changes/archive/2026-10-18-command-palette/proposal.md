# Proposal

## Why

With several projects and dozens of worktrees, getting to one worktree means finding its project in the sidebar or filtering a view, and running an action on it means finding the action in the detail. The roadmap asked for a command palette (`Cmd/Ctrl+K`) to jump to any project or worktree. The epic removed project search from the UI on the promise that the palette would cover it (`app-shell-layout`). This is the epic's last change: one keyboard entry point to every view, project, worktree and frequent action.

## What Changes

- Cmd/Ctrl+K opens a command palette from anywhere in the main window. It searches views (Active, All worktrees, each machine resource, Preferences), projects, worktrees of every project, and actions.
- Matching ignores case and accepts the typed letters in order with gaps. Results are ranked by how well they match: exact or prefix matches first, then word starts, then the rest. With an empty query, the palette lists recently used items.
- Each result shows its kind, its context (a worktree's project, for example) and its shortcut when it has one.
- Enter runs the result's main action: open a view, select a worktree, open a project. For worktrees, Cmd/Ctrl+Enter copies the launch command. Projects offer "New worktree in…" and "Settings of…" as separate results.
- The palette never runs a destructive action. Deleting stays behind its confirmation.

## Capabilities

### New Capabilities

- `command-palette`: opening the palette, what it searches, how results are matched, ranked and shown, how they're run from the keyboard, recent items, and the exclusion of destructive actions.

### Modified Capabilities

None.

## Impact

- New `ui/palette/` (palette overlay, result list) and a `PaletteIndex` built from the shell's catalog, `WorktreeBoard` rows and `MachineResources` entries.
- A pure matcher and ranker with unit tests. Recent items stored in `settings.json` (`recentPaletteItems`, at most 20).
- View > Command palette in the menu. Docs: `docs/architecture/ui.md` in both languages, and the keyboard shortcuts listed in the README.
