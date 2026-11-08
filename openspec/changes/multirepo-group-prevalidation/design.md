# Design

## Context

`CreateGroupUseCase` walks the group's members in order and, for each one, lists its worktrees, fetches when asked and calls `CreateWorktreeUseCase` or `CreateWorktreeFromBranchUseCase`. Those use cases run `checkLocalFilesAndResolve` (`WorktreeCreation.kt`) before any Git command, so a single worktree never changes anything when a local file is unavailable. In a group, that check only protects the repository being created: the earlier members are already created when a later one fails with `WorktreeError.LocalFilesUnavailable`, and "A failing repository doesn't undo the others" keeps them.

Both entry points go through the use case: the CLI's `shogun new <id>` in a multirepo (`ShogunCli`), and the desktop's "New change" and "Add repository" (`WorktreeBoard` through `MultirepoActions.createGroup`).

## Goals / Non-Goals

**Goals:**

- No worktree, branch, fetch or provisioning in any repository of a group when one of the repositories to create has unavailable local files.
- Report every repository with unavailable files, each with its own `LocalFilesUnavailable` error, and say which repositories weren't created because of them.

**Non-Goals:**

- Checking anything other than local files ahead of time (base branch, worktrees folder, destination taken, post-creation commands). Those failures keep stopping only their own repository.
- Rolling back worktrees created before a later failure of another kind.

## Decisions

### Two phases in `CreateGroupUseCase`

The use case first plans every member: it lists the member's worktrees and keeps those already on the branch as `EXISTING`, and a member whose listing fails as `FAILED` with that error, as today. For every other member it runs `checkLocalFiles`, the same check the installation check and the creation use cases share. When any member has unavailable files, the use case stops there and returns:

- `FAILED` with `WorktreeError.LocalFilesUnavailable(basePath, files)` for each member with unavailable files,
- `SKIPPED` with `blockedBy` set to the aliases of those members for every other member to create,
- the `EXISTING` and listing-`FAILED` results unchanged.

When every member is available, the second phase creates the members as before. The creation use cases still run their own check, so a file that disappears between both phases fails only its repository, as today. `onRepository` is called once per member in member order in both outcomes.

A member whose check throws (e.g. `git ls-files` fails) is treated as available: its creation runs the same check again and reports the failure as it does today. This keeps the existing error text and the "doesn't undo the others" behaviour for errors that aren't about local files.

Alternative considered: failing the whole `Result` with a new group-level `WorktreeError`. Rejected because the CLI's JSON and the desktop dialog are built around one result per repository, and a per-repository answer keeps telling which repositories were already in the group.

### `SKIPPED` and `blockedBy` instead of a new error type

`GroupRepositoryOutcome` gains `SKIPPED`, and `GroupRepositoryResult` gains `blockedBy: List<String> = emptyList()`. The use case doesn't build a message for skipped repositories; each surface words it:

- CLI: `shogun: app: not created because the local files of api are unavailable` on standard error, after the failed repositories' lines; `--json` prints `result: "skipped"` with `error` set to that same sentence without the `shogun: app: ` prefix, `path` null.
- Desktop: the "New change" dialog labels it with `groupRepositorySkipped(aliases)` ("not created: api has unavailable local files" / "no creado: api tiene ficheros locales no disponibles").

A new `WorktreeError` subtype would force an `ErrorTexts` entry in both error catalogues and the `ErrorDescription` mapping for a message that is never shown as an error on its own.

`GroupCreation.failed` stays `any { FAILED }`, which is true whenever there is a skipped repository, so the CLI exits with code 1 and the desktop dialog stays open with its results.

### Desktop opening with nothing created

With a runner picked, `WorktreeBoard` opens the created worktrees after creating. When the check stops the group, no result has a path; the board skips opening when there is no worktree to open, instead of asking the runner to open an empty group.

## Risks / Trade-offs

- The local files of every member are now resolved twice (check, then creation). `checkLocalFiles` runs `git ls-files` once per pattern set; for a handful of repositories the cost is negligible.
- The check runs before `--fetch`. It reads the base folder only, so fetching doesn't change its outcome.

## Coverage

`core` and `cli` stay at 100% lines and branches; `GroupRepositoryResult` and `GroupRepositoryOutcome` aren't `@Serializable`, so `core`'s missed-branches maximum doesn't change.
