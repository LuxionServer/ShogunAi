# Proposal

## Why

A UX review of the v2 interface (on `develop` at `aaf42f94`, with sample data) found that "Active" doesn't answer "what needs me?" at a glance. "Needs you" mixes an agent waiting for the user with Git work left uncommitted or unpushed, and the classification runs before checking for the main checkout or inactivity, so 5 of the review's 7 active rows sat in "Needs you" and the sidebar's count was always high. "↑" also means two things: ahead of the upstream when there is one, ahead of the base when there isn't.

## What Changes

- **BREAKING (UI vocabulary)** "Needs you" splits into two states and groups:
  - "Waiting for you": an agent is blocked or done. Its group header uses the warning color.
  - "Unfinished": no agent is deciding and there are uncommitted changes, commits not pushed to the upstream, or commits of its own on a branch without an upstream. Its header is neutral.
- The main checkout never goes under "Unfinished": it stays in "Other" (not active) and its row keeps its ✎ mark. A worktree whose own work is older than the inactivity threshold, with no agent, is "Inactive" even when it has uncommitted changes or unpushed commits, and keeps its ✎ and ↑ marks. An agent that is blocked or done still puts any worktree, the main checkout included, under "Waiting for you".
- The sidebar's counts (the "Active" view and each project and multirepo folder) count only worktrees "Waiting for you".
- State groups can be collapsed from their header, and in "Active" the "Stopped" group starts collapsed.
- The row's "↑" means only commits not pushed to the upstream (with "↓" for commits behind it). A branch with commits of its own and no upstream shows a "not pushed" icon instead. The comparison with the base leaves the row and stays in the inspector, whose Status section also says when the branch isn't pushed.
- When nothing is "Waiting for you", "Active" says so calmly: "Nothing needs you", how many agents are working and "See all worktrees", above the remaining groups or alone when the list is empty. It replaces "Nothing is active".
- The tray menu's "!" mark, Orca's board status and comment, the guided tour's sample and the docs follow the new states.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-views`: the states and their order, what counts as active, groups by state (two new groups, warning and neutral headers, collapsible groups, "Stopped" collapsed in "Active"), and the calm empty state replacing "Nothing active".
- `worktree-overview`: inactivity no longer requires a clean worktree; the scenarios that name "Needs you" now name "Waiting for you".
- `worktree-row`: the state badge's two new states, and the Git cell's second line (upstream divergence, "not pushed" icon, no base comparison).
- `worktree-detail`: the Status section says when the branch has no upstream.
- `app-shell`: the sidebar's counts count only worktrees waiting for the user.
- `tray-menu`: the "!" mark is for worktrees waiting for the user.
- `multirepo-desktop`: the combined state's scenario names "Waiting for you".
- `first-run-tour`: the sample's worktree with uncommitted changes shows under "Unfinished".

## Impact

- `core`: `Activity` (`NeedsYou` → `WaitingForYou` and `Unfinished`), `worktreeActivity`, `isInactive`, `orcaBoardStatus`/`orcaComment`, the tour's sample and script.
- `desktopApp`: `WorktreeGrouping`, `WorktreeRowModel`/`WorktreeRow` (badge, Git cell), `WorktreeListPane` (headers, collapsing), `WorktreeBoard` (counts, collapsed states, calm state), `WorktreeScreen` (calm notice), `inspector/InspectorModels`, `TrayMenuModel`, `Sidebar`, `Strings` (English and Spanish).
- `cli`: none beyond the shared `Activity` (Orca sync).
- Docs: `docs/guide/desktop-app.md`, `docs/architecture/ui.md`, `docs/architecture/domain.md`, `docs/agents.md`, `docs/process/clean-account-rehearsal.md` and their Spanish translations; the roadmap.
- The multirepo group row that follows this change builds on these groups.
