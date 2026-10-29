# Proposal

## Why

ShogunAi decides whether a worktree's branch is merged only against the project's main branch, although every branch it creates already records its base (`branch.<branch>.gh-merge-base`). A feature branched from `epic/redesign` and merged into it, or a stacked pull request merged into the branch below it, doesn't count as merged until the epic reaches `develop`, so `shogun clean` and the desktop app's cleanup don't offer it and `--delete-branch` keeps its branch.

## What Changes

- A branch counts as merged when it is merged into its recorded base **or** into the main branch, so it keeps reading as merged after the base reaches the main branch and is deleted.
- The recorded base is resolved like the main branch: `origin/<base>` when it exists, otherwise the local branch. A branch without a record, or whose base no longer exists, is compared with the main branch only, as today.
- The same rule applies everywhere ShogunAi decides "merged": the merged and not-started states, cleanup's selection (repository and multirepo groups), and branch deletion in `shogun clean --delete-branches`, `shogun remove --delete-branch`, the `WorktreeRemove` hook and the desktop app.
- Commits ahead of and behind are shown against the worktree's base (the recorded one when it resolves, the main branch otherwise): the desktop row and detail, the multirepo group lines and JSON, the activity date and "has commits of its own".
- `shogun status` adds `base-ahead=N base-behind=M of <base>` to each line, keeping the upstream's `ahead`/`behind`; `shogun status --json` adds `status.base` with `branch`, `ahead` and `behind`. `mainBranch` and `upstream` keep their meaning.
- Out of scope: detecting squash or rebase merges.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-base-branch`: the recorded base is resolved and used as the comparison point of a worktree, and shown with its divergence in `shogun status`.
- `worktree-status`: merged means merged into the base or the main branch; divergence and activity date are against the base.
- `worktree-row`: the Git cell compares with the base and names it.
- `worktree-cleanup`: selection and branch deletion accept a branch merged into its base.
- `command-line-tool`: `shogun status` lines, `shogun remove --delete-branch` and the `WorktreeRemove` hook follow the new rule.
- `multirepo-groups`: group status and group cleanup compare each worktree with its own base.

## Impact

- Core: `WorktreeStatus` (new base divergence), `ReadWorktreeStatusUseCase`, a resolver for recorded bases next to `ReadBaseBranchesUseCase`, `deleteBranchIfContained` (used by `DeleteMergedBranchUseCase`, `RemoveWorktreeUseCase`, `CleanUpWorktreesUseCase`), `ReadGroupsUseCase`, `WorktreeOverview`.
- CLI: `status`, `clean`, multirepo status and clean, `StatusJson`, `MultirepoJson`.
- Desktop app: `ProjectWorktrees` status reading, `WorktreeRowModel`, `WorktreeDetailPane`, `WorktreeBoard` group cleanup.
- Docs: CLI and worktree status pages (EN and ES), roadmap.
- One extra Git command per status read batch (reading recorded bases) plus one ref check per distinct base; no new dependencies.
