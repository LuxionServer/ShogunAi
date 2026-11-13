# Design

## Context

The Xcode provider reads Xcode's DerivedData location through the `DerivedDataSource` port (`MacDerivedDataSource`), lists and attributes folders with `ListDerivedDataUseCase`, measures them with `MeasureDerivedDataUseCase` (remembered in `derived-data-sizes.json`), deletes orphans with `DeleteOrphanedDerivedDataUseCase` and honours the quota with `TrimDerivedDataUseCase`. `shogun xcode`, `shogun xcode clean`, `shogun xcode trim` and the desktop app's `XcodeDerivedDataView` (fed by `MachineResources`) sit on top. Stack detection already counts a root `Package.swift` as Xcode evidence and suggests the `xcode` provider for it.

A `.build` folder differs from a DerivedData folder in where it lives (inside its worktree, not in a shared location), in how it is found (from the worktrees, not by scanning a folder) and in its lifetime (it goes away with its worktree, so it can't be orphaned).

## Goals / Non-Goals

**Goals:**

- One listing, one measurement, one usage figure and one trim for both kinds of folders, so the quota, the remembered sizes, the parallel measurement and the views keep a single code path.
- Deleting a `.build` folder only when it is one: a real folder named `.build` next to a `Package.swift`.

**Non-Goals:**

- Packages in subfolders of a worktree (`Package.swift` outside the root) and `swift build --scratch-path` elsewhere: only `<worktree>/.build` is read.
- Linux and Windows: the provider stays macOS-only, as decided with the user, even though `swift build` runs elsewhere.
- Sharing SwiftPM builds between worktrees, or a provisioning step for `swift package resolve`.

## Decisions

### Part of the Xcode provider

The user chose the Xcode provider over a provider of its own: the repositories concerned are already detected as the Xcode stack, the quota is the provider's, and a second provider would duplicate the command, the view and the settings. The `.build` folders are read only for repositories that declare `"xcode"`.

### A kind on `DerivedDataFolder`

`DerivedDataFolder` gains `kind: DerivedDataKind` (`XCODE`, the default, or `SWIFT_BUILD`). For a `.build` folder `path` is `<worktree>/.build`, `workspacePath` is the worktree, so the existing attribution (`owningWorktree`) and the desktop's project attribution work unchanged, and `lastAccessed` is its last modification.

Alternative: a separate `SwiftBuildFolder` model and listing. Rejected: the quota must order both kinds together, and every consumer (measure, size store, trim, JSON, view) would have needed a second path.

### A `SwiftBuildSource` port next to `DerivedDataSource`

`SwiftBuildSource` (domain/io) has `folderOf(worktreePath)`, `size`, `packagesSize` and `delete`; `FileSwiftBuildSource` (infrastructure) implements it on the `FileManager` port, so its tests run on `FakeFileManager`, and is wired in `EnginePorts.swiftBuilds`. A small domain class, `DerivedDataFolders`, dispatches `size`, `packagesSize`, `delete` and "read again" on the folder's kind for the use cases.

`DerivedDataSource` isn't extended because its `delete` deliberately refuses anything that isn't a direct child of the DerivedData location, and its `folders()` scans that location; mixing worktree paths into it would weaken that guard.

`folderOf` returns a folder only when the worktree has a regular `Package.swift` and `.build` is a directory that isn't a symbolic link. `delete` refuses a path whose name isn't `.build`, that is a symbolic link or whose parent has no `Package.swift`.

### Last access from the folder's top level

The last access is the latest modification time of `.build` and of its direct entries (not following links): `swift build` rewrites `build.db`, `workspace-state.json` and the triple folder on every build. A recursive walk would cost as much as measuring, which the remembered sizes exist to avoid.

### Which worktrees are read

`ListDerivedDataUseCase` gains `packageWorktrees: List<Worktree>`, separate from the `worktrees` used for attribution, because `shogun xcode trim` attributes against every saved project's worktrees but must only read `.build` folders of repositories declaring the provider:

- `shogun xcode` and `shogun xcode clean --swift-build`: the repository's worktrees.
- `shogun xcode trim`: the worktrees of the saved repositories that declare the provider.
- The desktop's DerivedData view: the worktrees of the saved projects that declare the provider (`MachineProject.declares` has `XCODE_DERIVED_DATA`).
- The worktree screen's orphan check and `shogun clean --delete-derived-data`: none, as `.build` folders are never orphans.

### The desktop listing gets the worktrees

The desktop app listed DerivedData with no worktrees, so every live folder was "another project" and its quota only ever released orphans, against the `Release folders to honour the quota` requirement. To list `.build` folders it now passes the declaring projects' worktrees for both lists, which also makes their DerivedData folders releasable. A regression test reproduces the old behaviour first. The view's owner column is unaffected: it attributes every non-orphan folder to projects itself.

### Manual cleaning of unused `.build` folders

A `.build` folder has no orphan state, so with "No quota" nothing would ever clean it. `DeleteUnusedSwiftBuildsUseCase` deletes the `.build` folders of the given worktrees that aren't in use, reading each again before deleting it, and returns `SwiftBuildDeletion` (deleted, in use, failures). "In use" is the trim's rule, moved to a shared function in the use case package so both use it: the worktree's agent is working or blocked, or the folder changed within the last hour. The CLI exposes it as `shogun xcode clean --swift-build`, the desktop view as "Delete unused .build", which detects running agents like its trim.

### Output

`shogun xcode` prints `.build` folders by path (`<worktree>/.build  4.0 GB  (packages 800 MB)`), since the worktree path alone would read like its DerivedData. The JSON adds `kind` to every `folders` entry, keeping the existing fields. The desktop row shows `<worktree> · .build`.

### Staying within the file length caps

`CommandJson.kt`, `MachineResources.kt`, `Strings.kt`, `EnglishStrings.kt` and `SpanishStrings.kt` were at their caps. Instead of raising them, the Xcode parts move to files of their own: the `xcode` JSON functions to `XcodeJson.kt`, the machine view's DerivedData models and owner attribution to `MachineDerivedData.kt`, the `.build` deletion state to `SwiftBuildCleanup.kt`, and the Xcode view's texts to an `XcodeStrings` interface that `Strings` extends, implemented by `EnglishXcodeStrings` and `SpanishXcodeStrings`, to which `EnglishStrings` and `SpanishStrings` delegate. `EveryTextTest` reads the inherited members too, so the moved texts stay checked. No cap changes.

### No new serializable class

Remembered sizes stay keyed by path in the existing file; no `@Serializable` class is added, so `core`'s maximum of missed branches doesn't change.

## Risks / Trade-offs

- [A `swift build` running while the quota or the manual clean deletes its `.build`] → the folder changed within the last hour is in use and it is read again just before deleting; a build started in the second between both is the same risk DerivedData already has, and SwiftPM rebuilds the folder.
- [A large worktree list makes listing slower] → `folderOf` only stats a few files per worktree; measuring stays in the background with the remembered sizes.
- [`shogun xcode` forgets the remembered sizes of other repositories' `.build` folders when it saves] → it is the existing behaviour of the size store for any call that measures part of the folders; the desktop measures them again once.
- [The desktop quota starts releasing worktree DerivedData folders that it used to keep] → that is what the spec required; worktrees in use are still protected.
