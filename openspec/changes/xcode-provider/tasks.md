# Tasks

## 1. Provider declaration

- [ ] 1.1 Add `XcodeProvider(compilationCache)` to `RepositoryProviders` and parse `providers.xcode` in `RepositoryProvisioningFiles` (object required, `compilationCache` optional boolean); change the unknown-provider test example from `xcode` to `cocoapods`; verify with tests for every scenario of "Declare the Xcode provider"

## 2. DerivedData

- [ ] 2.1 Confirm the preference key and value format Xcode uses for a custom DerivedData location by reading Xcode's binaries, and record it in design.md; verify by finding the key next to the location style values
- [ ] 2.2 Add the `DerivedDataSource` port, the `DerivedDataFolder` / `DerivedDataOwner` model and a fake in `testFixtures`; verify `./gradlew :core:compileTestFixturesKotlin` passes
- [ ] 2.3 Implement `MacDerivedDataSource` (location from `defaults` as argv, folders with an XML `info.plist` and `WorkspacePath`, sizes, last access, mounted volumes, deleting only direct children); verify with unit tests on a temporary folder holding real `info.plist` files, shared caches, a binary plist and a non-child path
- [ ] 2.4 Implement `ListDerivedDataUseCase` (worktree, nested worktree, other project, orphaned, unavailable volume) and `DeleteOrphanedDerivedDataUseCase` (fresh check, project restored in the meantime, failures reported, bytes freed); verify with use case tests for every scenario of "Attribute DerivedData to worktrees", "Unmounted volumes are not orphans" and "Delete only orphaned DerivedData"

## 3. Compilation cache

- [ ] 3.1 Add the compilation cache contribution (resource writing `~/.shogunai/xcode/compilation-cache.xcconfig`, `XCODE_XCCONFIG_FILE` variable) to `MachineProviderContributions`, only on macOS and with `compilationCache`; verify with `ResolveWorktreeEnvironmentUseCase` tests: variable and file content, nothing without the option or without reservation, file re-created when deleted, `env` wins

## 4. Command line

- [ ] 4.1 Parse `shogun xcode`, `shogun xcode clean` and `shogun clean --delete-derived-data`, and update `shogun help`; verify with `CliCommand` parsing tests
- [ ] 4.2 Implement the output and exit codes of `xcode` and `xcode clean`, and the orphan deletion after removals in `clean --yes --delete-derived-data` (listing without `--yes`); verify with CLI tests against the fake source for each scenario

## 5. Desktop app

- [ ] 5.1 Load the DerivedData state in `WorktreeViewModel` only for repositories with the provider and only on list loads, with the delete-orphans action; verify with view model tests
- [ ] 5.2 Add the "Xcode DerivedData" section to `WorktreeScreen` with English and Spanish strings; verify with the running app
- [ ] 5.3 Add the checked "also delete orphaned Xcode DerivedData" option to `CleanupDialog`, enable "Clean up" with orphans only, and report the space freed; verify with view model tests and in the running app

## 6. Documentation

- [ ] 6.1 Document the provider in the README (new "Xcode repositories" section) and `docs/agents.md`, including the compilation cache and its limits (Swift packages, Xcode from the Dock, `XCODE_XCCONFIG_FILE` replaced), with the Spanish translations; verify both languages match
- [ ] 6.2 Describe the model, port, use cases, contribution, CLI commands and UI in `docs/architecture/` (domain, infrastructure, cli, ui), in both languages; verify `mkdocs build` passes

## 7. Validation

- [ ] 7.1 Validate end to end on this Mac with a toy `.xcodeproj` repository in the scratchpad declaring `{ "xcode": { "compilationCache": true } }`, with a backup of `~/.shogunai/projects.json` restored afterwards: build two worktrees with `xcodebuild` after `eval "$(shogun env)"` and check the second reports cache hits; check `shogun xcode` and the app section list both; remove one worktree with the cleanup option on and check only its folder is deleted, shared caches untouched; remove every DerivedData folder and cache the validation created
- [ ] 7.2 Run `./gradlew test` and `openspec validate xcode-provider --strict`; verify both pass

## Workflow follow-up

- Archive the change with `/opsx:archive` in the same branch once validated, removing the DerivedData bullet from `docs/roadmap.md` and `docs/roadmap.es.md` and updating the "Android, KMP and JVM" layer row, before opening the pull request to `epic/pivot`; then add the pull request to the project (layer C, step 9).
