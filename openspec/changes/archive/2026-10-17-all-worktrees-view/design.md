# Design

## Context

After `worktree-list-detail`, one `WorktreeViewModel` per project (created with `viewModel(key = projectId + configHash)`) owns the list, statuses, overview, providers, selection, dialogs and the 3-second watcher. The shell's `ShellDestination.Project(projectId)` picks which one is shown. `DetectRunningAgentsUseCase` runs `lsof` and attributes processes to the worktree paths it is given. Use cases are built per project by `AppContainer.worktreeUseCases(config)`.

## Goals / Non-Goals

**Goals:**
- One state holder per project that any view can show, so "All", a project filter and later "Active" are just different selections over the same state.
- Watching several projects without multiplying the cost of process inspection.

**Non-Goals:**
- The "Active" view and grouping by state (`active-worktrees-view`).
- Moving the provider sections out of the project groups (`machine-resources-view`).

## Decisions

### `ProjectWorktrees` per project, owned by a shell-scoped `WorktreeBoard`
`WorktreeViewModel`'s per-project logic moves into `ProjectWorktrees`, a plain state holder that takes its `WorktreeUseCases` and a `CoroutineScope`. `WorktreeBoard` is a ViewModel scoped to the shell. It keeps `Map<ProjectId, ProjectWorktrees>`, created and disposed as the catalog changes. A project whose config changed (same id, different hash) is recreated, keeping the guarantee from `worktree-workspace` that the view uses the saved configuration. View-level state (selection, filters, dialogs) moves to a `WorktreeViewState` in the board, because a selection can point at a worktree of any project: `selected: WorktreeRef(projectId, path)`. *Alternative considered:* one `WorktreeViewModel` per project, with a view that combines several. Rejected because selection, dialogs and auto refresh would be split across holders that then need to coordinate.

### Loading with two bounds
Projects load with at most 2 lists in parallel. Within each project, statuses stay at 4 at a time, as today. Sizes are measured through one board-wide sequential queue (`SizeQueue`) that prioritizes the projects currently shown, because `du`-style walks are the expensive part. A project's load state (`Loading`, `Loaded`, `Failed(error)`) is shown in its group: a progress indicator in the header while it reloads, and the failure with a reload action below it. A list failure is kept apart from action errors (`listFailure` vs `error`). `ListWorktreesUseCase` optionally takes a `FileManager` to report a missing base repository as `BaseRepositoryNotFound` instead of a failed process start.

### One watcher tick for all shown projects
The board runs a single 3-second ticker while a worktree view is visible. On each tick it reads the fingerprint of every shown project that is idle (not loading, no action running) and reloads only the ones that changed. It then calls `DetectRunningAgentsUseCase` once, with an `AgentSearch` (worktrees and agent command) per unchanged project, and distributes the result; a project's own agent command is only matched in its own worktrees. A project whose fingerprint changed reloads instead, which detects its agents as part of the load. Emulator, simulator and Gradle states are refreshed per project that declares the provider, as today.

### Destinations and migration
`ShellDestination.Project(id)` becomes `All(projectFilter: ProjectId?)`. The sidebar marks "All worktrees" when the filter is null and the project when it's set. `app-shell-layout` saved a project as `lastDestination = "project:<id>"`; that value keeps its meaning and is read as `All(id)`, and the unfiltered view is saved as `all`. With no saved view, or a deleted project, the app opens "All worktrees".

### Group rendering
In the unfiltered view the `LazyColumn` uses sticky group headers (`stickyHeader`) with the project's name, count, load state and actions. In the filtered view the single group's header is merged into the view's header, so a project view looks like `worktree-list-detail` built it. The detail pane shows the project's name above the branch when the view spans several projects. Collapsed groups are stored in `settings.json` as `collapsedProjects`.

### Text filter
A `TextField` in the header, focused with Cmd/Ctrl+F (a menu item), filters on `branch` and the folder name, case-insensitive. Filtering is a pure function over the board's rows, tested without Compose. Escape clears it before clearing the selection; it's handled by the main window's key handler, because on macOS the focused field never sees Escape's key-down. The filters sit in a second header row so the title isn't truncated on narrow windows.

### Creating from the unfiltered view
`CreateWorktreeDialog` gets an optional project selector (a `DropdownSelector`), shown when the dialog is opened without a project. The last project used is stored as `lastCreateProject` in `settings.json`.

## Risks / Trade-offs

- [Many projects make the 3-second tick heavier] → Fingerprints are cheap file stats, and agent detection runs once. If the tick takes longer than its interval, the next one is skipped instead of piling up.
- [Splitting `WorktreeViewModel` is a large refactor of a well-tested class] → The tests move with the logic into `ProjectWorktreesTest` before any UI change, so the refactor is checked on its own (tasks group 1).
- [A selection referring to another project's worktree can survive that project's removal from the catalog] → The board clears selections whose project is disposed.
