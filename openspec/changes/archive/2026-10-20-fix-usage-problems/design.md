# Design

## Context

`RemoveWorktreeUseCase` runs `git worktree remove` and then `git branch -d`, and any failure of the second command is thrown as the removal's failure. `DeleteMergedBranchUseCase` and the cleanup already delete branches through `deleteBranchIfContained`: `git merge-base --is-ancestor <branch> <main>` against the project's main branch, then `git branch -D`. The CLI's `shogun remove --delete-branch` and the hooks already use that check; only the desktop single removal still relies on `-d`, which compares with HEAD and the upstream rather than the main branch.

The grouping selector is rendered by `GroupingSelector` in `WorktreeScreen.kt` for every view. Buttons come from `ui/components/Buttons.kt` plus a few direct Material buttons.

## Goals / Non-Goals

**Goals:**
- One merged check for every way of deleting a branch.
- A removal never reports failure for something that happened after the worktree was gone.
- The user can still delete an unmerged branch, explicitly.

**Non-Goals:**
- Changing which branches the cleanup deletes.
- Redesigning the screens' layouts.

## Decisions

**Branch outcome as part of the removal result.** `WorktreeRemoval` gains `branch: BranchDeletion?` (null when no deletion was requested), a sealed type: `Deleted`, `KeptNotMerged`, `KeptFailed(reason)`. `RemoveWorktreeUseCase` keeps its `branchToDelete` parameter and gains `forceBranchDeletion`. The use case returns success once the worktree is removed. Alternative considered: a second use case called by each caller after the removal. Rejected because the desktop app, the CLI and the hooks would each repeat the sequencing and the Herdr close in between; `DeleteMergedBranchUseCase` stays as the shared building block.

**Merged check reused, not duplicated.** The non-forced path calls the internal `deleteBranchIfContained` extended to return the outcome (deleted, not merged, or Git's error) instead of a boolean; `DeleteMergedBranchUseCase` keeps returning `Result<Boolean>` over it so its callers don't change.

**Forced deletion.** A new `DeleteBranchUseCase(config, executor)(branch, force)` runs `git branch -D` after rejecting option-like refs. The desktop notice and `--force-delete-branch` use it; `RemoveWorktreeUseCase(forceBranchDeletion = true)` uses it too, so a forced deletion right at removal is possible from the CLI.

**Desktop outcome notice.** When the branch is kept, `ProjectWorktrees` closes the dialog, reloads, and keeps a `keptBranchNotice` (branch and reason) that the worktree screen shows as a dismissible notice with a "Delete branch anyway" action. That action opens a confirmation naming the branch and warning that unmerged commits may be lost, and then calls the forced deletion. The notice is state of the project's holder, like `removeError`.

**CLI.** `shogun remove` already prints "kept branch … (not merged …)". It adds `--force-delete-branch`, prints Git's reason when a deletion of a merged branch fails, and adds `branchKeptReason` to the JSON next to `branchDeleted` (fields are only added). Exit code stays 0 for a kept branch.

**Grouping in a project's view.** `GroupingSelector` isn't rendered when `board.isProjectView`, and the board's effective grouping in that view is `BY_STATE` regardless of the stored choice, which stays untouched for the other views. Alternative considered: keep the selector with only "by state". Rejected because a one-option selector is noise.

**Single-line buttons.** The shared button components set `maxLines = 1` and `softWrap = false` on their label through a common label style, plus `minWidth` on the button, so the label always has its intrinsic width. Layouts where a row of buttons could overflow (dialog action rows, group headers, detail panels) get `FlowRow` or a weighted flexible neighbour so the button keeps its width and the neighbour shortens. The sweep lists every `Button`, `TextButton`, `OutlinedButton` and `DropdownMenu`-triggering button, checks its Spanish string length, and fixes the ones in tight containers. Tests measure `TextLayoutResult.lineCount` through the semantics `GetTextLayoutResult` action.

## Risks / Trade-offs

[Forcing `maxLines = 1` could cut a label in a very narrow container] → keep the minimum width on the button and give the container room; the narrow-window test covers the main screens.
[A branch merged by squash is never "contained" so it's always kept by default] → that is the intended safe default; the forced deletion is one confirmation away.
[Changing `deleteBranchIfContained` affects cleanup] → its behaviour is unchanged and its existing tests must keep passing.
