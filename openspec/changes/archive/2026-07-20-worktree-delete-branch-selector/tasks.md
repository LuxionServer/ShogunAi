## 1. ViewModel: unify pending-removal state

- [x] 1.1 In `WorktreeViewModel.kt`, replace `PendingForceRemoval`/`pendingForceRemoval` with a `PendingRemoval(worktree: Worktree, deleteBranch: Boolean, requiresForce: Boolean = false)` state.
- [x] 1.2 Add `requestRemoval(worktree: Worktree)` that sets `pendingRemoval = PendingRemoval(worktree, deleteBranch = false, requiresForce = false)` instead of removing immediately.
- [x] 1.3 Add `setPendingRemovalDeleteBranch(deleteBranch: Boolean)` (or equivalent) to update the checkbox choice while the dialog is open.
- [x] 1.4 Rename `confirmForceRemoval()`/`dismissForceRemoval()` to `confirmPendingRemoval()`/`dismissPendingRemoval()`, computing `branchToDelete = worktree.branch.takeIf { pendingRemoval.deleteBranch }` and passing `force = pendingRemoval.requiresForce`.
- [x] 1.5 Update the failure branch in `remove(...)` so a force-eligible failure sets `pendingRemoval = pendingRemoval.copy(requiresForce = true)` (preserving `deleteBranch`) instead of creating a new `PendingForceRemoval`.
- [x] 1.6 Expose read-only accessors the screen needs: `worktreePendingRemoval: Worktree?`, `pendingRemovalDeleteBranch: Boolean`, `pendingRemovalRequiresForce: Boolean`.

## 2. Screen: single confirmation dialog

- [x] 2.1 In `WorktreeScreen.kt`, change `WorktreeRow`'s `onRemove` wiring so the remove button calls `viewModel.requestRemoval(worktree)` instead of `viewModel.remove(worktree, worktree.branch)`.
- [x] 2.2 Replace the existing force-removal-only `AlertDialog` with a single dialog bound to `viewModel.worktreePendingRemoval`, covering both the initial confirmation and the force-retry state.
- [x] 2.3 Add a checkbox row labeled "Also delete local branch" (or the Spanish UI copy used elsewhere in this screen), bound to `pendingRemovalDeleteBranch`, hidden when the worktree has no branch (detached HEAD).
- [x] 2.4 Vary the dialog's title/body text based on `pendingRemovalRequiresForce` (normal confirmation copy vs. the existing uncommitted-changes warning copy).
- [x] 2.5 Wire the confirm button to `viewModel.confirmPendingRemoval()` and the dismiss/cancel button to `viewModel.dismissPendingRemoval()`.

## 3. Tests

- [x] 3.1 Update `WorktreeViewModelTest.kt` for the renamed state/methods (`requestRemoval`, `confirmPendingRemoval`, `dismissPendingRemoval`, `pendingRemovalDeleteBranch`, `pendingRemovalRequiresForce`).
- [x] 3.2 Add a ViewModel test: confirming with the checkbox unchecked calls `remove` with `branchToDelete = null`.
- [x] 3.3 Add a ViewModel test: confirming with the checkbox checked calls `remove` with `branchToDelete = worktree.branch`.
- [x] 3.4 Add a ViewModel test: a force-eligible failure preserves the previously chosen `deleteBranch` value when escalating to `requiresForce = true`.
- [x] 3.5 Add a ViewModel test: canceling the dialog at any stage runs no further removal command and leaves the worktree in the list.

## 4. Manual verification

- [x] 4.1 Run the app (`./gradlew :desktopApp:run`), remove a worktree with the checkbox unchecked, and confirm the branch still exists afterward (e.g. `git branch --list`).
- [x] 4.2 Remove another worktree with the checkbox checked, and confirm both the worktree and its branch are gone.
- [x] 4.3 Force a dirty-worktree removal (uncommitted change inside the worktree) and confirm the dialog escalates to the force-retry copy while keeping the checkbox state chosen earlier.
- [x] 4.4 Confirm a worktree with a detached HEAD (if reachable in the UI) shows no checkbox.
