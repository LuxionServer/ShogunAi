# Proposal

## Why

With every provider on, a worktree row stacks up to six lines of small grey text (path, branch with origin and agents, status, size and ports, emulator, simulator) next to two outlined buttons that get cut on narrow windows. The full path is the main label and the branch is secondary, the opposite of how users recognize their work. Emulator and simulator lines appear on every row, even when nothing is running. This is the roadmap's "Worktree screen redesign" (`docs/roadmap.md`), solved inside the epic's layout: a list where each worktree is one scannable line, and a detail pane for everything about the selected worktree.

## What Changes

- The worktree view becomes a list and a detail pane side by side. **BREAKING** (UI): rows lose their buttons and extra lines.
- Each row is one line: a status indicator, the branch as the main label (monospaced), the folder name and origin as secondary text, and compact chips only for states that matter (agent running, uncommitted changes, ahead/behind, merged, inactive, locked, folder missing, device running), plus the last commit time. The main worktree is pinned first and marked.
- Selecting a row (click or arrow keys) shows its detail: branch and full path (selectable, with copy), origin, full status, running agents, size, reserved ports, emulator and simulator controls (only for repositories that declare the provider), and the actions: copy launch command (primary) and delete (danger, not for the main worktree).
- Row hover shows a copy-launch-command icon. A context menu offers copy launch command, copy path and delete. Keyboard: arrows move the selection, Cmd/Ctrl+Shift+C copies the launch command and Cmd/Ctrl+Backspace asks to delete.
- With nothing selected, the detail pane shows the project's summary: worktree count, total size and cleanup candidates.
- On narrow windows the detail pane opens over the list and closes with Escape.
- The project view's header gets "Project settings", the roadmap's "direct way to edit the project from its worktree screen".
- The list is a lazy list keyed by worktree path. The selection survives auto refresh.

## Capabilities

### New Capabilities

- `worktree-detail`: selecting a worktree, the detail pane's content and actions, the empty state with the project summary, keyboard navigation and shortcuts, and the overlay layout on narrow windows.

### Modified Capabilities

- `worktree-workspace`: rows show the branch as the main label and the folder as secondary information, the main worktree is pinned first, removal is started from the detail, the context menu or the keyboard, and the header offers the project's settings.
- `worktree-overview`: origin, size and ports move to the detail. Agents, inactivity and running devices show as row indicators with full information in the detail. Emulator and simulator controls live in the detail.
- `worktree-status`: rows show a compact status, and the detail shows the full status.
- `worktree-terminal-launch`: the launch command is copied from the detail, the row's hover action, the context menu or the keyboard.

## Impact

- `ui/worktree/`: `WorktreeScreen` split into `WorktreeListPane`, `WorktreeRow`, `WorktreeDetailPane` and `ProjectSummary`. `WorktreeStatusLine`, `WorktreeOverviewLines`, `EmulatorLine` and `SimulatorLine` are reshaped into row chips and detail sections.
- `WorktreeViewModel`: `selectedWorktreePath`, selection kept across reloads, per-worktree action errors for the detail.
- The Gradle daemons and DerivedData sections stay below the list until `machine-resources-view` moves them out of the project view.
- Docs: `docs/architecture/ui.md` (worktree view sections) in both languages, and README screenshots if any.
