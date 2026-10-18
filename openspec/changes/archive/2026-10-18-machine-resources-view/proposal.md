# Proposal

## Why

Gradle daemons, Android emulators, iOS simulators and Xcode DerivedData belong to the machine, not to a project. A daemon serves builds from any repository, and DerivedData and simulators pile up across all of them. Yet the app shows them at the bottom of one project's worktree list, where a daemon from another repository appears as "other project" and orphaned folders are only found from the project that happens to declare the provider. The roadmap asked for "a better place for the Gradle daemons and DerivedData sections than the bottom of the worktree list". The epic's answer (`docs/roadmap.md`, "Desktop app redesign") is a "Machine" section in the sidebar with one view per resource, across every saved project.

## What Changes

- A "Machine" section in the sidebar with an entry per resource: Gradle daemons, Android emulators, iOS simulators (macOS) and Xcode DerivedData (macOS). Each entry is shown only when at least one saved project declares its provider, with a short summary (count and memory or size).
- **Gradle daemons**: every daemon on the machine, each attributed to a project and worktree of any saved project, to a removed worktree, or to a folder outside every project. Stop one or all idle daemons, each project's worktree build settings, and the build cache notice for each project that has it off.
- **Android emulators**: every worktree emulator of the projects that declare the provider, with its state and Start/Stop, plus running emulators that no worktree owns, which can be stopped.
- **iOS simulators**: every ShogunAi simulator with its project and worktree, state and Start/Stop, and the orphaned ones with "Delete orphans".
- **Xcode DerivedData**: every folder attributed to a project and worktree, the totals, orphaned folders with "Delete orphans", and the compilation cache size.
- The owner of a resource links to its worktree in "All worktrees".
- The views refresh while they are shown.
- **BREAKING** (UI): the Gradle daemons and Xcode DerivedData sections are removed from the project view. Per-worktree emulator and simulator controls stay in the worktree's detail.

## Capabilities

### New Capabilities

- `machine-resources`: the sidebar's "Machine" section and its views for Gradle daemons, Android emulators, iOS simulators and Xcode DerivedData across every saved project, with attribution to projects and worktrees, their actions and their refresh.

### Modified Capabilities

- `gradle-provider`: the daemons and settings are no longer shown on the worktree screen. They move to the machine view.
- `xcode-provider`: DerivedData is no longer shown on the worktree screen. It moves to the machine view.

## Impact

- `core`: attribution helpers that take the worktrees of every saved project (Gradle daemon owners, DerivedData owners, simulator owners), next to the existing per-repository ones the CLI uses.
- New `MachineResources` state holder (shell-scoped) with its own refresh. `ui/machine/` with one view per resource. `GradleDaemonsSection` and `XcodeDerivedDataSection` are rebuilt there and removed from `ui/worktree/`.
- `WorktreeBoard` and `ProjectWorktrees` drop Gradle and DerivedData loading. The cleanup options that use them keep working through the domain use cases.
- Docs: `docs/architecture/ui.md` (Gradle daemons, Xcode DerivedData, emulators and simulators sections) in both languages.
