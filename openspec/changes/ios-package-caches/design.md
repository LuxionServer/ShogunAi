# Design

## Context

Roadmap item 2 asks for the caches of iOS packages. Exploring them on top of the Xcode provider (DerivedData quota, compilation cache shared between worktrees) gave this map:

| Packages | Per worktree | Shared | Governed today |
|---|---|---|---|
| SwiftPM in Xcode projects | `DerivedData/<project>/SourcePackages` (checkouts and repositories) | `~/Library/Caches/org.swift.swiftpm` (repository mirrors, manifests) | The per-worktree part, through the DerivedData quota; the shared part isn't visible |
| CocoaPods | `<folder>/Pods`, usually ignored by Git | `~/Library/Caches/CocoaPods` (downloaded pods) | Nothing; a fresh worktree has no `Pods` |

Swift packages are mostly covered: a new worktree resolves its packages from Xcode's shared repository cache without the network, its checkouts live in its DerivedData and are released with it. Sharing the checkouts themselves would only be possible through `xcodebuild -clonedSourcePackagesDirPath`, a flag that neither an `.xcconfig` nor an environment variable can set, and two agents resolving into the same folder at once could corrupt it. CocoaPods is where a worktree is furthest from building.

Decisions taken with the user while exploring: clone `Pods` from the base when the lock matches; for SwiftPM, show and don't share; show the shared caches apart, without a quota or a delete action.

## Goals / Non-Goals

**Goals:**
- A worktree of a CocoaPods project builds without `pod install` when its `Podfile.lock` matches the base's installed pods, at no disk cost.
- Seeing how much of the DerivedData are Swift packages and how large the shared package caches are.

**Non-Goals:**
- Sharing Swift package checkouts between worktrees, or exporting a packages folder for `xcodebuild`.
- Running `pod install` (it stays a post-creation command the repository can declare).
- A quota or cleanup for the shared package caches; Xcode, SwiftPM and CocoaPods own them.
- `swift build` packages (`.build` of `Package.swift` repositories) and Carthage.
- Custom cache locations (`-packageCachePath`, CocoaPods' `cache_root`); only the default locations are measured.

## Decisions

### Clone `Pods` with `cp -c -R`, never copy

`cp -c` clones with `clonefile(2)`: on APFS the clone is instant and shares blocks until a file changes, so a worktree's pods cost no disk until it runs `pod install` with other versions. It runs as argv through the `ShellCommandExecutor` (`cp -c -R <base>/<folder>/Pods <worktree>/<folder>/Pods`), which keeps symbolic links as links. On a volume without clones `cp -c` fails; then the partial destination is removed and the folder is reported as needing `pod install`. A plain copy is never attempted: pods can take gigabytes and a slow full copy would be worse than `pod install` using CocoaPods' own cache.

Alternatives: the `FileManager` port copies files one by one with NIO (it clones single files on recent JDKs on macOS but walks the tree itself); a symbolic link to the base's `Pods` would make every worktree change the base's pods.

### `Manifest.lock` against `Podfile.lock` decides

CocoaPods writes `Pods/Manifest.lock` as a copy of `Podfile.lock` after installing, and its build phase "Check Pods Manifest.lock" fails when they differ. Comparing the base's `Pods/Manifest.lock` with the worktree's tracked `Podfile.lock` byte by byte is the same check CocoaPods makes, so cloned pods are exactly the ones the worktree's lock asks for. Pod folders are found with `git ls-files -z -- ':(glob)**/Podfile.lock'` in the worktree: tracked locks only, at any depth, without walking ignored folders.

Generated pods refer to their files through `${PODS_ROOT}` and `${SRCROOT}` relative paths, which is why some teams commit `Pods`; development pods declared with `:path` are referenced relative to the Podfile too, so the clone works from the worktree's location.

### Pods run inside the environment preparation

Creating (`CreateWorktreeUseCase`, `CreateWorktreeFromBranchUseCase`, `CreateGroupUseCase`) and provisioning (`ProvisionWorktreeUseCase`) already call `WorktreeEnvironmentPreparer.prepare` after copying files and before the post-creation commands. `prepare` will return a `PreparedWorktree` with the pods report, and `PrepareWorktreeEnvironmentUseCase` takes a new `PrepareCocoaPodsUseCase` (defaulting to none) and runs it after writing the environment file. That puts the pods in every flow without new constructor parameters in the creation use cases, which other changes in flight (multirepo in the CLI) also touch. The pods use case never throws except for cancellation: any failure becomes part of its report, so a pods problem can't roll a worktree back. The report reaches `Worktree.pods` and `ProvisionResult.pods`.

`PrepareCocoaPodsUseCase` reads the repository's provisioning and does nothing unless `providers.xcode.cocoaPods` is true and the platform is macOS (a flag injected by the CLI and the app, as the DerivedData source's `isSupported`).

### Report shape

```
PodsPreparation(
  cloned: List<String>,                 // pod folders, relative to the worktree ("." for the root)
  installNeeded: List<PodsInstallNeeded(folder, reason)>,
  error: String?,                       // couldn't even list the locks
)
reason: BASE_HAS_NO_PODS | BASE_PODS_DIFFER | CLONE_FAILED
```

JSON: `pods: { cloned: [...], installNeeded: [{ folder, reason }], error }` in `shogun new`, `shogun provision`, and the hook's notes. Text: `pods cloned: iosApp` and `iosApp: pod install needed (the base's pods differ)`. The hook prints them to stderr as it does with skipped links, since its stdout is the worktree path.

### Packages size measured with the folder

`DerivedDataSource.size` returns a `FolderSize(bytes, packagesBytes)`: the Mac source measures the folder and then its `SourcePackages` (zero if missing). The second walk only covers the packages subtree. `MeasuredSize`, `RememberedSize` and `DerivedDataFolder` gain `packagesBytes`; the size store writes it as an optional field, and an entry without it isn't valid, so folders remembered by an earlier version are measured once more. The quota keeps using the total size.

### Shared caches as a separate port

A `PackageCacheSource` port (`swiftPmCacheBytes()`, `cocoaPodsCacheBytes()`, null when the folder is missing) with a Mac implementation measuring `~/Library/Caches/org.swift.swiftpm` and `~/Library/Caches/CocoaPods`. They aren't DerivedData, so they don't go into `DerivedDataSource`. `ListDerivedDataUseCase` returns them in `DerivedDataListing.packageCaches`, measured in parallel with the compilation cache, and the app's machine resources holder measures them alongside the compilation cache. They're shown whenever the Xcode provider is active, without a new per-project flag, so `ProjectWorktrees.kt` doesn't change.

## Risks / Trade-offs

- [A cloned `Pods` refers to something absolute in the base] → CocoaPods' generated files use relative build settings; a project that breaks can turn the option off, and `pod install` in the worktree repairs it.
- [The base's `Pods` is being rewritten by `pod install` while it's cloned] → the clone could be inconsistent; the worktree's "Check Pods Manifest.lock" build phase and `pod install` recover. Not worth a lock.
- [Measuring `SourcePackages` adds a walk per folder] → it's a subtree of a walk already made, measured at most four folders at a time and remembered.
- [Re-measuring every folder once after upgrading] → one slow listing, the same as the first run of the size store.

## Migration Plan

No migration: `cocoaPods` is off by default and the size store accepts entries without `packagesBytes`.
