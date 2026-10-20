# Proposal

## Why

Three problems showed up in real use. Removing a worktree reports a failure when only its branch couldn't be deleted (always the case for squash or rebase merged pull requests), although the worktree is already gone. The "by project" grouping is offered in a single project's view, where it groups nothing. And some button labels wrap in the middle of a word or get cut, especially in Spanish.

## What Changes

- Removing a worktree and deleting its branch become two steps with their own results: the worktree is removed, and the branch is deleted, or kept with the reason. The merged check is the one cleanup already uses against the project's main branch, instead of `git branch -d`.
- Unmerged branches can be deleted explicitly with a forced deletion, from the desktop app and the command line.
- The command line and its JSON report a kept branch and why.
- A single project's view no longer offers grouping by project.
- Button labels in the desktop UI stay on one line, in English and Spanish, with minimum widths where the layout needs them.

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-workspace`: removal reports the branch deletion separately, keeps unmerged branches, and offers a forced deletion.
- `command-line-tool`: `shogun remove` reports kept branches, supports a forced branch deletion, and its JSON says why a branch was kept.
- `worktree-views`: the grouping selector is hidden in a project's view.
- `ui-consistency`: button labels don't wrap or get cut.
- `desktop-ui-testing`: UI tests guard the button labels.

## Impact

- `core`: `RemoveWorktreeUseCase`, `DeleteMergedBranchUseCase` and a new forced branch deletion; tests.
- `cli`: `shogun remove` output, flags and JSON.
- `desktopApp`: `ProjectWorktrees`, the removal dialog and its outcome notice, `GroupingSelector`, buttons across screens, localized strings (English and Spanish); UI tests.
- `docs/`: pages describing removal and the CLI, in both languages, if they mention branch deletion.
