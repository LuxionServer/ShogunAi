# Proposal

## Why

Creating a group (`shogun new <id>` in a multirepo, or "New change" in the desktop app) checks each repository's local files just before creating that repository's worktree. When the second repository has an unavailable local file, the first repository's worktree is already created and stays, leaving a half-made group the user has to clean up or complete by hand after fixing the file. A single worktree already fails before changing anything; a group should too.

## What Changes

- Before creating any worktree of a group, ShogunAi checks the local files of every repository that will get a new worktree, with the same check a single `shogun new` runs (`local-files-preflight`).
- When any of them has unavailable local files, no worktree, branch or fetch is made in any repository: each repository with unavailable files is reported as failed with its `LocalFilesUnavailable` error, the other repositories that would have been created are reported as not created, repositories already on the branch are still reported as already in the group, and the command exits with code 1.
- A new per-repository result, `skipped`, for repositories left uncreated because another repository of the group has unavailable local files: in the command line output, in `--json` and in the desktop "New change" dialog (English and Spanish).
- Repositories already in the group aren't checked, and the other failures of a repository (no base branch, destination taken, post-creation command) keep behaving as today: they don't stop the other repositories.
- Roadmap item 6 loses its first bullet.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `multirepo-groups`: a new requirement checks every repository's local files before creating any worktree of a group; the JSON result of group creation gains the `skipped` result.
- `multirepo-desktop`: "New change" reports the repositories with unavailable local files and the ones left uncreated, and creates nothing.

## Impact

- `core`: `CreateGroupUseCase` (a check phase before creating, a new `GroupRepositoryOutcome.SKIPPED`), reusing `checkLocalFiles` from `WorktreeCreation.kt`.
- `cli`: the per-repository line for a skipped repository in `shogun new`; `groupCreationJson` prints `skipped`.
- `desktopApp`: the "New change" dialog's label for a skipped repository, in `Strings`, `EnglishStrings` and `SpanishStrings`.
- Docs: `docs/roadmap.md` and `docs/roadmap.es.md`.
