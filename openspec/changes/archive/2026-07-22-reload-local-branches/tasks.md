## 1. Domain

- [x] 1.1 Add `BranchOption(name, isCheckedOut)` to `domain/model`
- [x] 1.2 Rename `ListEligibleBranchesUseCase` to `ListLocalBranchesUseCase`, returning `List<BranchOption>` for every local branch instead of filtering out checked-out ones
- [x] 1.3 Update `AppContainer.kt` wiring for the renamed use case

## 2. UI

- [x] 2.1 Add `enabled: (T) -> Boolean` to `DropdownSelector` (default `{ true }`, preserves `ProjectConfigScreen` usage)
- [x] 2.2 Rename `WorktreeViewModel.eligibleBranches`/`loadEligibleBranches` to `localBranches`/`loadLocalBranches`
- [x] 2.3 Make `refresh()` also call `loadLocalBranches()` when `createMode == EXISTING_BRANCH`
- [x] 2.4 Render checked-out branches disabled with a "(ya tiene un worktree)" label in `WorktreeScreen`'s dropdown

## 3. Tests

- [x] 3.1 Update `ListLocalBranchesUseCaseTest` to assert `BranchOption` results including checked-out branches
- [x] 3.2 Update `WorktreeViewModelTest` for `localBranches`/`loadLocalBranches` and reload-also-refreshes-branches behavior

## 4. Verification

- [x] 4.1 `./gradlew :desktopApp:test` passes
