# Tasks

## 1. Resolve recorded bases

- [ ] 1.1 Extract `resolveBranchRef(name)` (`origin/<name>` first, local otherwise) from `ResolveMainBranchUseCase` and use it there unchanged
- [ ] 1.2 Add `ResolveRecordedBasesUseCase` returning `Map<branch, MainBranch>` from one `ReadBaseBranchesUseCase` read, resolving each distinct base once and leaving out unresolvable ones; empty map on read failure
- [ ] 1.3 Tests: base on `origin`, only local, missing, shared base resolved once, read failure

## 2. Status against the base

- [ ] 2.1 Add `base: Divergence?` and `baseName: String?` to `WorktreeStatus`, with `isMergedIntoBase`, `isMergedIntoMainBranch`, `isMerged` as their union, and `hasOwnCommits` from `base`
- [ ] 2.2 `ReadWorktreeStatusUseCase(worktree, mainBranch, base = mainBranch)`: read the base divergence (reusing the main one when they're the same branch, skipping the worktree's own branch) and compute `neverCommitted` when either comparison reads as merged
- [ ] 2.3 Tests in `ReadWorktreeStatusUseCaseTest` and `WorktreeOverviewTest`: merged into base only, into main only (base deleted), not started after the base moves, epic branch without commits is New with creation activity date, no record behaves as before

## 3. Branch deletion

- [ ] 3.1 `deleteBranchIfContained` reads the branch's recorded base before deleting, resolves it and deletes when the branch is an ancestor of the base or of the main branch
- [ ] 3.2 Tests in `DeleteMergedBranchUseCaseTest`, `RemoveWorktreeUseCaseTest`, `CleanUpWorktreesUseCaseTest`: contained in base only → deleted; contained in neither → kept; no record → main branch rule

## 4. CLI

- [ ] 4.1 `shogun status` and `shogun clean` resolve recorded bases and pass each worktree's base to the status read
- [ ] 4.2 `shogun status` line adds `base-ahead=N base-behind=M of <base>`; `--json` adds `status.base` `{branch, ahead, behind}` or `null`
- [ ] 4.3 Multirepo: `ReadGroupsUseCase` resolves per repository the main branch and recorded bases, `GroupWorktreeState.base` is the worktree's effective base, and group lines/JSON use `status.base`
- [ ] 4.4 Tests in `ShogunCliTest`, `HookCommandsTest`, `MultirepoCommandsTest` and `GroupStateAndContextTest` for the scenarios of the delta specs

## 5. Desktop app

- [ ] 5.1 `ProjectWorktrees` resolves recorded bases with the main branch and passes each worktree's base to `readStatus`, also for single-worktree reads
- [ ] 5.2 Row Git cell and detail show `status.base` and `baseName`; the merged tooltip/chip names the branch it's merged into
- [ ] 5.3 `WorktreeBoard.groupCleanupBlocker` passes each worktree's effective base
- [ ] 5.4 Tests for the row model and `statusSegments`

## 6. Validation and docs

- [ ] 6.1 `./gradlew test` green, without warnings
- [ ] 6.2 Check the real CLI in a scratch repository: feature from an epic merged into the epic shows `merged`, is offered by `shogun clean`, and `shogun clean --yes --delete-branches` deletes it
- [ ] 6.3 Update `docs/guide/cli.md`, `docs/guide/desktop-app.md`, `docs/guide/multirepo.md` and their `.es.md` translations where they describe merged state, cleanup or `status`
