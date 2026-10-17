# Proposal

## Why

ShogunAi's goal is to be a control tower for worktrees, but it still shows one project at a time: knowing how every agent is doing means opening each project in turn. The epic's direction (`docs/roadmap.md`, "Desktop app redesign") puts every worktree of every project in one view, where a project is just a filter for focusing on it. This change adds that "All worktrees" view and turns the project view into the same view filtered to one project. `active-worktrees-view` then builds the default "Active" view on the same foundation.

## What Changes

- A new "All worktrees" entry in the sidebar's views section shows the worktrees of every saved project, grouped by project, with the list and detail of `worktree-list-detail`.
- Selecting a project in the sidebar shows the same view filtered to that project. A project filter in the view's header switches between "All projects" and one project, in sync with the sidebar.
- A text filter (Cmd/Ctrl+F) narrows the list by branch, folder or task id.
- Each project group has a header with its name, counts, a collapse toggle (remembered) and the project's actions: new worktree, clean up and project settings.
- Projects load independently and in parallel with a bound. A project whose repository can't be read shows its error in its group without blocking the others.
- "New worktree" from the unfiltered view asks for the project in the dialog, defaulting to the last project used.
- "Clean up" from the unfiltered view covers every project. Its dialog groups the candidates by project and shows provider options for the projects that declare them.
- Auto refresh watches every project shown in the current view, and detects running agents once per check for all of them.

## Capabilities

### New Capabilities

- `worktree-views`: the cross-project worktree views: the "All worktrees" view, project and text filters, project groups with their actions, independent project loading, and creating a worktree from a view that spans several projects.

### Modified Capabilities

- `worktree-auto-refresh`: watches every project shown in the current view instead of one project's screen, with a single agent detection per check.
- `worktree-cleanup`: "Clean up" works over the projects shown in the current view, and its confirmation groups the candidates by project.
- `app-shell`: the sidebar's views section gains the "All worktrees" entry, and project entries become filters of that view.

## Impact

- `WorktreeViewModel` is split: the per-project state and actions become a `ProjectWorktrees` state holder, and a shell-scoped `WorktreeBoard` owns one per project, their loading, auto refresh and agent detection.
- `ui/worktree/`: the list pane renders project groups, and the header gets the project and text filters. `CreateWorktreeDialog` gets a project selector. `CleanupDialog` groups by project.
- `ShellDestination.Project` is replaced by `All(projectFilter)`. `lastDestination` values saved by `app-shell-layout` are migrated.
- Core: `DetectRunningAgentsUseCase` is called once with the worktrees of all shown projects.
- Docs: `docs/architecture/ui.md` (worktree views, auto refresh) in both languages.
