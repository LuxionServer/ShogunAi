## 1. Shared rule

- [ ] 1.1 Write failing tests for `holdsChangesOf` (rebase count `0`, squash commit count `0`, unmatched counts, failed or unreadable `merge-base`, `commit-tree` and `rev-list` output, the fixed environment of `commit-tree`) and implement it in `core` until they pass with `./gradlew :core:test`

## 2. Branch deletion

- [ ] 2.1 Write a failing regression test in `DeleteMergedBranchUseCaseTest` and `CleanUpWorktreesUseCaseTest` for a squash-merged and a rebase-merged branch being kept, make `deleteBranchIfContained` use `holdsChangesOf` after a failed `--is-ancestor`, and verify the tests pass with `./gradlew :core:test`

## 3. Worktree status

- [ ] 3.1 Write failing regression tests in `ReadWorktreeStatusUseCaseTest` and the `WorktreeStatus` tests for a squash-merged branch reading as unmerged (main branch, recorded base, base equal to the main branch, a branch not behind and one not ahead skipping the check), add `mainBranchHoldsChanges`/`baseHoldsChanges` and use them in `isMergedIntoBase`/`isMergedIntoMainBranch`, and verify the tests pass with `./gradlew :core:test`
- [ ] 3.2 Add a `selectCleanup` test selecting a clean squash-merged worktree and verify it passes

## 4. Documentation

- [ ] 4.1 Describe the rule in `docs/architecture/domain.md` and `docs/architecture/domain.es.md` (`WorktreeStatus` and the branch deletion section) and verify both pages say the same

## 5. Integration

- [ ] 5.1 Run `./gradlew test koverVerify` and verify it passes with `core` and `cli` at 100% of lines and branches
- [ ] 5.2 Check the rule end to end with the CLI built from this branch on a scratch repository with a squash-merged worktree: `shogun clean` lists it and `shogun clean --yes --delete-branches` removes it and deletes its branch

## Workflow follow-up

- Bring in `origin/develop`, resolve conflicts and re-run `./gradlew test koverVerify`.
- Archive the change (`/opsx:archive`) and update `docs/roadmap.md` and `docs/roadmap.es.md` (drop point 7's first item).
- Open the pull request against `develop` with `--assignee CiroFeliu`.
