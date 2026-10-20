# Tasks

## 1. Core: branch deletion as its own step

- [ ] 1.1 Return a branch outcome (deleted, kept as not merged, kept with Git's error) from the merged-check deletion shared with `DeleteMergedBranchUseCase`, keeping its boolean API; verify `DeleteMergedBranchUseCaseTest` and the cleanup tests still pass
- [ ] 1.2 Add `DeleteBranchUseCase` with forced deletion and option-like ref rejection; verify with a unit test that it runs `git branch -D` and rejects `--foo`
- [ ] 1.3 Change `RemoveWorktreeUseCase` to use the merged check instead of `git branch -d`, to return success with the branch outcome once the worktree is removed, and to accept `forceBranchDeletion`; verify tests for merged branch, unmerged branch kept with worktree removed, Git failure kept with reason, forced deletion, and failure when the worktree removal itself fails

## 2. CLI

- [ ] 2.1 Add `--force-delete-branch` to `shogun remove`, print the reason of a kept branch, and add `branchKeptReason` to the `--json` output; verify with CLI tests for merged, unmerged kept, forced and JSON cases
- [ ] 2.2 Update `shogun hook worktree-remove` and the Herdr hook to use the shared outcome message; verify their existing tests pass
- [ ] 2.3 Update the CLI docs page in English and Spanish and verify both mention the new flag and field

## 3. Desktop: branch outcome in removal

- [ ] 3.1 Make `ProjectWorktrees.remove` close the dialog and keep a kept-branch notice when the worktree was removed but the branch kept; verify with a `ProjectWorktreesTest` case
- [ ] 3.2 Show the notice with "Delete branch anyway", its confirmation and the forced deletion; add English and Spanish strings; verify with a UI or holder test that confirming deletes the branch and dismissing keeps it
- [ ] 3.3 Update user docs on removing worktrees in both languages if they mention branch deletion

## 4. Desktop: grouping in a project's view

- [ ] 4.1 Hide `GroupingSelector` in a project's view and group by state there without altering the stored choices; verify with `WorktreeBoardTest` cases for the project view and for returning to "All worktrees"

## 5. Desktop: single-line buttons

- [ ] 5.1 Make the shared buttons keep their labels on one line with a minimum width; verify with a UI test using long Spanish labels in a narrow container
- [ ] 5.2 Sweep every direct Material button and tight action row (dialogs, group headers, detail, project settings, update views, tray, palette) and fix those whose English or Spanish label could wrap or be cut; verify with UI tests for the removal and cleanup dialogs and the group header in Spanish, and by checking that no unreviewed button remains in a search of the sources

## 6. Integration

- [ ] 6.1 Run `./gradlew test` and verify all modules pass
- [ ] 6.2 Run the desktop app and the CLI against a scratch repository with a squash-merged branch and verify the removal flow, the notice and the forced deletion end to end

## Workflow follow-up

- Archive the change with `/opsx:archive` after validation, in this branch before opening the pull request.
- Check the base with `git log origin/develop..HEAD` and open the pull request against `develop`.
