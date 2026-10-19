# Design

## Context

See proposal.md for motivation. Relevant current state:

- `ProcessBuilderShellCommandExecutor.execute` reads stdout and stderr with blocking `readText` inside `coroutineScope`, waits with `runInterruptible { waitFor() }` and calls `destroy()` in `finally`, which is only reached after the readers finish, i.e. after the process ends. Stdin is inherited and never closed; there is no timeout.
- `WorktreeCreation` rolls back with `removeIncompleteWorktreeIgnoringResult` on `SecretCopyFailed` and on environment failures, but deletes the branch only in the Git LFS path (`branchToDeleteOnLfsFailure`).
- `ShogunCli.execute` catches `WorktreeError` and `CommandExecutionException` only. `JsonProjectRepository` decodes with `decodeFromString` and throws `SerializationException`; `JsonSettingsFile` swallows decode errors and returns defaults, and `update` is an unlocked read-modify-write.
- `ResolveRepositoryUseCase` requires `git rev-parse --git-common-dir` to end in `/.git`.
- `ListWorktreesUseCase` marks the main worktree with `it.path == config.baseRepositoryPath`.
- `DeleteMergedBranchUseCase` runs `git branch -d`; `CleanUpWorktreesUseCase` uses `merge-base --is-ancestor <branch> <main>` (`deleteIfContained`).
- `DetectRunningAgentsUseCase` is used by the desktop rows and sidebar but not by cleanup selection in either the desktop or the CLI.
- `GitFailureCause.classifyGitFailure` lives in the desktop path only.
- `ProjectConfig.worktreePathFor(id)` keeps `/`; `worktreePathForBranch` replaces it with `-`.

## Goals / Non-Goals

**Goals:**
- No external process outlives its operation or waits for input.
- No failure path leaves state that blocks the retry, and no unexpected failure reaches the user as a stack trace.
- One rule for "safe to delete" across hooks, CLI and desktop.

**Non-Goals:**
- Supporting bare repositories (worktrees of a bare clone): it changes what "base repository" and "copy from base" mean; it goes to the roadmap.
- The "no own commits reads as merged" label, already in the roadmap; this change only makes cleanup safe against it through the agent guard.

## Decisions

**Executor.** Start with `redirectInput(ProcessBuilder.Redirect.from(nullDevice))` (`/dev/null` or `NUL`). Read both streams on `Dispatchers.IO` in `async` blocks; wrap the wait in `withTimeoutOrNull(limit)` when a limit is given. On cancellation or timeout, in a `NonCancellable` block, call `process.descendants().forEach(ProcessHandle::destroyForcibly)` and `process.destroyForcibly()`, which closes the streams and unblocks the readers, then rethrow (`CancellationException`) or throw `CommandExecutionException.TimedOut(command, limit)`. `ShellCommandExecutor` gains `executeWithin(command, timeout, workingDirectory)`, whose default implementation ignores the limit so existing fakes keep working; read-only callers (status, worktree list, rev-parse, adb devices, simctl list, ps) pass a limit (30 s for Git reads, 10 s for device and process listings). Alternative: a global default timeout; rejected, it would kill a legitimate long `npm install` or Gradle build.

**Rollback.** `createWorktreeAt` remembers whether it created the branch (`-b`) and passes that branch to every rollback path, not only the LFS one. Rollback deletes it with `git branch -D -- <branch>` best-effort. A branch that existed before (`--branch` mode) is never in that variable.

**Typed unreadable files.** `JsonProjectRepository` maps decode errors to `ProjectsFileUnreadable(path, reason)`. Before the first write after a failed read, the file is moved to `<name>.unreadable-<yyyyMMdd-HHmmss>`. `JsonSettingsFile` does the same instead of swallowing, and exposes `lastReadProblem` so the desktop can show a one-time notice. Both use a `FileChannel.lock()` on a sibling `<name>.lock` file around read-modify-write, shared by the CLI and the app, with the existing atomic temp-file move inside the lock.

**CLI last-resort catch.** `execute` adds `catch (error: Exception)` after the typed ones (never catching `CancellationException`), printing `shogun: <message or class name>` and returning `EXIT_FAILURE`. Typed errors still get their specific messages.

**Shared Git failure hints.** Move `classifyGitFailure` and its messages from the desktop to `core` so the CLI prints the same hint after Git's message. The desktop's i18n strings stay; the CLI uses English text from `core`.

**Repository layout.** Resolve with `git rev-parse --show-toplevel --git-common-dir --is-bare-repository` in one call. Bare → `WorktreeError.BareRepositoryNotSupported`. Otherwise the main worktree is `core.worktree` of the common Git folder when set (submodules), or `git worktree list --porcelain`'s first entry (`--separate-git-dir`); `ListWorktreesUseCase` shows a submodule's main worktree at its working tree, since Git lists its Git folder. No commits (`rev-parse HEAD` fails) → `WorktreeError.RepositoryHasNoCommits`, raised by `new` instead of the `getOrNull()` fallback.

**Path comparison.** Reuse `sameDirectory` (normalize, `toRealPath` when it exists, case-insensitive on macOS and Windows) in `ListWorktreesUseCase` and anywhere `baseRepositoryPath` is compared with a Git path.

**Cancelling from the terminal.** `ShogunCli.run` registers a shutdown hook that cancels and joins the running command's job, so Ctrl+C or SIGTERM reaches the executor's cleanup instead of killing the JVM and orphaning the child processes.

**One deletion rule.** `DeleteMergedBranchUseCase` resolves the project's main branch with `ResolveMainBranchUseCase` and uses `deleteIfContained` (already used by cleanup), then `git branch -D -- <b>` only when contained. Both hooks call it.

**Agent guard in cleanup.** `CleanUpWorktreesUseCase` selection takes the running agents (from `DetectRunningAgentsUseCase`, run once for the candidates) and moves worktrees with an agent to a `keptForRunningAgent` list. The desktop cleanup dialog shows them disabled with the agent's name; `shogun clean` lists them as kept. Platforms where processes can't be inspected return "unknown", reported once.

**Task ids with `/`.** `worktreePathFor` uses the same `replace('/', '-')` as `worktreePathForBranch`. Existing nested worktrees keep working because listing comes from Git.

**Per-command help and reporting.** `CliCommand` parsing treats `--help`/`-h` in any position after the command as `CliCommand.Help(command)`; the usage text is split per command and the global help joins them (fixing the misaligned `hook worktree-create` line). `status` prints the stored failure reason; `clean` keeps the failed status reads in a `skipped` list. The dry-run hint is built from the parsed flags plus `--yes`, and its wording depends on which kinds of items qualify.

**Emulator early exit.** The detached start keeps the `Process` handle; the boot wait loop checks `isAlive` each poll and, when it's gone, fails with `EmulatorError.ExitedEarly(lastLogLines)` read from the existing emulator log under `~/.shogunai/logs`.

## Risks / Trade-offs

- [Killing descendants could hit processes the user wants to keep, e.g. a daemon started by a post-creation command] → Only on cancellation or timeout of that command, which already means the user gave up on it; Gradle daemons started by builds are detached and not descendants once the build exits.
- [Timeouts too short on slow disks or huge repositories] → Limits only for reads, generous values, and a timeout is reported as "status unavailable", not as a failure of the whole view.
- [File locks on network home folders] → `FileChannel.lock` works on local filesystems; on failure to lock, fall back to the current behavior and log once.
- [Changing the hook deletion rule keeps more branches than before] → Intentional; the note on stderr says why.
