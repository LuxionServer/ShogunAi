# Design

## Context

The project view renders `GradleDaemonsSection` and `XcodeDerivedDataSection` below its list, fed by `ProjectWorktrees` (`gradleDaemons`, `derivedData`, settings, build cache). Attribution is per repository: `DaemonOwner.Worktree | RemovedWorktree | OtherProject` and the DerivedData equivalent. `ListGradleDaemonsUseCase`, `ListDerivedDataUseCase`, `ReadEmulatorStatesUseCase`, `ReadSimulatorStatesUseCase` and the stop and delete use cases are built per project in `AppContainer.worktreeUseCases(config)`, and the CLI uses the same per-repository attribution. Emulator and simulator controls are already in the worktree detail (`worktree-list-detail`).

## Goals / Non-Goals

**Goals:**
- One place per machine resource, with owners named by project and worktree.
- Reusing the existing listing, stop and delete use cases and their safety rules unchanged.

**Non-Goals:**
- Starting emulators not owned by a worktree, or managing AVDs.
- Changing the CLI's per-repository output.
- Changing the cleanup options, which still act on the removed worktrees' resources.

## Decisions

### Machine-wide attribution as a new domain function
`attributeToProjects(path, projects: List<ProjectWorktreeSet>)` returns `Owned(projectId, worktreePath) | RemovedWorktree | Outside`, using the same "deepest containing worktree" rule as the per-repository functions. `ProjectWorktreeSet` comes from `WorktreeBoard`'s already-loaded lists, so the machine view never runs `git worktree list` itself. A folder that belonged to a worktree path recorded in a saved project but no longer exists is `RemovedWorktree`. *Alternative considered:* generalizing the per-repository `DaemonOwner`. Rejected to keep the CLI's output and tests untouched.

### `MachineResources` state holder with its own cadence
A shell-scoped `MachineResources` owns one loader per resource. A loader starts when its view is shown and stops when it isn't, with a 5-second tick for processes and devices. The sidebar summaries come from a lighter 30-second tick that only counts (daemons and memory, running emulators, booted simulators). The DerivedData total is cached from the last full read. The use cases are built from `AppContainer` without a project, since none of them need a repository: the Gradle daemon list, `adb devices`, `simctl list` and the DerivedData location are all machine-wide. The settings and build cache notes take each declaring project's config.

### Provider visibility from the catalog
An entry is visible when any saved project's repository declares the provider. That's read with the same provider readers `ProjectWorktrees` already uses on load, exposed by the board as `declaredProviders: Set<ProviderId>`. Simulators and DerivedData also need macOS (`supported`).

### Views as tables with owner links
Each view is a dense list with aligned columns (`data` styles): Gradle (kind and version, PID, memory, state, owner, action), emulators (serial, owner, state, action), simulators (name, owner, state, action), DerivedData (folder, owner, size, last access). The owner is a link-styled text that navigates to `All(null)` with `selected = WorktreeRef(projectId, path)`. Destructive bulk actions (stop idle daemons is safe and has no confirmation, delete orphans has one) use `DangerButton` in their confirmation.

### Removal from the project view
`ProjectWorktrees` stops loading Gradle daemons and DerivedData for the view. The cleanup flow still lists daemons and DerivedData through the use cases at cleanup time, as today, so its options don't depend on the removed sections.

## Risks / Trade-offs

- [Users of the Gradle section look for it in the project view] → The project's group header shows a small "Gradle" chip linking to the machine view when the project declares the provider. The docs and the epic's release notes say where it went.
- [Attribution depends on every project's worktree list being loaded] → Until a project's list is loaded, its resources show as "loading owner" instead of "outside". Attribution is recomputed when the board's lists change.
