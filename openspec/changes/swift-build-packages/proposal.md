# Proposal

## Why

A Swift package repository built with `swift build` leaves a `.build` folder in each worktree, often several gigabytes, and the Xcode provider only sees Xcode's DerivedData. Those repositories can't see what their builds cost per worktree, the DerivedData quota ignores them, and the only way to reclaim the space is to delete folders by hand.

## What Changes

- The Xcode provider also reads the `.build` folder of every worktree of a repository that declares it, when the worktree has a `Package.swift` at its root, with its size, last modification and packages size (`.build/checkouts` and `.build/repositories`).
- `shogun xcode` lists those folders next to the worktrees' DerivedData, by path, and its JSON gives each folder a `kind` (`derived-data` or `swift-build`).
- The `.build` folders count against the DerivedData quota and the quota releases them like worktree DerivedData folders: never while their worktree is in use, least recently modified first, checked again just before deleting, in `shogun xcode trim` and in the desktop app.
- `shogun xcode clean --swift-build` deletes the `.build` folders of the repository's worktrees that are not in use and reports the ones kept.
- The desktop app's Xcode view shows each `.build` folder as a row of its worktree and adds a "Delete unused .build" action, with confirmation.
- Their sizes are remembered and measured in parallel like DerivedData folders.
- The desktop app's quota now also releases the DerivedData folders of unused worktrees, as the spec already required: until now it only saw orphans, because it listed the folders without the saved projects' worktrees.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `xcode-provider`: the provider finds, attributes, measures, shows, cleans and counts in the quota the `.build` folders of Swift package worktrees.
- `machine-resources`: the DerivedData view shows the `.build` folders of the saved projects' worktrees and offers to delete the unused ones.

## Impact

- `core`: `DerivedDataFolder` gains a kind; a new `SwiftBuildSource` port with its `LocalSwiftBuildSource` implementation wired into `EnginePorts`; `ListDerivedDataUseCase`, `MeasureDerivedDataUseCase` and `TrimDerivedDataUseCase` handle both kinds; a new `DeleteUnusedSwiftBuildsUseCase`.
- `cli`: `shogun xcode`, `shogun xcode trim`, `shogun xcode clean --swift-build`, their JSON and help.
- `desktopApp`: `XcodeDerivedDataView`, the DerivedData parts of `MachineResources` and `AppContainer`, English and Spanish strings.
- Docs: the Xcode provider pages (en and es) and the roadmap bullet, removed when archiving.
