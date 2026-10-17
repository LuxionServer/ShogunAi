# Design

## Context

`CreateWorktreeUseCase` builds `git worktree add <path> -b <branch>` and runs it with `workingDirectory = config.baseRepositoryPath` (`WorktreeCreation.kt`), so Git starts the branch at the main checkout's HEAD. The CLI resolves the repository from the current directory (`configFor`, through `ResolveRepositoryUseCase`) but then loses which worktree the user was in. `hookWorktreeCreate` does the same with the hook's `cwd`. The desktop app calls the same use case with no notion of a current worktree.

## Goals / Non-Goals

**Goals:**
- `shogun new <id>` and the WorktreeCreate hook start the branch where `git worktree add -b` run in that worktree would.
- An explicit start point for any ref.

**Non-Goals:**
- A start point in the desktop app's "New worktree" dialog (it keeps the main checkout's HEAD; a base selector would be its own change).
- Changing `shogun new --branch`, which checks out an existing branch and has no start point.

## Decisions

### The use case takes a resolved start point
`CreateWorktreeUseCase.invoke` gains `startPoint: String? = null`, appended after `-b <branch>` when present. The caller resolves it; the use case keeps running Git from the base repository, so folder, provisioning and rollback don't change. Passing `null` keeps today's behavior, which is what the desktop app does.

### Start points are resolved to a commit in the caller's worktree
A new `ResolveStartPointUseCase(executor)(directory, ref)` runs `git rev-parse --verify --quiet <ref>^{commit}` in `directory` and returns the commit id, or fails with `WorktreeError.StartPointNotFound(ref)`. Resolving to a commit id (instead of passing `HEAD` or a branch name) makes `HEAD` mean the caller's worktree even though Git then runs in the base repository, and avoids Git setting an upstream when the ref is a remote-tracking branch. Refs are shared by every worktree of a repository, so `--from <ref>` resolves the same from any of them.

- `shogun new <id>`: `ref` is `--from` or `HEAD`, `directory` is the working directory.
- The hook: `ref` is `HEAD`, `directory` is the input's `cwd` (or the working directory without one).
- When the default `HEAD` can't be resolved (a repository without commits), the start point is left out and Git reports the error as today; an explicit `--from` that can't be resolved always fails before anything is created.

*Alternative considered:* run `git worktree add` from the caller's worktree. Rejected because every path, provisioning and rollback step assumes the base repository as working directory, and the main checkout wouldn't get the same treatment.

### Usage rules
`--from` takes a value and is rejected together with `--branch` (exit code 2, like `--fix`). The help line becomes `new <task-id> [--fix] [--from <ref>]`, saying that the branch starts at the current worktree's HEAD unless `--from` is given.

## Risks / Trade-offs

- [Someone relied on `shogun new` from a linked worktree starting at the main checkout's HEAD] → The new default matches Git and the main checkout is unchanged; `--from develop` restores the old result explicitly. Documented in `cli.md`.
- [A detached HEAD in the caller's worktree] → It resolves to its commit like any other HEAD, which is what Git does.
