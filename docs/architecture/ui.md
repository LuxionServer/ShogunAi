# UI layer

Lives in the `desktopApp` module: `desktopApp/src/main/kotlin/app/luxion/shogunai/ui`, plus `AppContainer.kt` at the package root. It uses the domain and infrastructure from the `core` module.

## Navigation

No `navigation-compose`: the main window is a shell (`ui/shell/AppShell`) with a persistent `Sidebar` and a content area. `ShellViewModel` owns the selected `ShellDestination` (`Active`, `All(projectFilter)`, `Machine(resource)`, `ProjectSettings`, `AddProject`), the project catalog (sorted alphabetically) and the sidebar state, and the content area does a `when` over the destination. There are two worktree views: `Active` is the main one and the default when nothing was saved, `All(null)` is "All worktrees", and selecting a project is the second view filtered to it (`All(projectId)`). A sidebar app has no meaningful back stack: Cancel on a form returns to the destination selected before it, and saving selects the view filtered to the saved project. With no destination (no projects), the content area shows the first-launch empty state.

The selected view is saved as `lastDestination` in `settings.json` (`active`, `all`, `project:<id>` for a filtered view, `machine:<RESOURCE>` for a machine view, the same value earlier versions wrote for a project) and restored on startup; forms are never restored, and a project that no longer exists falls back to "Active". The catalog is re-read when the window regains focus and on View > Reload, which also bumps `reloadRequests` so the shown view reloads its data.

The sidebar starts with a views section, whose "Active" entry shows the number of active worktrees (`WorktreeBoard.activeCount`) and "All worktrees" the total (`WorktreeBoard.totalWorktreeCount`), followed by the projects and, when some project declares a provider, the "Machine" section (see "Machine resources"). Each project shows its own active count and, while an agent runs in any of its worktrees, a dot in the info color with a tooltip naming the agents. Cmd/Ctrl+K opens the command palette (see "Command palette"). Cmd/Ctrl+1 opens "Active" and Cmd/Ctrl+2 "All worktrees" (View menu). It's 240 dp wide, and becomes a 64 dp rail (an icon for each view, each project's initial and an icon for each machine entry, with tooltips) when the window is narrower than 900 dp or the user collapses it (View > Toggle sidebar, Cmd/Ctrl+Backslash). The manual choice is saved as `sidebarCollapsed` and only applies to wide windows; toggling on a narrow window expands it until the width changes. Each project row has a context menu with Edit project and Remove project.

Success messages are transient and don't move the layout: the shell owns one `AppMessages` (a `SnackbarHostState` shown at the bottom of the content area) and provides it as `LocalAppMessages`. `show(message, action)` replaces the current message, keeps it 4 seconds, or 8 seconds when it has an action (`MessageAction`, e.g. "Copy launch command"), and runs the action when it's activated. Errors stay as `ErrorNotice` where the action happened (see Errors).

`main.kt` adds the desktop conventions around the shell:

- A `MenuBar` (`AppMenuBar`) with File (New worktree while a worktree view is active, Save while project settings have changes, Add project; Preferences and Quit off macOS), View (Find while a worktree view is active, Reload, Toggle sidebar) and Help (About, off macOS), labelled from `Strings` so it follows the language. On macOS, `java.awt.Desktop` handlers put About and Settings… in the ShogunAi menu, and a quit handler cancels the system quit and goes through the `LeaveGuard`, which saves the window bounds when it really quits.
- `PreferencesWindow` (theme mode and language, a single instance brought to the front when reopened) and `AboutWindow` (icon, name, `BuildInfo.VERSION`, repository link). Every window reads the same `Appearance` holder, so a change applies to all of them at once.
- The window's bounds and maximized state are saved as `windowBounds` when it closes and restored on startup; bounds that don't intersect any connected display open the window centered instead. The minimum size is 720×480.

## Screens and ViewModels

| Screen | ViewModel | Responsibility |
|---|---|---|
| `AppShell` | `ShellViewModel` | Sidebar with the project catalog from `ProjectRepository` (alphabetical; add, edit and remove with confirmation) and the content area for the selected destination. |
| `ProjectConfigScreen` | `ProjectConfigViewModel` | Form for a `Project` (name, base repository path, worktrees root, main branch, the repository's provisioning, local files, agent command and Headroom switch); validates required fields and asks before discarding unsaved changes. |
| `WorktreeScreen` | `WorktreeBoard` | Lists the worktrees of every project, or of the project in the filter, grouped by project as one-line rows with a detail pane for the selected one; creates them from a new or an existing branch, removes them (optionally deleting the branch, retrying with `--force` when Git asks for it), cleans them up and copies their launch command. |

Each ViewModel uses `androidx.lifecycle` viewmodel-compose (`viewModelScope`) to call use cases without blocking the UI thread. Lists are reloaded when the window regains focus, so changes made outside the app (a branch created from the terminal, a worktree removed by hand) show up without pressing "Reload". `ProjectConfigViewModel` lives in a `ViewModelStore` of its own for each visit of the configuration screen (`rememberScreenViewModelStoreOwner`), cleared when the screen leaves the composition, so every visit starts from the saved project and rereads the repository.

### Worktree board

`WorktreeBoard` is a ViewModel scoped to the shell, created in `AppShell` and fed with the catalog through `syncCatalog(projects)`. It keeps one `ProjectWorktrees` per project: a plain state holder with that project's list, statuses, overview, providers, creation, removal and cleanup, bound to a `CoroutineScope` that is a child of the board's. A project whose configuration changed (same id, different `ProjectConfig`) is disposed and recreated, so the view never uses use cases bound to old paths, and a project removed from the catalog is disposed, clearing a selection, filter or dialog that pointed at it. Renaming a project keeps its holder.

View-level state lives in the board, because it can point at any project: the project filter (`show(projectFilter)`, set from the destination), the text filter, the selection as a `WorktreeRef(projectId, path)`, the collapsed groups, and the create and cleanup dialogs.

- **Loading**: each holder lists its worktrees on creation, with at most 2 lists in parallel across projects (a `Semaphore` shared by the board); statuses stay at 4 at a time within a project. A project's `loadState` is `Loading` until its first list, then `Loaded` or `Failed(error)`. A failure is shown inside its group with a reload action and never blocks the other groups; `ListWorktreesUseCase` reports a missing base repository as `BaseRepositoryNotFound` without running Git.
- **Sizes**: worktree sizes are measured through one board-wide `SizeQueue`, one worktree at a time. A new load replaces that project's pending measurements, and the queue measures the projects shown in the view first.
- **Groups**: `groups` is the list of shown projects with their rows after the text filter. In the unfiltered view each group has a sticky header (`stickyHeader`) with a collapse toggle, the name, the worktree count, a progress indicator while it reloads, and "Clean up", "Project settings" and "New worktree" for that project. Collapsed groups are saved as `collapsedProjects` in `settings.json`. In the filtered view the single group's header is merged into the view's header, so a project view looks as before.
- **Text filter**: a field in the header, focused with View > Find (Cmd/Ctrl+F), keeps the worktrees whose branch or folder name contains the text, ignoring case (`filterWorktrees`, a pure function). Groups without matches are hidden, the view says when nothing matches with an action to clear the filter, and Escape clears it before clearing the selection (`WorktreeBoard.escape()`, called from the main window's `onPreviewKeyEvent`, see the shortcuts below).

### Creating worktrees

Worktrees are created in `CreateWorktreeDialog`, opened from the "New worktree" action in the view's header or a group's header, from File > New worktree or with Cmd/Ctrl+N (`onNewItemShortcut` on the view, and `ShellViewModel.newWorktreeRequests` for the menu item, which only exists while a worktree view is active). `WorktreeBoard` owns the dialog's state: the target project (`createProjectId`, which also means the dialog is open), whether to ask for it (`createAsksForProject`) and the form fields (`taskId`, `branchType`, `createMode`, `selectedBranch`); the project's `ProjectWorktrees` keeps `isCreating`, `createError`, `localBranches` and `lastCreatedWorktree`. From a group's header or a filtered view the dialog creates in that project. From the unfiltered view it shows a project selector, defaulting to the project of the last worktree created (`lastCreateProject` in `settings.json`, saved on each creation) or the first project. `openCreateDialog()` resets the task id, the selected branch and the error, so the fields are reset on open and never on close. `confirmCreate()` creates only when `canCreate` holds (a valid task id, or an available branch), which is also what the "Create worktree" button and Enter in the task id field check.

While creating, the dialog shows `InlineProgress` and disables its inputs, the mode selector and both actions, and `dismissCreateDialog()` is ignored, so Escape and clicking outside do nothing. A failure keeps the dialog open with the entered values and shows the error as a `Notice` inside it (`createError`, separate from the view's `error`). A success closes the dialog, adds the worktree to the list and sets `lastCreatedWorktree` and `success`, which the screen turns into "Worktree created: <folder>" with a "Copy launch command" action (`copyLastCreatedLaunchCommand()`).

`updateTaskId` applies the normalization (trim + whitespace runs collapsed to `-`) before storing the value, so the field always shows the already-normalized id while typing.

In "Existing branch" mode the branches from `ListLocalBranchesUseCase` are shown in a `DropdownSelector` grouped by the segment before the first `/` (or "other" for branches without one). Branches that already have a worktree are rendered disabled and can't be selected, and "Create worktree" is only enabled for an available branch (`ProjectWorktrees.isBranchAvailable`). Changing the project reloads its branches.

### Worktree list and detail

The view's header shows "Active", "All worktrees" or the filtered project's name and, right-aligned, the reload action, "Project settings" in a filtered view (opens `ShellDestination.ProjectSettings` through `ShellViewModel.editProject`), "Clean up" and "New worktree". A second row holds the project filter ("All projects" and each project, kept in sync with the sidebar through `ShellViewModel.selectFilter`; "Active" always spans every project, so it has none), the text filter and the grouping selector (by state or by project), which each view remembers as `grouping.active` and `grouping.all` in `settings.json`. Below them, `WorktreeScreen` lays out `WorktreeListPane` and, when there's room, a 360 dp detail pane with a vertical divider. `BoxWithConstraints` on the content area decides the layout: below 760 dp of content width the detail is drawn over the list from the right edge, with a scrim, and closing it clears the selection.

- **Rows**: `WorktreeListPane` is a `LazyColumn` keyed by project and worktree path, with each project's main worktree first (`orderedWorktrees`). Each `WorktreeRow` renders a `WorktreeRowModel` built by the pure `worktreeRowModel(...)` (in `WorktreeRowModel.kt`, unit-tested without Compose): a leading `RowIndicator` dot with the most urgent state (folder missing > agent running > uncommitted changes > merged > inactive), the branch in the data family as the main label, the folder name and origin as secondary text, `RowChip`s in a fixed order (main, folder missing, status unavailable, agents, changes, divergence from the main branch, merged, inactive, locked, a device only while booting or running) and the last commit time right-aligned. Hovering a row shows a copy-launch-command `IconAction`; its context menu offers copy launch command, open in Herdr, copy path and, except for the main worktree, delete.
- **Selection**: `WorktreeBoard.selected` is a `WorktreeRef` set with `select(ref)` and the up and down arrows (`selectNext()`/`selectPrevious()`, which cross project groups, skip collapsed ones and stop at the ends), and `animateScrollToItem` keeps the selected row visible. `selectedWorktree` resolves it against the shown projects' lists, so a worktree that's no longer listed isn't shown as selected and the selection survives auto refresh.
- **Detail**: `WorktreeDetailPane` shows the project's name when the view spans several projects, the branch with its origin chip, the full path as selectable text with a copy action, the actions ("Copy launch command", "Open in Herdr", "Delete worktree" for non-main worktrees), and the Status, Agents, Environment, Devices and Disk sections, each omitted when it has nothing to show. Failures of that worktree's actions (emulator, simulator, copy, Herdr) are kept in `actionErrors[path]` and shown as a `Notice` inside the detail, not in the view's error banner.
- **Summary**: with nothing selected, `ProjectSummary` shows the worktree count of the shown projects, their total size, how many can be cleaned up and the "Clean up" action.
- **Shortcuts**: while a worktree view is active, `AppShell` hands the board's actions to `ShellViewModel.worktreeActions` (find, copy, delete, clear selection), and `AppMenuBar` shows View > Find and a Worktree menu with Cmd/Ctrl+Shift+C (copy the selected worktree's launch command), Cmd/Ctrl+Backspace (ask to delete it, ignored for the main worktree or while a dialog is open) and "Clear selection". Escape clears the text filter, or else the selection, through the main window's `onPreviewKeyEvent` instead of a menu accelerator: on macOS the window only receives Escape's key-up, so neither an accelerator nor a handler on the focused node sees it. The handler doesn't consume the event.

### Command palette

Cmd/Ctrl+K (View > Command palette) opens `CommandPalette` over the main window from any view, with the search field focused; Escape or a click outside closes it without running anything. `ShellViewModel.isPaletteOpen` holds its state and `AppShell` builds its index when it opens.

- **Index**: `buildPaletteIndex(projects, worktrees, machineResources, canCleanUp, strings, shortcutLabel)` is a pure function that returns `PaletteItem`s of four kinds: views (Active, All worktrees, each visible machine resource, Preferences), projects, the worktrees of every project (`WorktreeBoard.paletteWorktrees`, with their state indicator) and actions (Add project, "New worktree in <project>", "Settings of <project>", Clean up while there is something to clean, Reload, Toggle sidebar). Each item has a stable id, search keys, a primary action and, for worktrees, a secondary one (copy the launch command). No item deletes or removes anything.
- **Matching and ranking** (`PaletteMatcher.kt`, pure and unit-tested): `matchScore(query, key)` matches the typed letters in order, ignoring case and allowing gaps. An exact match scores above a prefix, which scores above a match with letters at word starts (after `/`, `-`, `_`, a space or a case change), which scores above any other match; ties inside a tier favor contiguous letters and fewer gaps. An item's score is its best key; the project of a worktree is a weak key, so a worktree ranks below the project itself. Remaining ties go to the most recently used item, then alphabetically.
- **Recent items**: running a result records its id in `recentPaletteItems` of `settings.json` (unique, most recent first, at most 20). With an empty query the palette lists them, dropping the ones that no longer exist, or the views before anything has been run.
- **Running a result**: Up and Down move the highlight, Enter runs the primary action and Cmd/Ctrl+Enter the secondary one. Navigation goes through `ShellViewModel.navigate(destination, andThen)`, which uses `requestLeave`, so unsaved project settings still ask before being discarded and the follow-up (select the worktree, open the create or clean up dialog) only runs once the user has confirmed. Selecting a worktree opens "All worktrees" with it selected, clearing the text filter and expanding its project if needed.

### Worktree status

Each row shows a compact status and the detail pane the full one. `ProjectWorktrees` keeps it in `statuses`, a map from worktree path to `WorktreeStatusState` (`Loading`, `Loaded` or `Unavailable`):

- Once the list is read, the main branch is resolved with `ResolveMainBranchUseCase` (exposed as `mainBranch`) and every worktree's status is read with `ReadWorktreeStatusUseCase`, at most 4 at a time. The list shows right away and each row shows a small progress indicator until its status arrives.
- A worktree whose status can't be read shows "Status unavailable" without affecting the others or the screen's error. A worktree whose folder is gone (`isPrunable`) shows "Folder missing" instead, and a locked one (`isLocked`, e.g. in use by an agent session) shows a neutral "locked" chip next to its branch.
- Reloading the list, including when the window regains focus, reads every status again; results from an older load are dropped. Creating a worktree reads only the new one's status, and removing one drops its status.

The row shows uncommitted changes as a warning chip `✎N`, the divergence from the main branch as `↑N ↓N <main branch>` and a merged branch as a "merged into <main branch>" success chip instead of that divergence. The detail's Status section lists the changes, the merged chip and the segments from `statusSegments` (in `WorktreeDetailPane.kt`). The segments are `↑N ↓N upstream`, `↑N ↓N <main branch>` and the last commit date as relative time (`relativeTime`, in `RelativeTime.kt`).

### Auto refresh

While `WorktreeScreen` is shown, a `DisposableEffect` calls `WorktreeBoard.startWatching()`, and `stopWatching()` when it leaves the composition. The board runs a single 3-second ticker for every project in the catalog, so the sidebar's counts and agent indicators stay current whichever view is open; if a check is still running when the next one is due, that one is skipped instead of piling up. On each check, every project that is idle (not loading, no create, remove or cleanup running) reads `ReadGitMetadataFingerprintUseCase` through `ProjectWorktrees.checkFingerprint()`: a project whose fingerprint changed reloads, and only that project. For the unchanged ones the board calls `DetectRunningAgentsUseCase` once with an `AgentSearch` per project (its worktrees and its agent command), so processes are inspected once and each agent is attributed to its worktree in any project; then each of them refreshes its emulators and simulators. `refresh()` stores the fingerprint it starts from, so a reload triggered by the app itself isn't repeated on the next check. Focus and manual reloads reload every shown project.

### Worktree overview

`WorktreeScreen` receives the project's `ProjectConfig` to classify each row's origin with `classifyOrigin`, together with Herdr's worktrees folder, which `ProjectWorktrees` reads on each list load (`herdrWorktreesRoot`, through `WorktreeUseCases.herdr`). After each list load, `ProjectWorktrees` also runs `DetectRunningAgentsUseCase` once for its worktrees (`agents`, `null` when processes can't be listed) and queues every worktree's size measurement with `MeasureWorktreeSizeUseCase` in the board's `SizeQueue` (`sizes`, added up in `totalSize`); stale results are dropped with `listGeneration`. It also reads each worktree's reserved ports (`ports`, through `WorktreeUseCases.readPorts`, which resolves the environment with `reserve = false`, so opening the screen never reserves a port) and the detail's Environment section shows them with `portsLabel` (`PORT 20001 · api 20002`).

- The row shows the origin (ShogunAi, Claude Code, Herdr or other location) in muted text after the folder name, each running agent as an info chip, and an "inactive" chip when `isInactive` holds; the detail repeats the origin as a chip and names the agents in its Agents section.
- The detail's Disk section shows the size (`formatSize`, binary units with one decimal), and the project summary shows "N worktrees · <total size>".

### Cleaning up worktrees

Each project's `cleanupCandidates` come from `selectCleanupCandidates` over its listed worktrees and their loaded statuses. The "Clean up" action in the view's header covers the projects shown in the view, and the one in a group's header covers that project; each is enabled when some project in its scope can clean up (`WorktreeBoard.canCleanUp(projectId)`). It opens `CleanupDialog` over that scope (`cleanupScope`), which lists the candidates by folder name and branch under a heading per project (no heading in a filtered view) and offers "Also delete their local branches", unchecked by default. Confirming runs `ProjectWorktrees.cleanUp(options)` for each project in scope, one after the other (`CleanUpWorktreesUseCase`), shows a progress indicator with the dialog's actions disabled, then closes the dialog, shows one "N worktrees cleaned up" message with the totals of every project (`cleanupSuccess`) and reloads them. Failures and kept branches stay visible in each project's group in `CleanupIssues` until dismissed. When the dialog opens, each project in scope looks up which of its candidates are open in Herdr (`openInHerdrPaths`, through `WorktreeUseCases.herdr.openWorkspaces`, which is empty when Herdr isn't available) without blocking the dialog; those candidates are marked "open in Herdr" and a notice says their workspace, and the agent in it, will be closed. Workspaces that fail to close are listed in the project's `herdrCloseFailures` and shown in its group by `HerdrCloseFailures`.

Provider options follow the projects in scope: each one is offered when at least one of them declares its provider, and applies only to the worktrees of the projects that declare it.

When a project declares the Gradle provider, the dialog also offers "Also stop the idle Gradle daemons of the worktrees being removed", checked by default (`cleanupStopGradleDaemons`): stopping an idle daemon only costs a restart on the next build. Before cleaning up, `cleanUp` lists the daemons with the worktrees as they were; after the removals it stops the idle ones whose last build was in a removed worktree (`idleDaemonsOfRemovedWorktrees`, `StopGradleDaemonsUseCase`). The success message then adds how many daemons were stopped and kept. A daemon that can't be listed or stopped never turns the cleanup into a failure.

### Android emulators

Only for repositories whose `.shogunai.json` declares the emulator provider (`EmulatorUseCases.declared`, read on every list load); otherwise nothing below is shown and no Android tool runs.

- **Worktree row and detail**: `ProjectWorktrees` loads `emulators` (`ReadEmulatorStatesUseCase`, one `adb devices` for all rows, no reservation) after each list load and on each auto-refresh tick without changes. The row shows a device chip while the emulator boots or runs, and the detail's Devices section shows `Emulator · emulator-5556 · running` with Stop, or Start when stopped or not reserved yet. `startEmulator(worktree)` marks the worktree in `startingEmulators` (a progress indicator replaces the button), runs `StartWorktreeEmulatorUseCase` (which reserves the port if needed and waits for the boot), then reloads emulators and ports; errors go to the worktree's `actionErrors`, shown in its detail.
- **Cleanup**: the dialog adds "Also stop the emulators of the worktrees being removed", checked by default (`cleanupStopEmulators`). The serials of the candidates' running emulators are taken from the loaded states before removing (removal deletes their reservations) and stopped after (`stopSerials`); the success message adds how many were stopped.
- **Project configuration**: `EmulatorSettingsSection` shows the "Android emulator" section when `ProjectConfigViewModel.emulatorSection` isn't `Hidden`: the AVD list loaded in the background through `AppContainer.loadAvds` (with "Automatic" first, and a saved AVD the machine lacks marked as missing), the "Run without a window" switch, and the SDK-not-found message with the places checked. The settings are saved in `ProjectConfig.emulator` and count as unsaved changes. The section follows the base repository path as it's edited.

### iOS simulators

Only on macOS and for repositories whose `.shogunai.json` declares the iOS simulator provider (`SimulatorUseCases.declared`, read on every list load); otherwise nothing below is shown and no Xcode tool runs.

- **Worktree row and detail**: `ProjectWorktrees` loads `simulators` (`ReadSimulatorStatesUseCase`, one `simctl list devices` for all rows, nothing created) after each list load. The row shows a device chip while the simulator boots or is booted, and the detail's Devices section shows `iOS simulator · booted` with Stop, or Start when it's shut down or doesn't exist yet. `startSimulator(worktree)` marks the worktree in `startingSimulators`, runs `StartWorktreeSimulatorUseCase` (which creates the simulator if needed, waits for the boot and opens it in Simulator or Device Hub) and reloads the states; errors go to the worktree's `actionErrors`, shown in its detail.
- **Cleanup**: the dialog adds "Also delete the iOS simulators of the worktrees being removed, and orphaned ShogunAi simulators", checked by default (`cleanupDeleteSimulators`), and lists the orphaned ShogunAi simulators (`orphanedSimulators`, loaded with the list) that will be deleted too. "Clean up" is enabled when there are orphans even without candidates (`canCleanUp`). The candidates' UDIDs are taken from the loaded states before removing and deleted after, with the orphans, looked for again after removing (a worktree whose folder was already gone has no readable simulator before, and becomes an orphan once removed); the success message adds how many were deleted.
- **Project configuration**: `SimulatorSettingsSection` shows the "iOS simulator" section when `ProjectConfigViewModel.simulatorSection` isn't `Hidden`: runtime and device dropdowns built from the machine's iOS runtimes (`AppContainer.simulatorSetup`, "Automatic" first, a saved name the machine lacks marked as missing), and, in a Kotlin Multiplatform repository whose KMP tests aren't wired (`KmpTestWiring`), the repository snippet with Copy and "Install for every project on this machine", which installs the Gradle init script only after a confirmation dialog. The settings are saved in `ProjectConfig.simulator` and count as unsaved changes.

### Open in Herdr

The detail of a worktree whose folder exists has an "Open in Herdr" button after "Copy launch command", and its row's context menu has the same action. `openInHerdr(worktree)` marks the worktree in `openingInHerdr` (the button is disabled with a progress indicator meanwhile) and runs `WorktreeUseCases.herdr.open` (`OpenWorktreeInHerdrUseCase`, built in `AppContainer` with `CliHerdrClient`) with that worktree. The success message says the worktree was opened with its agent, or that it was already open and Herdr switched to it (`WorktreeSuccess.OpenedInHerdr`); a missing `herdr` or a failing command goes to the worktree's `actionErrors`, shown in its detail with the link to herdr.dev or Herdr's own message.

Removing a worktree also closes its Herdr workspace: `requestRemoval` looks the worktree up the same way as the cleanup dialog (`pendingRemovalOpenInHerdr`) and the remove dialog adds the notice when it's open; a failing close shows the removal's success together with a `HerdrCloseFailures` warning in the project's group naming the worktree and Herdr's message.

### Machine resources

Gradle daemons, Android emulators, iOS simulators and Xcode DerivedData belong to the machine, not to a project, so they have their own views in the sidebar's "Machine" section (`ui/machine/`), across every saved project. The section sits after the projects, with one entry per resource and a summary (daemons and memory, running emulators, booted simulators, DerivedData size). An entry is shown only when some saved project declares its provider (`MachineResources.visibleResources`, from `ProjectWorktrees.declaredResources`; iOS simulators and DerivedData also need macOS). The destination is `ShellDestination.Machine(resource)`, saved as `machine:<RESOURCE>`; if the board has loaded every project and the resource is no longer declared, the shell falls back to "Active".

`MachineResources` is the shell-scoped state holder, built in `AppShell` over `AppContainer.machineUseCases` (`MachineUseCases`: machine-wide Gradle and DerivedData use cases that need no repository, plus the per-project emulator and simulator ones, all delegating to the existing use cases and their rules). It reads the projects from the board as `MachineProject` (worktrees, or `null` while that project's list is loading, the providers it declares, its Gradle settings and build cache state, whether it declares compilation caching). A loader runs only while its view is shown: every 5 seconds for daemons, emulators and simulators, and once on opening and on View > Reload for DerivedData, whose sizes are expensive. A separate 30-second tick (`runSummaryTicks`, restarted when the declared set changes) refreshes only the sidebar summaries; the DerivedData size is read once and then kept.

Owners are computed from the board's worktree lists, never by running `git worktree list` again: `attributeToProjects` (in `core`) picks the deepest worktree of any saved project containing the folder (`Owned(projectId, worktreePath)`), or `RemovedWorktree` when the folder no longer exists, or `Outside`, or `Loading` while a project that could own it hasn't loaded. The CLI keeps its per-repository attribution. In the views, an owner is a link that selects that worktree in "All worktrees" (`WorktreeRef`).

- **Gradle daemons**: one row per daemon (kind and version, PID, memory, "busy" / "idle · 12 minutes ago" / "state unknown", owner), a "Stop" button per row and "Stop idle daemons". A button is enabled only for daemons the stop rules allow (`isStoppable`: an idle Gradle daemon, or a Kotlin daemon while no Gradle daemon is busy or unknown); `StopGradleDaemonsUseCase` checks the state again anyway. The result (`daemonStopReport`) stays until dismissed. Below, each declaring project's worktree build settings (e.g. "api · Worktree builds: 2 GB heap · idle after 30 min", `formatMemorySetting` turns `2g` into "2 GB") and the build cache note for each declaring project that has it off.
- **Android emulators**: the emulator of every worktree of the declaring projects (serial, owner, state, Start or Stop through the same use cases as the worktree's detail, after which that project's board state is reloaded so both places agree), and, apart, running emulators no worktree owns (`adb devices` minus the reserved serials), with Stop. Listing reserves no port.
- **iOS simulators**: every ShogunAi simulator with its owner, state and Start or Stop, and the orphaned ones (`DeleteSimulatorsUseCase.orphans`) apart, with "Delete orphans" behind a confirmation dialog. Simulators not created by ShogunAi aren't listed.
- **Xcode DerivedData**: every folder (workspace, size, last access, owner), the total, the orphans' count and size with "Delete orphans" behind a confirmation (disabled without orphans, running `DeleteOrphanedDerivedDataUseCase`), folders on unavailable volumes shown as kept, and the compilation cache size when any saved project declares it.

`ProjectWorktrees` no longer loads daemons or DerivedData for display. It still reads the provider declarations (`isGradleRepository`, `isXcodeRepository`, `gradleSettings`, `isGradleBuildCacheEnabled`, `hasCompilationCache`) and the DerivedData orphans, which the cleanup dialog needs. A project's group header shows a small "Gradle" chip when it declares the provider, linking to the Gradle daemons view.

### Launch command

The detail's "Copy launch command" button, the row's hover icon and context menu, and Cmd/Ctrl+Shift+C on the selected worktree call `ProjectWorktrees.copyLaunchCommand(worktree)`; the row's icon shows a "Copied" state for a couple of seconds. The command (`cd "<path>" && <agent command>`) is pasted by the user in the terminal of their choice.

### Project settings

`ProjectSettingsView` (`ui/projectconfig`) hosts the `ProjectSettings` and `AddProject` destinations. It is a column limited to 720 dp, centered, with the sections General (name, base repository, worktrees folder, main branch), Worktree files (what the repository declares and the local files), Repository configuration (providers and environment, written to `.shogunai.json`), Agent and, when the repository declares them, Android emulator and iOS simulator. Sections are separated by a heading, not cards, with each field's label above it and its help below. From 1000 dp of content width a section index sits on the left: activating an entry animates the scroll to the section, and the entry for the section in view is highlighted.

`ProjectConfigViewModel` shows a required-field error only once the field was edited (`touchedFields`) or a save was attempted (`saveAttempted`), through `isErrorVisible`. "Save" is always enabled: `submit()` returns `SaveOutcome.Invalid` with the first invalid field, which the view focuses, or saves. A save bar slides in at the bottom while `hasUnsavedChanges` holds, with "Discard" and "Save"; while adding a project it always shows "Cancel" and "Add project". File > Save (Cmd/Ctrl+S) is enabled by `ShellViewModel.canSaveSettings`, which the view keeps up to date, and reaches the view through `saveSettingsRequests`.

The "Repository configuration" section (`RepositoryConfigSection`, between Worktree files and Agent) edits the repository's `.shogunai.json` instead of the project's own settings. `ProjectConfigViewModel` keeps it as an immutable `RepositoryConfigDraft` (providers on or off with their options as text, the `env` rows and `envFile`), compared with the draft read when the settings opened to count as unsaved changes. Validation (`RepositoryConfigDraft.problems`) uses `RepositoryConfigRules` from `core`, the same rules the readers apply, and the typed `RepositoryConfigIssue`s are rendered by `Strings.repositoryConfigIssue`. iOS simulator and Xcode are only offered on macOS, but their declarations are kept in the draft and written back. An unreadable `.shogunai.json` (`RepositoryProvisioningState.Unreadable`) leaves the section read-only with the reason.

Saving runs in this order: validate (`SaveOutcome.InvalidRepositoryConfig` scrolls to the section), show the resulting file in a dialog before the first repository write of the visit (`SaveOutcome.NeedsPreview`; cancelling keeps the edits and saves nothing), write `.shogunai.json` through `RepositoryProvisioningSource.write` with the content hash recorded when the settings opened (`RepositoryProvisioningSource.fingerprint`), and only then save the project. Moved local files and provider edits go in the same write. If the file changed on disk the write is refused with `WorktreeError.RepositoryConfigChanged` and the section offers to reload; any write failure leaves the project unsaved. After a repository write the shell asks `WorktreeBoard` to reload the project's holder, which refreshes the providers the Machine section and the worktree detail read. The emulator and iOS simulator sections are labeled "This machine only"; the preferred AVD, device and runtime live in this section because they are repository configuration.

The view registers its `ProjectConfigViewModel` as the shell's `LeaveGuard`. `ShellViewModel.requestLeave` runs a navigation (sidebar, shortcuts, menu entries, "Discard") or the window close only when the guard has no unsaved changes; otherwise it keeps it as pending and `AppShell` asks to discard, with `confirmLeave` or `keepEditing` resolving it. The window's close request, File > Quit and the macOS quit handler all go through it.

"Add project" starts by choosing the repository folder, with the picker or by dropping a folder on the window (`folderDropTarget`, which only accepts a single directory and outlines the window while hovering). `AppContainer.inspectRepository` resolves the folder to its repository's top level with `ResolveRepositoryUseCase` and detects the main branch with `ResolveMainBranchUseCase`; `ProjectConfigViewModel.chooseFolder` then prefills the name, the `<folder>-worktrees` folder next to the repository and the detected branch as the main branch placeholder. A folder that isn't a Git repository is rejected with a message and the form stays closed. The prefilled values count as the form's initial values, so an unedited prefilled form has no unsaved changes.

### Repository provisioning and local files

The configuration form has two file sections:

- **From the repository** (read-only): the patterns of the base repository's `.shogunai.json` and `.worktreeinclude`, each with the file it comes from. `ProjectConfigViewModel.repositoryProvisioning` holds a `RepositoryProvisioningState` (`None`, `Declared`, or `Unreadable` with the file and the parse error), read through `RepositoryProvisioningSource` when the form opens and every time the base repository path changes.
- **Local files**: the project's `secretFiles`, exact paths stored only in `~/.shogunai/projects.json`.

"Move to .shogunai.json" (`moveLocalFilesToRepository`) is offered when some local file is inside the repository (relative, not starting with `..`) and `.shogunai.json` isn't unreadable. It turns those entries into patterns anchored to the root (`local.properties` → `/local.properties`), lists them under "From the repository" marked "on save", and removes them from the local list; absolute entries stay local. Nothing is written yet: pending patterns count as an unsaved change, so cancelling discards them. `save()` returns `Result<Project>`: it first appends the pending patterns with `addCopyPatterns`, then saves the project; if writing the repository file fails, the project isn't saved and the screen shows the error above the buttons, keeping the form open.

### Moving a repository

When the user edits a project because its repository moved on disk:

- `ProjectConfigViewModel` turns absolute local file entries inside the base repository into relative ones, so they follow the repository.
- Changing the base repository path also moves the worktrees root when it was inside the repository, its parent or a sibling folder, unless the user edited it by hand (`RepositoryRelocation.kt`).
- After saving with a different base path or worktrees root, `ShellViewModel.onProjectSaved` selects the view filtered to the project with the previous worktrees root, and `WorktreeBoard.repairAfterMove` runs `RepairWorktreesUseCase` on that project before listing. A failed repair stays visible as an error.
- The board recreates a project's `ProjectWorktrees` when its configuration changes, not only its id, so an edited project never reuses use cases bound to the old paths.

List loads are tagged with a generation number: a result is only applied if no newer load started meanwhile, so a slow earlier load can't overwrite a newer state (or clear a newer error).

## Shared components (`ui/components`)

- **`Spacing`**: spacing scale (`xxs=2dp`, `xs=4dp`, `sm=8dp`, `md=16dp`, `lg=24dp`, `xl=32dp`) used instead of ad hoc `dp` values. Component-internal sizes, such as icon sizes, are named constants.
- **`SegmentedSelector<T>`**: segmented control (`SingleChoiceSegmentedButtonRow`/`SegmentedButton`) for a small, fixed set of options that should all be visible at once (e.g. `CreateMode`, `BranchType`). Labels are sized to their full text (`Modifier.width(IntrinsicSize.Max)`): the row sizes its segments from their minimum intrinsic width, which for plain text is only its longest word.
- **`DropdownSelector<T>`**: dropdown (`ExposedDropdownMenuBox`) for longer or dynamically loaded lists, with optional collapsible groups (`groupBy`) and disabled options (`enabled`), which are ignored whatever the input method.
- **`SectionCard`**: titled `OutlinedCard`, used to group sections of a long form (every section of `ProjectConfigScreen`).
- **Keyboard shortcuts** (`KeyboardShortcuts.kt`): `onEnterKey` triggers the main action of a text field (e.g. adding a local file), and `onNewItemShortcut` binds Cmd+N (macOS) / Ctrl+N (other systems) to opening the "New worktree" dialog.

## Theme (`ui/theme`)

`ShogunAiTheme` applies the light or dark color scheme from the stored `ThemeMode`, chosen in the Preferences window. In `SYSTEM` mode, `rememberSystemInDarkTheme` polls the OS theme every second, because Compose Desktop's `isSystemInDarkTheme()` doesn't react to changes while the app is open.

## Design system

The theme and the shared components form the app's design system. Screens use its tokens and components instead of literal colors, sizes or per-screen copies.

### Palette

Every Material 3 color role of the light and dark schemes is defined by the app (`ShogunLightColorScheme`, `ShogunDarkColorScheme` in `Theme.kt`), so no dialog, menu or selected control falls back to Material's baseline purple. The roles come from one cool neutral ramp (`Neutral*` in `Color.kt`) and one brand accent. The brand red is `primary`: it's used for the main action of a view and for brand marks, never to convey a status. It's slightly darker in the light scheme, so white text on it stays readable.

Status colors are separate tokens with one meaning each, exposed as `ShogunTheme.status` (`ShogunStatusColors`). Each status has a `color` for text and icons, an `onColor`, and a `container`/`onContainer` pair for chips and notices:

| Status | Meaning | Examples |
|---|---|---|
| success | done or healthy | merged, device running, operation succeeded |
| warning | needs your attention, not broken | uncommitted changes, inactive |
| info | work in progress | agent running, device booting |
| danger | failed or destructive | errors, missing folder, delete actions |
| neutral | plain state | locked, device stopped |

The mapping from worktree indicators and device states to a status lives in `StatusMapping.kt`.

Contrast is checked by tests, not by eye: `ThemeContrastTest` uses `contrastRatio` (WCAG relative luminance) to assert that text reaches 4.5:1 and outlines 3:1 on every surface, in both schemes, and `ColorSchemeTest` asserts that no role keeps Material's baseline value.

### Type

The app bundles Geist for UI text and Geist Mono for data, in weights 400, 500 and 600, under `desktopApp/src/main/resources/fonts/` with their SIL Open Font License (`OFL.txt`). Bundling them makes text render the same on macOS, Windows and Linux. Every `Typography` slot uses Geist, with sizes tuned for a dense desktop tool. `ShogunTheme.typography` adds two styles for data, `data` and `dataSmall`, in Geist Mono with tabular figures: paths, branches, ports, PIDs, sizes, counts and relative times use them, so values that refresh don't shift the layout.

### Shapes and focus

`ShogunShapes` sets the corner radius scale (`extraSmall` 4, `small` 6, `medium` 10, `large` 14, `extraLarge` 20 dp). The theme raises the focused state layer of Material's ripple, so keyboard focus is visible on every Material control.

### Shared components

- **`InlineProgress`**: the one inline spinner, in `Small` and `Medium` sizes.
- **`Notice`**: error, warning, success and info messages with their status icon and color, the message and a dismiss action. It has a `details` slot for extra content. `ErrorNotice` builds on it for errors (summary, next step, fix, "Try again" and "Technical details").
- **`StatusChip`**: a compact label on a status container color, with an optional icon.
- **`DangerButton`, `DangerTextButton`**: destructive actions, where they're offered and in the confirmation that follows. **`NeutralTextButton`** is the text button for neutral actions such as "Cancel", so the brand color stays reserved for main actions.
- **`IconAction`**: an icon-only action whose label is both its tooltip and its accessible description.
- **`Modifier.interactive`**: makes a custom row or header clickable, with a hover state, the hand pointer and a focus ring (`Modifier.focusRing`).

## `AppContainer`

Builds every port implementation once at app startup: `JsonProjectRepository`, `JsonSettingsFile` with the theme and language repositories, `NioFileManager`, `ProcessBuilderShellCommandExecutor`, `AwtClipboardWriter`, `RepositoryProvisioningFiles`, `ProcessGradleDaemonSource`, `MacDerivedDataSource`, `NioEnvironmentStateStore` with `SocketPortProbe`, `SimctlAppleSimulators`, `FileKmpTestWiring`, `LocalAndroidSdk`, `HerdrConfigFile` and `CliHerdrClient`. It also builds the machine-wide pieces shared by every project: `MachineProviderContributions` (reading each repository's simulator settings from its project), the environment resolver and preparer, and the simulator and emulator use cases.

- The project configuration screen gets `simulatorSetup` (runtimes, KMP wiring, the init script) and `loadAvds` for its provider sections.
- The worktree use cases are scoped to a project, so `worktreeUseCases(config)` builds a `WorktreeUseCases` bundle for the selected `ProjectConfig`, with its `GradleUseCases`, `XcodeUseCases`, `EmulatorUseCases`, `SimulatorUseCases` and Herdr use cases over the shared implementations.

There's no DI framework.

## `main.kt`

The entry point of the packaged app. With arguments (ignoring macOS's legacy `-psn_…` one) it runs the [`shogun` command](cli.md) and exits without loading AWT or Compose, so the app's launcher doubles as the command. Without arguments it sets the Dock icon where the platform supports it and opens the window with `AppRoot`.

## `FilePicker`

Wraps `JFileChooser` for the "Browse…" buttons on the configuration screen (base repo path, worktrees folder, local files). Chosen over a native Compose picker because Compose Desktop doesn't ship one and `JFileChooser` is already available on the JVM target with no new dependencies. Both dialogs show hidden files (`isFileHidingEnabled = false`), so dotfiles such as `.env` can be picked. `relativeToBase` converts the absolute path of a chosen local file into the path relative to the repo that `ProjectConfig.secretFiles` expects.

## Errors

Every error is shown as a `UserFacingError` built by `Strings.describe(error)`: a **summary** that says what failed, a **next step** that says what to do, an optional **fix** (`ErrorFix.OpenProjectSettings` or `OpenPreferences`, a button next to the next step) and optional **technical details** (`TechnicalDetails`: the command, its exit code and the tool's output, unchanged). Neither the summary nor the next step contains a command line, an exit code or a tool's output. The texts live in `EnglishErrors.kt` and `SpanishErrors.kt` and are exhaustive over the sealed `WorktreeError` hierarchy, so a new variant doesn't compile until both languages describe it. The domain errors stay language-agnostic (the CLI prints its own English messages).

- **Git causes**: `classifyGitFailure(stderr)` in `core` recognizes a lock file, permission denied, a folder that isn't a repository, an existing branch and local changes in the worktree from Git's output (`GitFailureCause`); anything else is `Unknown` and gets a general explanation. Only Git's English output is matched, so every Git command runs with `LC_ALL=C` (`GitLocale`, applied by `ProcessBuilderShellCommandExecutor`).
- **Rendering**: `ErrorNotice` wraps `Notice` with the summary, the next step in muted text, the fix button, "Try again" and a collapsed "Technical details" disclosure (data style, scrollable up to 200 dp) with a copy action that copies plain text. Holders store the `Throwable` and describe it at render time, so a language change re-renders it. The fix buttons use `LocalErrorFixActions`, which the shell provides (`openProjectSettings(projectId)`, `openPreferences()`).
- **Placement**: a dialog's errors stay inside the dialog (`createError`), an action on one worktree shows in its detail (`actionErrors[path]`), a project that fails to load shows in its group (`loadState`), the machine views show theirs at the top of the view (`MachineResources.error`), and the project settings show a save failure above the buttons.
- **Lifetime**: an error stays until it is dismissed or the same action succeeds; a success of another action never clears it. Errors of actions that can be repeated safely (reloading, starting or stopping a device, stopping a daemon, listing branches) keep a retry (`actionRetry(path)`, `errorRetry`, `MachineResources.errorRetry`) that "Try again" runs after clearing the error. A project that failed to load offers "Try again" too and can't be dismissed, since its group has nothing else to show.

## Languages (`ui/i18n`)

The UI is available in English (default) and Spanish. Every visible text lives in `interface Strings`, with one implementation per language (`EnglishStrings`, `SpanishStrings`); texts with parameters are functions (`deleteProjectMessage(projectName)`), so arguments are checked at compile time.

- `Appearance` loads the stored `AppLanguage` (`LanguagePreferenceRepository`) and `AppearanceScope` provides the active table with `CompositionLocalProvider(LocalStrings provides ...)` in every window: switching language recomposes texts, including the menu bar, without recreating ViewModels or losing screen state.
- Composables read texts with `val strings = LocalStrings.current`. Non-composable code (such as `FilePicker`) receives the already-translated text as a parameter.
- The language selector in Preferences shows each language by its own name ("English", "Español").
- ViewModels and state holders expose data, not messages: `ProjectWorktrees` exposes `listFailure`, `error: Throwable?` and `success: WorktreeSuccess?` (worktree created, worktrees repaired after a move, worktree opened in Herdr), `WorktreeBoard` exposes `cleanupSuccess` (cleanup done), and the screen formats them with `strings.describe(error)` and the matching `Strings` text. `describe` is exhaustive over the sealed `WorktreeError` hierarchy, so a new variant doesn't compile until it's translated; a process that can't start (`CommandExecutionException`) gets its own message, and anything else falls back to a generic one. Git's error output is shown as-is, untranslated, in the error's technical details (see Errors).

**Adding a text**: declare the member in `Strings` and implement it in every language (the compiler flags any missing one). **Adding a language**: new `AppLanguage` value, new `object` implementing `Strings`, and its branch in `AppLanguage.strings()`.

## The Active view and activity

A worktree's activity is a pure function in `core` (`worktreeActivity`, next to `isInactive`): `AgentWorking` when an agent runs in it, `NeedsYou` when it has uncommitted changes or commits not pushed (ahead of its upstream, or ahead of the main branch when it has no upstream), `InProgress` for any other worktree that isn't the main one, merged, inactive or missing its folder, and `Merged`, `Inactive`, `Missing`, `Idle` (the clean main worktree) or `Unknown` (status not read yet, no agent) otherwise. The first three are the *active* ones, and "Active" lists them across every project.

Grouped by state, "Active" shows "Agent working", "Needs you" and "In progress"; "All worktrees" adds "Merged" (whose header offers "Clean up"), "Inactive", "Folder missing" and a trailing "Other" for the clean main worktrees and those whose status isn't read yet. Empty groups are hidden. The selected worktree is pinned: it stays in "Active" in the group for its real state until it's deselected, so it never vanishes under the cursor. While statuses are being read the header shows a loading line, and when nothing is active the view offers "New worktree" and "See all worktrees".
