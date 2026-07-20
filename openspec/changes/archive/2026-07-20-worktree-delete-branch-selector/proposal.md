## Why

`RemoveWorktreeUseCase` and `WorktreeViewModel.remove` already support an optional `branchToDelete` parameter, but `WorktreeScreen` calls `viewModel.remove(worktree, worktree.branch)` directly on the remove button click, always deleting the local branch with no confirmation and no way to opt out. This risks losing a branch the user did not intend to delete, and skips confirmation entirely for what is otherwise a destructive, hard-to-reverse action.

## What Changes

- Add a confirmation dialog shown when the user clicks remove on a worktree, before any Git command runs.
- The dialog includes a checkbox "Also delete local branch", unchecked by default.
- Confirming the dialog invokes `WorktreeViewModel.remove` with the branch name only if the checkbox is checked, otherwise with `null`.
- The existing force-removal retry dialog (shown when `git worktree remove` fails due to uncommitted/untracked changes) is unified with this confirmation flow: it reuses the user's branch-deletion choice from the initial dialog rather than asking again.
- Canceling the confirmation dialog performs no Git command and leaves the worktree in the list, matching today's cancel behavior for the force-removal dialog.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `worktree-workspace`: the "Remove a worktree" requirement changes so that clicking remove always shows a confirmation dialog with a branch-deletion checkbox (default unchecked) before invoking `RemoveWorktreeUseCase`, instead of removing the branch unconditionally with no confirmation.

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/worktree/WorktreeScreen.kt`: replace the direct `onRemove` call with a confirmation dialog + checkbox state; extend the existing force-removal `AlertDialog` state to carry the user's branch-deletion choice.
- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/worktree/WorktreeViewModel.kt`: no functional change expected (already accepts `branchToDelete`), but its pending-removal state may need to track the user's checkbox choice through the force-retry path.
- No change expected to `RemoveWorktreeUseCase.kt` (domain layer already supports this).
- Tests: `WorktreeViewModelTest.kt` and any Compose UI tests for `WorktreeScreen` covering the new dialog and checkbox behavior.
