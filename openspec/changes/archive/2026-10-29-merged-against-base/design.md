# Design

## Context

Every branch ShogunAi creates records its base in `branch.<branch>.gh-merge-base` (`BaseBranches.kt`, written by `WorktreeCreation`). Today the record is only displayed (`shogun base`, `base=` in `shogun status`). Every "is it merged?" decision compares with the main branch resolved by `ResolveMainBranchUseCase`:

- `ReadWorktreeStatusUseCase` computes the `mainBranch` divergence, and `WorktreeStatus.isMerged`/`neverCommitted`/`hasOwnCommits` derive from it.
- `selectCleanup` (repository) and `selectGroupCleanup`/`selectLooseCleanup` (multirepo) select on `isMerged`.
- `deleteBranchIfContained` runs `git merge-base --is-ancestor <branch> <main>` for `shogun clean --delete-branches`, `shogun remove --delete-branch`, the `WorktreeRemove` hook and the desktop app.
- `ReadGroupsUseCase` passes each repository's main branch as the group worktree's `base`, and the desktop row/detail show the main branch divergence.

Feature branches of an epic and stacked pull requests are therefore never "merged" until their base reaches the main branch.

## Goals / Non-Goals

**Goals:**
- One rule everywhere: merged = merged into the recorded base **or** into the main branch.
- Divergence shown against the worktree's base, with its name.
- Branches without a record, or whose base is gone, behave exactly as today.
- `shogun status --json` only gains fields.

**Non-Goals:**
- Detecting squash or rebase merges.
- Recording bases for branches that have none (that's `shogun base --set`).
- Changing how a multirepo group's worktrees are created or which base they start from.

## Decisions

### A worktree's base is a resolved ref, defaulting to the main branch

A new `ResolveRecordedBasesUseCase(config, executor)` returns, per branch, the `MainBranch(name, ref)` of its recorded base: one `git config --get-regexp` (through `ReadBaseBranchesUseCase`) plus one ref check per **distinct** base (`origin/<base>` first, local `<base>` otherwise), mirroring `ResolveMainBranchUseCase`. That resolution is extracted to a shared `resolveBranchRef(name)` so both use cases resolve identically. Unresolvable bases are left out, and a failed read yields an empty map. Callers fall back to the main branch: `base = recordedBases[branch] ?: mainBranch`.

`MainBranch` stays the name of the type (it's a named ref); renaming it would touch dozens of files for no behaviour change.

Alternative considered: let `ReadWorktreeStatusUseCase` read `git config` for its own branch. Rejected: one extra Git command per worktree on every desktop refresh, and the existing spec already requires reading bases with one command.

### `WorktreeStatus` keeps `mainBranch` and gains `base`

`ReadWorktreeStatusUseCase(worktree, mainBranch, base = mainBranch)` reads:
- `mainBranch: Divergence?` against the main branch, unchanged.
- `base: Divergence?` against `base` (skipped when `base` is the worktree's own branch); when `base` is the main branch the main divergence is reused instead of running Git twice.
- `baseName: String?`, the name of the branch compared with.

Derived properties:
- `isMergedIntoBase` / `isMergedIntoMainBranch`: `ahead == 0 && behind > 0` on each divergence, and not `neverCommitted`.
- `isMerged = isMergedIntoBase || isMergedIntoMainBranch`.
- `neverCommitted` is computed when either comparison would read as merged.
- `hasOwnCommits` uses `base` (ahead > 0), falling back to the current rule; so a branch of an epic with no commits reads as new, and its activity date is its creation.

The UI and JSON names the branch it is merged into: `baseName` when merged into the base, otherwise the main branch's name.

### Branch deletion checks both refs

`deleteBranchIfContained` receives the main branch as today and, before deleting, reads the branch's own record (`git config --get branch.<branch>.gh-merge-base`) and resolves it with `resolveBranchRef`. It deletes when `merge-base --is-ancestor` succeeds against the base or the main branch. Reading the record must happen before `git branch -D`, which removes the `branch.<branch>.*` section. With neither ref resolvable the branch is kept, as today with no main branch.

### Multirepo groups use each worktree's base

`ReadGroupsUseCase.resolveBase` becomes a per-repository resolution returning the main branch plus the recorded bases (`RepositoryBases(main, recorded)`); each `GroupWorktreeState.base` is the worktree's effective base, so the existing `of <base>` label and JSON `base` name it and `ahead`/`behind` come from `status.base`. Group cleanup already uses `isMerged`, so it follows. The desktop board's `groupCleanupBlocker` passes the holder's per-worktree base the same way.

### CLI output

- `shogun status` line: after the upstream's `ahead=… behind=…`, add `base-ahead=N base-behind=M of <base>` (chosen by the user over replacing the upstream counts, so scripts reading `ahead=` keep their meaning).
- `shogun status --json`: `status.base = {"branch", "ahead", "behind"}` or `null`. `mainBranch`, `upstream`, `merged` keep name and type; `merged` now follows the new rule.
- Multirepo JSON: `base`, `ahead`, `behind` come from the effective base.

### Desktop app

`ProjectWorktrees` resolves the recorded bases together with the main branch on every status read (and on single-worktree reads, reusing the last map) and keeps `bases: Map<String, MainBranch>` next to `mainBranch`. The row (`worktreeRowModel`) and detail (`statusSegments`, merged chip) show `status.base` arrows and `status.baseName`, and the merged tooltip names the branch it's merged into.

## Risks / Trade-offs

- A stale local base (no remote, never pulled) can hide a merge → same limitation as the main branch today; remote is preferred when it exists.
- A branch whose recorded base was later reset to an older commit could read as merged into it → unlikely; it also requires `behind > 0`, and the not-started guard still applies.
- A base fast-forwarded onto the branch (ahead 0, behind 0) doesn't read as merged, as with the main branch today → unchanged behaviour, out of scope.
- Extra cost: one `git config` per status batch and one or two `rev-parse` per distinct base, plus one `rev-list` per worktree whose base differs from the main branch.

## Migration Plan

No data migration: branches without a record behave as before. Rollback is reverting the change.

## Open Questions

None.
