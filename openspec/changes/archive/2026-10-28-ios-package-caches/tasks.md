# Tasks

## 1. Configuration

- [x] 1.1 Add `cocoaPods` to `XcodeProvider`, read and write it in `RepositoryProvisioningFiles` (optional boolean, invalid when not a boolean, omitted when false); verified by `RepositoryProvisioningFilesTest` cases for reading, writing and the invalid value
- [x] 1.2 Add the CocoaPods switch under the Xcode provider in the repository configuration section (`XcodeDraft`, `RepositoryConfigSection`, English and Spanish strings); verified by a view model test that turns it on, saves it and reads it back into the draft

## 2. Cloning pods

- [x] 2.1 Add `PodsPreparation`, `PodsInstallNeeded` and their reasons to the domain, and `PreparedWorktree` as the result of `WorktreeEnvironmentPreparer.prepare`; carry it into `Worktree.pods` and `ProvisionResult.pods`; existing creation and provisioning tests still pass
- [x] 2.2 Implement `PrepareCocoaPodsUseCase` (tracked `Podfile.lock` via `git ls-files`, compare with the base's `Pods/Manifest.lock`, clone with `cp -c -R`, remove a partial clone, never throw); verified by tests for: option off, not macOS, lock in a subfolder, untracked lock, match cloned, `Pods` already present, base without pods, base differs, clone fails, listing fails
- [x] 2.3 Run it from `PrepareWorktreeEnvironmentUseCase` after the environment file, and wire it in the CLI and the desktop app with the macOS flag; verified by a test that a creation flow reports cloned pods and that a post-creation command runs after them, and by a test that a pods failure doesn't roll the worktree back

## 3. Reporting pods in the CLI

- [x] 3.1 Print the pods report in `shogun new` and `shogun provision` (text lines and `pods` in JSON) and as stderr notes in the worktree creation hook; verified by `ShogunCliTest`/`HookCommandsTest` cases for text, JSON and the hook

## 4. Swift packages in DerivedData

- [x] 4.1 Add `DerivedDataSource.packagesSize` for the `SourcePackages` size, carry `packagesBytes` through `MeasuredSize`, `RememberedSize`, `DerivedDataFolder` and `JsonDerivedDataSizeStore` (entries without it are measured again); verified by `MacDerivedDataSourceTest`, `MeasureDerivedDataUseCaseTest` and `JsonDerivedDataSizeStoreTest` cases
- [x] 4.2 Add the `PackageCacheSource` port and its Mac implementation, and return `packageCaches` from `ListDerivedDataUseCase`; verified by tests for both caches present, a missing cache and the quota usage unchanged
- [x] 4.3 Show the packages size per worktree folder and the shared caches in `shogun xcode` text and JSON (`sourcePackagesBytes`, `swiftPmCacheBytes`, `cocoaPodsCacheBytes`); verified by CLI tests
- [x] 4.4 Show the packages size next to each worktree's size and the shared caches in the desktop app's DerivedData view, measured by the machine resources holder with the compilation cache; verified by `MachineResourcesTest` and `XcodeDerivedDataViewUiTest` cases

## 5. Documentation and verification

- [x] 5.1 Document the `cocoaPods` option, the packages size and the shared caches in `docs/guide/providers`, `docs/guide/configuration`, `docs/guide/cli` and the architecture pages that list the Xcode ports, English and Spanish
- [x] 5.2 Run `./gradlew test` green, and check on this Mac with a sample CocoaPods repository that `shogun new` clones `Pods` (sharing blocks) and reports a differing lock, and that `shogun xcode` shows the packages and caches lines

## Workflow follow-up

- Archive the change with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md`.
- Implementation commit, branch up to date with `origin/develop`, pull request against `develop` assigned to CiroFeliu.
