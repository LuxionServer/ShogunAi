# Design

## Context

See proposal.md for the motivation. Today `RemoveWorktreeUseCase` looks up the worktree's Herdr workspace, runs `git worktree remove`, and closes the workspace only if Git succeeded. `CleanUpWorktreesUseCase` does the same per worktree with its own copy of the `git worktree remove` / `prune` logic. Git on Windows unregisters the worktree (`.git/worktrees/<name>`) before deleting its folder, so a locked folder leaves a half-removed state: unregistered, folder present, and `git worktree remove` then answers "is not a working tree".

Herdr nests a worktree's workspace under the repository's workspace (`worktree open`). `HerdrClient.openWorktreeWorkspaces` maps a worktree path to its `open_workspace_id`; `closeWorkspace` closes that workspace with every pane in it, including those added with `pane split` (`splitPane` always splits a pane of the worktree's own workspace). `listPanes` returns every pane with its working directory.

## Goals / Non-Goals

**Goals:**
- One removal path shared by the single removal and the cleanup, so both recover the same way.
- Never close Herdr unless a lock blocked the removal.
- Tests with the fake executor, fake Herdr client and fake file manager; nothing needs Windows to run.

**Non-Goals:**
- Finding which program holds the lock (no Restart Manager / handle enumeration) or killing it.
- Changing Unix behaviour: there the first `git worktree remove` succeeds and the new code never runs.
- Retrying more than once or waiting for the lock to clear.

## Decisions

**1. Detect the lock from Git's output with `isFolderInUseFailure`.** The failure is `failed to delete '<path>'` followed by `Permission denied`, `Device or resource busy` or `being used by another process` (`Directory not empty` isn't a lock and isn't matched). It is a separate predicate rather than a new `GitFailureCause`: the outcome is its own typed error, not a generic Git failure with a hint. Alternative: treat every `GitCommandFailed` as a lock and retry — rejected, it would close the user's Herdr session for unrelated failures such as uncommitted changes.

**2. Close after the failure, then retry once.** On `FolderInUse`, the use case closes the worktree's workspace (id looked up before removing, as today), plus any pane from `listPanes` whose directory is inside the worktree and that isn't in that workspace (for example panes the user opened in the repository's workspace and `cd`ed into the worktree). Then it re-runs the removal. Panes of the worktree's workspace are not closed one by one: `workspace close` already covers them, including split ones. Lookup or close failures from Herdr are swallowed into the existing `herdrFailure` warning and never block the retry. Alternative: close Herdr before the first attempt — rejected by the scope (it would kill sessions of worktrees that can't be removed anyway).

**3. Shared `WorktreeRemoval` helper in `core`.** A `RemoveWorktreeFolder` class (internal to `domain/usecase`) takes the executor, `FileManager` and `CloseHerdrWorkspacesUseCase?` and exposes `remove(path, force)` returning which Herdr workspaces it already closed. `RemoveWorktreeUseCase` and `CleanUpWorktreesUseCase` both call it; the later "close the workspace of what was removed" step skips workspaces already closed so a successful retry doesn't report a spurious Herdr error. The cleanup keeps its non-forced removal and its separate `prune` of missing folders.

**4. Half-removed state is resolved by comparing Git's list with the disk.** After a lock failure on the retry, and also when the first attempt answers "is not a working tree", the helper runs `git worktree list --porcelain` (via `ListWorktreesUseCase`). If the path is no longer registered it runs `git worktree prune`, and if the folder exists deletes it with the new `FileManager.deleteRecursively`; if that fails it throws `WorktreeError.WorktreeFolderInUse(path, unregistered = true, …)`. If the folder is already gone it succeeds. If the path is still registered after the retry and still locked, it throws `WorktreeFolderInUse(unregistered = false)`. The deletion is refused (error, nothing deleted) when the path is the base repository or contains a `.git` directory, so a mistyped path to a real clone can never be wiped; a linked worktree's `.git` is a file, so a leftover worktree folder passes the guard.

**5. Typed error with both languages.** `WorktreeFolderInUse(path, unregistered, command, exitCode, errorOutput)` keeps Git's output for the technical details. `EnglishErrors.kt` and `SpanishErrors.kt` explain that a program (terminal, editor, sync client) is using the folder, to close it and remove again, and when `unregistered` that removing again finishes the job. `ErrorDescription` maps it to `TechnicalDetails`. The CLI prints the error's message, which is also in plain English with the path.

**6. Wiring.** `FileManager.deleteRecursively(path)` gets a JVM implementation and a `FakeFileManager` one; `AppContainer` and `ShogunCli` pass their existing file manager to the use cases.

## Risks / Trade-offs

- [Closing a pane the user is working in] The retry closes panes whose directory is inside the worktree; that is exactly what holds the lock, and the confirmation dialog already warns that the worktree's Herdr workspace will close → the existing warning (workspace and agent will be closed) already covers it.
- [The CLI run from a pane inside the worktree closes its own pane] On Windows that pane was the lock anyway → accepted; removal via the CLI from inside the worktree being removed is already unsupported.
- [Git's message differs by locale or version] → match the stable English `failed to delete` plus the OS error strings; an unmatched lock behaves as today (raw Git error), no regression.
- [Deleting a folder Git no longer tracks] → guarded as in decision 4, and only for a path the user asked to remove.
- [Cannot be tested on Windows here] → unit tests drive the exact stderr strings from the report; a Windows user verifies on a build.
