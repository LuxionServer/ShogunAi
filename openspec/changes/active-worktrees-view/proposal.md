# Proposal

## Why

"All worktrees" shows everything, but most worktrees at any moment are finished, merged or abandoned. What the user checks all day is narrower: which agents are working, which worktrees have work waiting for them, and what is still in progress. The epic's direction (`docs/roadmap.md`, "Desktop app redesign") makes that the app's main view: "Active", the first entry of the sidebar and the default view. Grouping by state also turns cleanup into a natural action: the merged worktrees form their own group with its "Clean up" action.

## What Changes

- A new "Active" view, first in the sidebar and the default view on a first launch. It lists, across every project, the worktrees with an agent running, with uncommitted changes, or that are still in progress (not merged, not inactive, folder present).
- "Active" is grouped by state: "Agent working", "Needs you" (uncommitted changes or unpushed commits, no agent) and "In progress". Each row shows its project's name.
- A worktree stays in "Active" while it's selected, so it doesn't disappear under the cursor when it stops being active.
- "All worktrees" can also be grouped by state, adding the "Merged", "Inactive" and "Folder missing" groups. The "Merged" group header offers "Clean up". Each view remembers its grouping (state or project).
- The sidebar shows the number of active worktrees on "Active" and, on each project, its number of active worktrees with an indicator when an agent is running in it.
- An empty state for "Active" when nothing is active, with actions to create a worktree or see all.
- Cmd/Ctrl+1 opens "Active" and Cmd/Ctrl+2 opens "All worktrees".

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-views`: adds the "Active" view, its definition of an active worktree, grouping by state with a remembered choice per view, and its empty state.
- `app-shell`: "Active" is the first and default view, the sidebar shows active counts and agent indicators, and views have number shortcuts.
- `worktree-cleanup`: the "Merged" group offers cleanup of its worktrees.

## Impact

- `WorktreeBoard`: a pure classification `worktreeActivity(row)` and grouping functions. Each view's grouping is stored in `settings.json`.
- `ShellDestination.Active`, default when no destination is saved.
- `ui/worktree/`: group headers for state groups. `ui/shell/`: counts and indicators in the sidebar.
- Docs: `docs/architecture/ui.md` (Active view, state groups) in both languages. `README.md`/`README.es.md` describe the main view.
