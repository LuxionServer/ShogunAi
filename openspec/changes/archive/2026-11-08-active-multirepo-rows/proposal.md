# Proposal

## Why

The UX review of the v2 interface found that "Active" doesn't answer "what needs me?" at a glance for multirepo work: a change that spans three repositories shows up as three rows, often in three different state groups, each repeating the same branch and a `TASK-12 · +api +docs` tag. The user has to put the change back together in their head to know whether it needs them. It's the first fix of the review in the roadmap (item 2).

## What Changes

- "Active", when grouped by state, lists each group of a multirepo (the same branch in two or more of its repositories) as one change row instead of one row per worktree.
- The change row sits in the state group of the change's combined state, the same one its group header shows in the multirepo view: the most urgent state among its worktrees, or "Merged" only when all are merged. It shows the change's title, its branch and repositories, the agents of all its worktrees, and the group's actions ("Open all in <default runner>", "Open all with…", "Add repository", "Clean up group") plus "Show in multirepo".
- The change row is collapsed by default and expands into one child row per worktree of the group, one per repository, each with its alias, its own state and the usual row details. Child rows don't repeat the branch: their name shows the repository's project instead, and they carry no group tag.
- A change row is listed while its combined state is active, or while one of its worktrees is selected; it counts as one item in its state group's count. Text filtering keeps change rows whose worktrees match, expanded, with only the matching child rows.
- Selecting one of the change's worktrees from elsewhere (palette, menu bar, keyboard) expands its change row.
- "Add repository" and "Clean up group" work for a group outside its multirepo's view, since they now act on the group's own multirepo.
- Unchanged: "Active" grouped by project, "All worktrees" and project views keep one row per worktree with the group tag; the multirepo view keeps its change groups.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-views`: "Active" grouped by state lists multirepo changes as expandable change rows.
- `multirepo-desktop`: the group tag is no longer shown where the change row replaces it, and the group's actions are offered from the change row too.

## Impact

- Desktop app only: `WorktreeBoard` (state groups of "Active", selection, visible rows, group actions resolved from the group's multirepo), `ChangeGrouping` (combined activity, group's multirepo), `WorktreeListPane` (change and child list items), a new change row composable, `WorktreeRowModel`/`WorktreeRow` (child rows without the branch) and the strings for "Show in multirepo" in English and Spanish.
- No change to `core`, the CLI or the stored preferences.
- Shares `WorktreeListPane` and `WorktreeRow` with the parallel desktop changes; conflicts are resolved when catching up with `develop`.
