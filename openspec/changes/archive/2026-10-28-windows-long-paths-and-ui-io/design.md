# Design

## Context

See proposal.md for the report. Current state of each bug:

- Every external command goes through `ProcessBuilderShellCommandExecutor`, which already adapts Git commands in one place (`GitLocale` sets `LC_ALL=C` and `GIT_TERMINAL_PROMPT=0` when the program is `git`) and Windows arguments (`WindowsArguments.prepare`, driven by an `osName` constructor argument). Nothing sets `core.longpaths`.
- `WorktreeFolderRemoval` runs `git worktree remove` and recognizes two failures: "is not a working tree" (finish the leftover through `finishLeftover`) and a locked folder (close Herdr holders and retry). Anything else, "Filename too long" included, becomes `GitCommandFailed`. Git's `worktree remove` deletes the worktree's administrative directory even when deleting the folder failed, so after this error the worktree is already unregistered and its folder is half-deleted.
- `MachineResources` calls `MachineUseCases` from coroutines launched on the Compose scope (`LaunchedEffect` in `AppShell` and `machineScope`), which runs on the AWT thread. Most `MachineUseCases` lambdas in `AppContainer` call use cases directly, without `withContext(Dispatchers.IO)`, unlike the per-project ports in the same file. Those use cases read files under locks (`JsonSettingsFile`, environment state, reservations) and only the process runs themselves switch to IO inside the executor. The 22 s freeze was `TrimGradleDaemonsUseCase` → `ResolveResourceLimitsUseCase` → `JsonSettingsFile.read` → `LocalFileLock` on the AWT thread.
- `RecordShownError` records a `SHOWN_ERROR` entry from `LaunchedEffect(error)`, so it records again every time the composable re-enters the composition. `CleanupIssues` lives in a `LazyColumn` item (`GroupState` in `WorktreeListPane`), which is disposed and recomposed when it scrolls out and back or when the list items are rebuilt after the cleanup's reload.

## Goals / Non-Goals

**Goals:**
- Git operations on Windows work in worktrees with paths over 260 characters, for every Git command, not only removal.
- A removal that still hits "Filename too long" finishes instead of leaving a half-removed worktree.
- No machine section call blocks the interface thread, and a test fails if one does.
- One diagnostics entry per shown error instance.

**Non-Goals:**
- Changing the user's Git configuration (`git config --global core.longpaths true`) or the Windows registry `LongPathsEnabled` setting.
- Moving the per-project ports of `ProjectWorktrees` that still run on the caller's dispatcher; the report doesn't show them freezing and they're a separate audit.
- The Skiko `Direct3DRedrawer.dispose` and `WGlobalCursorManager` freezes and the `ResourcePoolFull` notice in the same report, which aren't ours or are correct behaviour.

## Decisions

### 1. `-c core.longpaths=true` on every Git command on Windows, added by the executor

`ProcessBuilderShellCommandExecutor` inserts `-c core.longpaths=true` right after `git` when the OS is Windows, through a pure `GitLongPaths.prepare(command, osName)` next to `GitLocale`, before `WindowsArguments.prepare`. The `CommandResult` keeps the command as the caller built it, so error messages, error classification and every existing test that matches commands stay as they are.

- Every Git command, not only `worktree remove`: status, checkout, `add` with a checkout of deep files and `clean` fail the same way in such worktrees, and the executor is already the single place that adapts Git commands.
- `-c` over `GIT_CONFIG_COUNT`/`GIT_CONFIG_KEY_0` environment variables: `-c` works with every Git version and can't clash with variables the user or a hook sets.
- Not writing `core.longpaths` into the repository's or user's config: that changes their setup outside ShogunAi and persists after uninstalling.
- Only on Windows: `core.longpaths` is a Git for Windows setting; elsewhere it's unknown and harmless, but keeping other systems' argv unchanged avoids surprises.

### 2. "Filename too long" finishes the removal through `finishLeftover`

`GitFailureCause.kt` gets `isPathTooLongFailure(stderr)` (Git's message, matched in English since Git runs with `LC_ALL=C`). `WorktreeFolderRemoval` sends that failure, on the first attempt and on the retry after closing Herdr holders, to the existing `finishLeftover`: prune, return if the folder is gone, refuse when it's the base repository or has its own `.git` directory, otherwise `FileManager.deleteRecursively` (JDK NIO prefixes long paths itself on Windows), and an `IOException` becomes `WorktreeFolderInUse(unregistered = true)`.

- The fallback doesn't depend on the OS: the stderr decides, so it's covered by tests with a fake executor and a fake `FileManager` on any machine, and it also catches a Git too old to honour `core.longpaths`.
- Reusing `finishLeftover` keeps one set of safety checks for deleting a folder Git left behind.
- Without a `FileManager` (callers that don't pass one) the error stays `GitCommandFailed`, as for "not a working tree".

### 3. `MachineResources` runs its use cases on an injected IO dispatcher

`MachineResources` takes `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` and every suspend `MachineUseCases` call goes through `withContext(ioDispatcher)`; `measureDerivedData`'s flow gets `flowOn(ioDispatcher)`. State is written back after the call, on the caller's dispatcher (the Compose scope), as before. `resourceSettings` stays a plain read of in-memory Compose state. In `AppContainer`, `DesktopTurnWatch`'s `read` and `forceRelease` switch to `Dispatchers.IO`, since the board also refreshes it.

- At the `MachineResources` boundary rather than wrapping each of the ~25 lambdas in `AppContainer`: one place covers every current and future machine call, and a test can assert it, which wrapping lambdas in the untested container can't. The missing wrapper in the container is how this bug got in.
- Tests pass their test dispatcher as `ioDispatcher`, keeping virtual time deterministic; a new test passes a distinct dispatcher and checks the fake use cases run on it.

### 4. An app-wide `ShownErrorLog` that records each error instance once

`ShownErrorLog(log: DiagnosticsLog, clock)` in `ui/components` remembers the identity of the last 256 errors it recorded and ignores an error it already recorded. `AppContainer` owns one; `main.kt` provides it through `LocalShownErrorLog`, which replaces `LocalDiagnosticsLog` (its only reader was `RecordShownError`). `RecordShownError` keeps its `LaunchedEffect(error)` and calls `ShownErrorLog.record` on IO.

- Identity, not equality or message: the same failure happening again later carries the same message (and, for the `WorktreeError` variants that are data classes, is equal to the first one) and must still be recorded (spec scenario "Same failure later"). The holder keeps the same `Throwable` while it is shown, so identity is exactly "this occurrence".
- App-wide rather than `remember` in the composable: a `LazyColumn` item that leaves the composition loses its remembered state, and the same error can be shown by two composables.
- Bounded so held errors don't grow without limit; 256 is far above how many errors are on screen at once.
- Recording when the holder stores the error was rejected: there are many holders, and the log is meant to hold what the user actually saw.

## Risks / Trade-offs

- [`-c core.longpaths=true` makes Git create paths other Windows tools (Explorer, older build tools) can't open] → Git only creates such paths when the repository's content already has them; the alternative is failing the checkout. Deleting them stays possible through ShogunAi.
- [Deleting the leftover folder recursively is destructive] → Same guards as the existing leftover path (never the base repository, never a folder with its own `.git`), and Git has already unregistered the worktree when it reports this error, so the folder is no longer a worktree.
- [A use case that touches Compose state from inside the IO context] → The `MachineUseCases` lambdas only call domain use cases and return values; `resourceSettings` (Compose state) is not suspend and isn't moved.
- [An error whose holder recreates the `Throwable` on every recomposition would still be recorded repeatedly] → Holders store the `Throwable` and describe it at render time (documented in `ui.md`), so the instance is stable.
