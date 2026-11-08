## Context

Two places decide whether a branch is merged, and both only recognize ancestry:

- `WorktreeStatus.isMergedIntoBase`/`isMergedIntoMainBranch` use `Divergence.readsAsMerged()` (`ahead == 0 && behind > 0`) over the `rev-list --left-right --count <ref>...HEAD` counts that `ReadWorktreeStatusUseCase` reads. The app's merged state, `selectCleanup` (app and `shogun clean`), the inactive flag and `shogun status` read `isMerged`.
- `deleteBranchIfContained` (cleanup with branch deletion, `DeleteMergedBranchUseCase` behind `shogun remove --delete-branch`) runs `git merge-base --is-ancestor <branch> <ref>` against the recorded base and the main branch.

A squash or rebase merge leaves the branch ahead of its target, so both say "not merged". ShogunAi supports Git 2.29 or later.

## Goals / Non-Goals

**Goals:**
- One rule, shared by the status and the deletion, recognizing rebase and squash merges with Git 2.29.
- No extra Git process for a branch that is up to date with its target, merged by ancestry, or not behind it.

**Non-Goals:**
- Asking GitHub whether a pull request was merged: it needs the network and `gh`, and works only for GitHub.
- Recognizing a squash whose conflict resolution changed the branch's changes, or a branch merged together with other branches in one squash: their patches differ from the branch's.

## Decisions

### Patch equivalence, not merge simulation

A new internal `holdsChangesOf(executor, workingDirectory, branch, target)` in `core` answers "does `target` already hold `branch`'s changes?":

1. Rebase: `git rev-list --cherry-pick --right-only --count <target>...<branch>` counts the branch's commits without a patch-equivalent commit (same `git patch-id`) in the target. `0` means merged.
2. Squash: otherwise, `git merge-base <target> <branch>` gives the fork point, `git commit-tree <branch>^{tree} -p <fork point> -m squash` builds a commit holding the branch's whole change as one patch, and the same `rev-list` count over `<target>...<that commit>` being `0` means a commit of the target holds that patch.

Each step's output is parsed strictly (an integer, a non-empty object id); a failed or unreadable step means "not merged", the current behaviour.

Alternative considered: `git merge-tree --write-tree <target> <branch>` and comparing its tree with the target's. Tried on a scratch repository, it stops recognizing a squash as soon as the target changes lines next to the branch's (the merge conflicts), and it needs Git 2.38. Patch-ids compare the squash commit's own diff, so later changes to the target don't matter.

Alternative considered: `git cherry`. It gives the same answer as the `rev-list` count but prints one line per commit to parse.

A branch whose commits cancel out builds an empty squash commit, which matches no commit of the target, so it stays unmerged (checked on a scratch repository).

### A deterministic synthetic commit

`commit-tree` writes a commit object. It runs with a fixed author, committer and date (`GIT_AUTHOR_*`/`GIT_COMMITTER_*`, date `@0 +0000`) through `executeWithEnvironment`, so the same branch state always produces the same object id: repeated status reads write it once, it is never referenced, and `git gc` prunes it like any unreachable object. The fixed identity also avoids failing in a repository without `user.name`.

### Where the rule runs

- Status: `ReadWorktreeStatusUseCase` checks a divergence only when it has `ahead > 0` and `behind > 0` (the only case where neither ancestry nor "not started" applies), with `HEAD` as the branch and the target's ref, in the worktree. It stores the result in two new `WorktreeStatus` fields, `mainBranchHoldsChanges` and `baseHoldsChanges` (default `false`; the base's defaults to the main branch's, as `base` defaults to `mainBranch`); when the base is the main branch the result is reused. `isMergedIntoBase`/`isMergedIntoMainBranch` become `!neverCommitted && (readsAsMerged() || <field>)`. `hasOwnCommits` keeps its meaning, so the activity logic, which tests `isMerged` before anything else of a merged branch, shows these as Merged.
- Deletion: `deleteBranchIfContained`'s per-target check becomes "ancestor, or `holdsChangesOf`". After a failed `--is-ancestor` the branch necessarily has commits the target lacks, so no extra count is needed there.

### Cost

The status reads run on every refresh. A branch both ahead and behind costs one `rev-list --cherry-pick` per target, and three more processes when it isn't a rebase merge. `--cherry-pick` computes patch-ids only of the commits in the symmetric difference. That's acceptable for a handful of worktrees; caching by commit ids is left out until it shows up as slow.

### Coverage

No new `@Serializable` class, so `core`'s branch maximum doesn't change. Every new branch is covered by unit tests with the fake executor; the Git behaviour itself was checked on a scratch repository while designing.

## Risks / Trade-offs

- [A squash with conflict resolutions, or of several branches together, still reads as unmerged] → Same as today; the branch can be removed with `shogun remove --force-delete-branch` or by hand.
- [A branch whose every change was cherry-picked into the target reads as merged although no pull request merged it] → Its changes are in the target, so deleting it loses nothing; that's what "merged" means for cleanup.
- [Extra Git processes on each refresh for diverged branches] → Only branches both ahead and behind a target pay it; see Cost.
- [Unreachable commit objects] → One per branch state, deterministic, pruned by `git gc`.
