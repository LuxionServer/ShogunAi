## Why

Users had to quit and relaunch the app to see local branches created or updated outside it (e.g. after a `git fetch`/`checkout` in a terminal), because the "Reload" action only re-ran `ListWorktreesUseCase`. Separately, branches already checked out in another worktree were silently dropped from the "Existing branch" dropdown, which made the list look incomplete without explaining why a branch was missing.

## What Changes

- The "Reload" action on the worktree screen now also reloads local branches when the create-worktree form is in "Existing branch" mode, instead of only refreshing the worktree list.
- `ListEligibleBranchesUseCase` is renamed to `ListLocalBranchesUseCase` and **BREAKING**: it no longer excludes branches already checked out elsewhere. It now returns every local branch as a `BranchOption(name, isCheckedOut)`.
- The "Existing branch" dropdown shows all local branches; entries already checked out in another worktree (or the base repository) are rendered disabled with a "(ya tiene un worktree)" label instead of being hidden.

## Capabilities

### Modified Capabilities
- `worktree-workspace`: "List local branches eligible for a new worktree" changes from excluding checked-out branches to listing all local branches with an `isCheckedOut` flag; "Reload the worktree list" gains a scenario where manual reload also refreshes local branches in "Existing branch" mode.

## Impact

- `domain/model/BranchOption.kt` (new), `domain/usecase/ListLocalBranchesUseCase.kt` (renamed from `ListEligibleBranchesUseCase.kt`).
- `ui/worktree/WorktreeViewModel.kt`: `eligibleBranches: List<String>` → `localBranches: List<BranchOption>`; `refresh()` also calls `loadLocalBranches()` when applicable.
- `ui/worktree/WorktreeScreen.kt`: dropdown renders disabled state and warning label for checked-out branches.
- `ui/components/Selector.kt`: `DropdownSelector` gains an `enabled: (T) -> Boolean` parameter (default `{ true }`, preserves the `ProjectConfigScreen` usage).
- `AppContainer.kt`: wiring updated for the renamed use case.
