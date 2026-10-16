# Design

## Context

`WorktreeViewModel` already holds `statuses` (per-path `WorktreeStatusState`) and the resolved `mainBranch`, and `WorktreeStatus.isMerged` tells whether a branch is contained in it. `parseWorktreeList` reads `git worktree list --porcelain` but ignores its `locked` and `prunable` lines. `RemoveWorktreeUseCase` removes one worktree and deletes its branch with `git branch -d`, failing the whole call if the branch deletion fails. Requirements are in `specs/worktree-cleanup/spec.md`.

## Goals / Non-Goals

**Goals:**
- One action that removes merged, clean worktrees and prunes missing ones, never touching locked worktrees or uncommitted work.
- Per-worktree outcomes: one failure doesn't stop the rest and is reported.

**Non-Goals:**
- Choosing individual worktrees in the dialog; the selection rule is fixed.
- Forcing removal. A worktree that can't be removed without `--force` is reported, and the existing per-row remove dialog stays the way to force it.
- Cleaning up branches without a worktree.

## Decisions

### `isLocked` and `isPrunable` come from the porcelain output

`parseWorktreeList` sets `isLocked` on a `locked` line (with or without a reason) and `isPrunable` on a `prunable` line. Git already knows both; asking it is cheaper and more reliable than checking folders ourselves. Claude Code locks the worktrees of its sessions, so `isLocked` doubles as "an agent is working here".

### Selection is a pure domain function

`selectCleanupCandidates(worktrees, statuses: Map<String, WorktreeStatus>)` returns the worktrees to clean up following the spec: not main, not locked, and either prunable or merged with zero uncommitted changes. The ViewModel passes only loaded statuses, so loading or unavailable ones are excluded by construction. Keeping it pure makes the rule testable without a ViewModel.

### `CleanUpWorktreesUseCase` handles each worktree independently

`CleanUpWorktreesUseCase(config, executor)` takes the candidates, the `MainBranch?` and `deleteBranches`, and returns a `CleanupReport(removed, keptBranches, failures)`, where `failures` pairs a worktree path with its `WorktreeError`. For each existing worktree it runs `git worktree remove <path>` (no `--force`); if any candidate is prunable it runs `git worktree prune` once and counts those as removed. A failing step is recorded and the loop continues; the use case itself only fails if something unexpected throws.

*Alternative considered*: calling `RemoveWorktreeUseCase` in a loop. Rejected because it deletes branches with `-d` and fails the whole call when that fails, which is the wrong behavior for a batch.

### Branch deletion re-checks containment and uses `-D`

When `deleteBranches` is set, after removing a worktree the use case runs `git merge-base --is-ancestor <branch> <main ref>` and, only if it succeeds, `git branch -D <branch>`. `git branch -d` checks against the base repository's `HEAD` (or the branch's upstream), so in a git-flow repository a branch merged into `origin/develop` would be refused while `main` is checked out. Re-checking against the same main branch the status used, right before deleting, keeps `-D` safe. A branch that fails the check, or without a main branch, is kept and reported.

### UI

- `WorktreeViewModel` exposes `cleanupCandidates` (derived from `worktrees` and loaded `statuses`), `isCleanupDialogOpen`, `cleanupDeleteBranches`, `isCleaningUp` and `cleanupReport`. Confirming runs the use case, stores the report, closes the dialog and refreshes the list.
- `WorktreeScreen` adds a "Clean up" button next to the "Worktrees" title, enabled when there are candidates. The dialog lists candidate paths and the branch checkbox, and shows a progress indicator while running. The report is shown in the existing success/error feedback area: a success message with the count, and the failures and kept branches as text below it.
- `WorktreeStatusLine` shows "folder missing" for prunable worktrees, and the row shows a "locked" label for locked ones.

## Risks / Trade-offs

- [The status used to select candidates can be stale by the time the user confirms] → Removal never forces, so new uncommitted changes make Git refuse; branch deletion re-checks containment.
- [A branch merged by squash isn't detected as merged, so its worktree isn't offered] → Same limitation as the status; documented.
- [`git worktree prune` also prunes missing worktrees that weren't listed] → Only missing entries are pruned, which is what the action promises; locked missing worktrees are skipped by Git itself.
