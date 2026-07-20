## Context

`WorktreeScreen.kt` currently wires the remove button directly to `viewModel.remove(worktree, worktree.branch)`, so the local branch is always deleted with no confirmation. Separately, `WorktreeViewModel` already has a `pendingForceRemoval` state that shows an `AlertDialog` only when `git worktree remove` fails because of uncommitted/untracked changes, offering a force retry. The domain layer (`RemoveWorktreeUseCase`) already accepts a nullable `branchToDelete` and a `force` flag, so no domain change is needed.

## Goals / Non-Goals

**Goals:**
- Every worktree removal goes through a confirmation dialog before any Git command runs.
- The dialog lets the user opt in to deleting the local branch via a checkbox, unchecked by default.
- The force-retry path (triggered when Git reports uncommitted/untracked changes) reuses the same dialog and the user's branch-deletion choice, instead of being a separate, disconnected dialog.

**Non-Goals:**
- Persisting the checkbox choice across removals or app restarts (each removal starts unchecked).
- Changing branch-deletion safety semantics — `RemoveWorktreeUseCase` still uses `git branch -d` (safe delete), never `-D`.
- Bulk removal of multiple worktrees at once.

## Decisions

- **Unify the two dialogs into one pending-removal state.** Replace `pendingForceRemoval: PendingForceRemoval?` with `pendingRemoval: PendingRemoval?`, where `PendingRemoval(val worktree: Worktree, val deleteBranch: Boolean, val requiresForce: Boolean = false)`. Clicking remove sets this state (`requiresForce = false`); a force-eligible failure updates it in place (`requiresForce = true`), preserving `deleteBranch`. This avoids two divergent code paths and two dialog composables that would need to stay in sync.
  - *Alternative considered*: keep two separate dialogs (confirm dialog + force dialog), passing the checkbox value between them. Rejected because it duplicates dialog layout/copy and adds a second piece of state to keep synchronized.
- **Checkbox defaults to unchecked.** Matches the recommendation already agreed with the user: branch deletion is harder to reverse than worktree removal, so the safer default requires an explicit opt-in.
- **The checkbox stays editable even after escalating to the force-retry dialog.** Simpler than freezing the choice once uncommitted changes are detected, and there is no reason force-retry should also lock the branch decision.
- **No checkbox shown when `worktree.branch` is `null`** (detached HEAD), since there is no branch to delete.
- **`WorktreeViewModel` gains `requestRemoval(worktree)` instead of the screen calling `remove(...)` directly.** The screen no longer decides `branchToDelete` — it only opens the confirmation UI; the ViewModel computes `branchToDelete` from `pendingRemoval.deleteBranch` when the user confirms.

## Risks / Trade-offs

- [Renaming `pendingForceRemoval`/`confirmForceRemoval`/`dismissForceRemoval` breaks `WorktreeViewModelTest.kt`] → Update the existing tests as part of this change; no external API depends on these names.
- [An extra click is now required for every removal, including ones where the user never wanted the branch kept] → Accepted trade-off per the earlier UX discussion: confirmation before a destructive, hard-to-reverse action is preferred over the previous instant-delete behavior.

## Migration Plan

Single PR, no data migration or feature flag: this only changes UI wiring and adds one dialog. Roll out through the normal PR flow (`develop` → `main`). No rollback concerns beyond reverting the PR.

## Open Questions

None outstanding — checkbox default and dialog unification were confirmed with the user before this design was written.
