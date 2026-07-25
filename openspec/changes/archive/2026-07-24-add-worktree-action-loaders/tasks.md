## 1. ViewModel state

- [x] 1.1 Add `isCreating` state to `WorktreeViewModel` (`ui/worktree/WorktreeViewModel.kt`), set to `true` before invoking `CreateWorktreeUseCase`/`CreateWorktreeFromBranchUseCase` and cleared in a `finally` block regardless of success/failure
- [x] 1.2 Wire `isCreating` into the existing `create(...)` function so both call sites (new-branch and existing-branch creation) share the same flag
- [x] 1.3 Add `isRemoving` state to `WorktreeViewModel`, set to `true` before invoking `RemoveWorktreeUseCase` (both the initial attempt and the force-retry path) and cleared in a `finally` block

## 2. Composable UI

- [x] 2.1 In `WorktreeScreen.kt`, show a `CircularProgressIndicator` and disable the "Crear worktree" confirm button and the new/existing-branch mode toggle while `isCreating` is `true`, following the same layout pattern used for `isLoading`/"Recargar"
- [x] 2.2 In the removal confirmation dialog (including its force-retry state), show a `CircularProgressIndicator` and disable both the confirm and cancel buttons while `isRemoving` is `true`

## 3. Verification

- [x] 3.1 Run the app (`./gradlew :desktopApp:run` or hot reload) and manually verify: creating a worktree (both modes) shows the spinner and disables the confirm button/mode toggle until it completes, and removing a worktree (including a forced retry after an uncommitted-changes failure) shows the spinner and disables both dialog buttons until it completes
- [x] 3.2 Verify the loading state clears correctly on failure paths too (e.g. `WorktreeError.WorktreeAlreadyExists`, a removal needing `--force`), not just on success
- [x] 3.3 Run `./gradlew test` to confirm no regressions
