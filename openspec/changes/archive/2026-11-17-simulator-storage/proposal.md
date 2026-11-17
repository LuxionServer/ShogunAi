# Proposal

## Why

Xcode downloads a simulator runtime for every new iOS version and never removes the old ones, each about 8 GB. On a Mac used for iOS work they pile up under macOS's opaque "System Data": on the machine that prompted this change, seven runtimes took 55 GB, the CoreSimulator dyld caches another 21 GB, and the simulator devices left behind by deleted runtimes about 12 GB. ShogunAi already shows and cleans Xcode's DerivedData and its own orphaned simulators, but it says nothing about the storage that costs the most, so the user only finds it after the disk fills up.

## What Changes

- On macOS, ShogunAi lists the machine's simulator runtimes with their platform, version, size, last use and who uses them: the ShogunAi simulators recorded by worktrees, the saved projects' simulator settings and preferences, and the default runtime ShogunAi creates simulators with.
- It deletes a runtime only when the user asks for that runtime explicitly and confirms, and refuses the runtimes in use, the ones Xcode bundles, and any with a booted simulator.
- It lists the simulator devices that are no longer available (their runtime is gone), with their count and size, and deletes them on request; deleting a runtime offers to delete them too.
- It shows the size of the CoreSimulator dyld caches per macOS build and flags the folders of a build other than the running one, with the administrator command to remove them for the user to run. ShogunAi never runs anything as administrator.
- The desktop app's iOS simulators view gains a storage section, and the view is also offered when a saved project declares the Xcode provider.
- The CLI gains `shogun simulator storage`, `shogun simulator runtime delete <runtime>` and `shogun simulator clean-unavailable`, with text and JSON output; without `--yes` the deletions only say what they would delete.
- `shogun clean` and the cleanup dialog don't touch runtimes, unavailable devices nor dyld caches: they are machine-wide, not a worktree's.

## Capabilities

### New Capabilities

- `simulator-storage`: what ShogunAi reads, protects and deletes of the machine's simulator storage (runtimes, unavailable devices, dyld caches).

### Modified Capabilities

- `machine-resources`: the iOS simulators view shows the simulator storage and is offered for projects that declare the Xcode provider.
- `command-line-tool`: commands to list the simulator storage, delete a runtime and delete unavailable devices.

## Impact

- `core`: `AppleSimulators` gains reading runtimes' storage details, deleting a runtime and deleting unavailable devices; `SimulatorDevice` gains its runtime, availability and data size; a new port for the dyld caches and the macOS build; new use cases to list the storage with each runtime's users and to delete a runtime or the unavailable devices.
- `cli`: the new `simulator` subcommands, their JSON and help.
- `desktopApp`: `IosSimulatorsView`, the simulator parts of `MachineResources`, the Machine entries, English and Spanish strings.
- Docs: the iOS simulator provider and CLI guide pages (en and es).
