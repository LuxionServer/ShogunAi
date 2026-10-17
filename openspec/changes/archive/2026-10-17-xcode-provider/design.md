# Design

## Context

See proposal.md for the motivation. These facts were checked on this machine (Xcode 27.0, 27A266a) and shape the approach:

- Xcode keeps one folder per project in `~/Library/Developer/Xcode/DerivedData`, named `<name>-<hash of the project path>`. For a Swift package the name is the package's folder, not the product (`wt-c-<hash>`), so the name can't be used to attribute a folder. Each folder has an XML `info.plist` with `WorkspacePath` (absolute path of the `.xcodeproj`, `.xcworkspace` or package) and `LastAccessedDate`.
- The same location holds shared caches without `info.plist`: `CompilationCache.noindex`, `ModuleCache.noindex`, `SDKExplicitPrecompiledModules`, `SDKStatCaches.noindex`, `SymbolCache.noindex`. This machine also has real orphans: `Test-…` (61 MB) points to `~/Test/Test.xcodeproj`, which no longer exists, and `Files-…` folders point to temporary file workspaces.
- Compilation caching (`COMPILATION_CACHE_ENABLE_CACHING`) stores its content-addressed cache in `<DerivedData>/CompilationCache.noindex`, shared by every project, or inside a custom `-derivedDataPath`. Deleting a project's DerivedData folder keeps the cache: rebuilding the same path after deleting it got 26 hits and took 1.5 s instead of 3.5 s.
- Between two paths of the same `.xcodeproj` (150 Swift files), caching alone gave 8 hits and 78 misses; adding `SWIFT_ENABLE_PREFIX_MAPPING`, `SWIFT_ENABLE_PROJECT_PREFIX_MAPPING`, `CLANG_ENABLE_PREFIX_MAPPING` and `CLANG_ENABLE_PROJECT_PREFIX_MAPPING` gave 60 hits and 0 misses (13.5 s → 2.0 s). Setting them in a file passed through `XCODE_XCCONFIG_FILE` gave the same result (16.5 s → 1.8 s). A Swift package got no cross-path hits even with prefix mapping.
- The environment already supports provider contributions with resources (`ResourceRequest` with `exists`/`create`, created only when resolving with reservation), and the iOS simulator provider already gates everything on macOS through `AppleSimulators.isSupported()`.
- Orphan handling has a precedent: the simulator cleanup looks for orphans again after the removals (`DeleteSimulatorsUseCase.orphans`), and `owningWorktree` attributes paths with nested worktrees resolved.

## Goals / Non-Goals

**Goals:**
- Show what DerivedData costs per worktree and free what nobody can use any more, without ever deleting the build products of a project that still exists.
- Make a new worktree's first `xcodebuild` reuse the compilations of the others, without touching the project.
- Nothing Xcode-related for repositories that don't declare the provider, or off macOS.

**Non-Goals:**
- Deleting the DerivedData of a live worktree ("Clean Build Folder" from outside): a running Xcode or `xcodebuild` could be using it, and checking that reliably is expensive.
- DerivedData relative to the project (Xcode's "Relative" location or `-derivedDataPath build/`): it lives inside the worktree, is already counted in the worktree's size and disappears with it.
- Making Xcode opened from the Dock use compilation caching: it doesn't inherit the agent's shell. Teams that want it everywhere set the build settings in the project.
- Managing the size of the shared compilation cache (`COMPILATION_CACHE_LIMIT_SIZE`); it's only shown.
- Caching for Swift packages, which got no cross-path reuse in the spike.

## Decisions

### A dedicated `xcode` provider
`RepositoryProviders` gains `xcode: XcodeProvider?` with `compilationCache: Boolean`. A separate id keeps the simulator provider (devices for tests) and the build provider (DerivedData, caching) independent, since a repository may want one without the other. The `repository-provisioning` example of an unknown provider changes from `xcode` to `cocoapods`.
Alternative: options of `ios-simulator`, rejected because a repository that only builds with Xcode would have to take simulators too.

### Reading DerivedData through a port
A `DerivedDataSource` port in the domain, implemented by `MacDerivedDataSource` in infrastructure:
- `location()`: `defaults read com.apple.dt.Xcode IDECustomDerivedDataLocation` run as argv through `ShellCommandExecutor`; an absolute existing path wins, otherwise the default location under the user's home. A failed `defaults` (key missing) means the default.
- `folders()`: each direct child with a readable `info.plist` that has a `WorkspacePath`, parsed with the JDK's XML parser (the files are XML; a binary one is skipped as unreadable), with size from `FileManager.directorySize` and `LastAccessedDate`. Xcode records the path as it was opened, through symbolic links (`/tmp/…` for a worktree Git lists as `/private/tmp/…`), so an existing `WorkspacePath` is resolved to its real path before attributing it; a missing one is kept as recorded. Found in the end-to-end validation.
- `isAvailable(path)`: `false` when the path is under `/Volumes/<name>` and that mount point doesn't exist.
- `delete(folder)`: recursive delete of that one child of the location; it refuses any path that isn't a direct child.
Confirmed in Xcode 27's `IDEFoundation`: the keys of the `com.apple.dt.Xcode` domain are `IDEDerivedDataPathOverride` (checked first), `IDECustomDerivedDataLocation`, and, for the compilation cache, `IDECustomCompilationCacheLocation` (and `IDECustomCompilationCacheSize`). Values are paths; a leading `~` is expanded. The compilation cache size is read from `IDECustomCompilationCacheLocation` when set, else from `<DerivedData location>/CompilationCache.noindex`. The per-workspace "Relative" style (`IDEWorkspaceUserSettings_DerivedDataLocationStyle`) stays out of scope, as above.

### One rule for deletion: orphaned now
`DerivedDataFolder(path, workspacePath, sizeBytes, lastAccessed)` and `DerivedDataOwner`: `Worktree(path)`, `OtherProject`, `Orphaned`, `UnavailableVolume`. `ListDerivedDataUseCase` attributes with `owningWorktree`; a missing `WorkspacePath` is `Orphaned` unless its volume is unavailable. `DeleteOrphanedDerivedDataUseCase` lists again, deletes only folders that are still orphaned and whose path is still absent, and returns a report with deleted folders, bytes freed and failures. A removed worktree's folder becomes orphaned by definition, so the cleanup only needs to run this use case after the removals, like the simulator cleanup re-looks for orphans. There is no separate "folders of removed worktrees" rule.
Orphans are machine-wide on purpose: once its project is gone, a DerivedData folder can't be used by anything, whichever tool created it.

### Compilation cache as a provider resource
With `compilationCache`, the provider contributes a `ResourceRequest` with key `xcode-compilation-cache` whose `create` writes `~/.shogunai/xcode/compilation-cache.xcconfig` (five `KEY = YES` lines, replaced atomically) and returns its path, and whose `exists` checks the file is there with that content. Its variable is `XCODE_XCCONFIG_FILE=<path>`. Being a resource, it's created by provisioning, the hooks and `shogun env`, re-created if deleted, and read-only listings don't write files. The file is per machine and identical for every repository, so concurrent writers write the same bytes.
Alternative: writing the file inside each worktree (via `envFile`-like logic) would put a generated file in the checkout.

### Section on the worktree screen
`WorktreeViewModel` loads a `DerivedDataState` (`Loading`, `Loaded(folders, orphans, cacheBytes)`, `Unavailable`) only for repositories that declare the provider and only on list loads, not on the auto-refresh checks: measuring gigabytes of DerivedData on every check would be too expensive. The section lists the worktrees' folders (worktree name, size, last access), the repository total, the orphans' count and size with "Delete orphans" (disabled without orphans), folders on unavailable volumes as information, and the shared compilation cache size when caching is declared. Strings in English and Spanish.

### Cleanup option and CLI
The cleanup dialog gets a checked "also delete orphaned Xcode DerivedData" option for repositories with the provider, showing the orphans already found; "Clean up" is enabled when there are orphans even without worktree candidates (same as simulators). The report adds the folders deleted and space freed. The CLI adds `shogun xcode` (list), `shogun xcode clean` (delete orphans) and `shogun clean --delete-derived-data`, which lists the orphans already found without `--yes` and deletes the orphans after removing with it.

## Risks / Trade-offs

- **Deleting the wrong folder** is the main risk. → Only direct children with an `info.plist` and a `WorkspacePath`, only when that path is absent at deletion time and not on an unmounted volume, never the shared caches; covered by unit tests on a fake source and an end-to-end check with a real orphan.
- **A project moved, not deleted**, leaves an orphan whose content Xcode wouldn't reuse anyway (the folder name hashes the old path). → Deleting it is safe.
- **Measuring size is slow** for large folders. → Measured off the main thread, only on list loads and CLI calls.
- **`XCODE_XCCONFIG_FILE` replaces one the user set**, and Xcode opened from the Dock doesn't see it. → Documented; a value declared in `env` wins over the provider's.
- **Compilation caching is an Xcode 26+ feature** and its cache grows without ShogunAi bounding it. → Documented; the section shows the cache size so it's visible.
- **The preference key for a custom location** comes from reading Xcode, not from documentation. → Verified in the first infrastructure task; if it differs, this design is updated before implementing.
