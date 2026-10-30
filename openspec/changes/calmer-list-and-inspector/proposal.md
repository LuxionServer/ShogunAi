# Proposal

## Why

The UX review of the v2 interface found the worktree list and its inspector noisy: an inspector column is always open even with nothing selected, an empty execution column takes 176 dp, every row repeats "ShogunAi" and the main checkout reads "project · project", sections repeat in their label what their content already shows, and narrow windows cut the task id before the branch's type. This change makes the list and the inspector show only what tells worktrees apart or needs attention.

## What Changes

- No inspector without a selection: the list takes the whole width, and the view's summary (worktrees, total size, cleanup candidates) moves to one line in the view's header. The inspector docks beside the list when the content is at least 980 dp wide, and opens over the list below that (the cut is 760 dp today).
- The row's execution column is left out of every row of a view when no row of that view has an agent, a device, a desktop turn or a running headless app.
- The origin chip in the inspector's header shows only when the origin isn't ShogunAi.
- The main checkout shows as its branch with a neutral "main" chip in its row and its inspector header; its row no longer shows its folder, and the time cell no longer writes "main". "Locked" becomes a lock icon with a tooltip.
- The inspector's path is shown relative to the project's worktrees folder (or to the home folder for a path outside it), on one line with an ellipsis in the middle; the full path goes in its tooltip and in copy.
- An inspector section's summary shows only while the section is collapsed. Provisioning is drawn only when a provisioned file is stale or the check failed.
- The Agent section is one line when no agent runs, and shows the running agents apart from a "Runs with" line naming the agent and runner the worktree opens with.
- On narrow rows the branch's type prefix (`feature/`) is truncated before the rest, so the task id always shows. Sidebar rail tiles show two letters that tell sibling projects apart (AP and WE for `api` and `web`).
- Counts inside sentences ("3 worktrees · 1.2 GB", "2 ports · 5 vars") use the interface font with tabular figures; lone figures stay in the monospaced data font.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-detail`: the summary leaves the detail pane for the view's header; the docking threshold is 980 dp; the header shows the origin only when it isn't ShogunAi, the main chip, and a relative path; Provisioning is drawn only when stale or failed; the Agent section's layout.
- `worktree-views`: the list takes the whole width without a selection, and the view's summary moves to its header.
- `inspector-sections`: a section's summary shows only while it is collapsed, and a section can be drawn only when it needs attention.
- `worktree-row`: the execution column collapses when unused; the main checkout's name cell and the time cell's marks; the branch's type prefix truncates first.
- `app-shell`: the rail's tiles use two distinguishing letters.
- `visual-theme`: counts inside sentences use the interface font with tabular figures.

## Impact

- Desktop app only (`desktopApp`): `WorktreeScreen` (content layout and header summary), `WorktreeListPane` and `WorktreeRow`/`WorktreeRowModel` (execution column, main chip, lock icon, branch truncation), the inspector (`InspectorHeader`, `AgentSection`, `ProvisioningSection`, `InspectorSection`), `Sidebar` (rail tiles), the theme's type styles, `ShogunLayout`, and the English and Spanish strings.
- No change to `core` or `cli`.
- Parallel changes touch the same files: the attention model (row and groups), the toolbar tidy (the view header, so the header change here stays one line under the title) and focusing an agent from its row (the row's agent indicator).
