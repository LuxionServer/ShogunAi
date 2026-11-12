# Design

## Context

See proposal.md for the motivation. The state this design starts from (develop at `fdda1d05`):

- `ShellCommandExecutor` has one abstract method, `execute(command, workingDirectory)`, and three with default bodies: `executeWithin` (calls `execute`, dropping the limit), `executeWithEnvironment` (calls `execute`, dropping the environment) and `executeStreaming` (calls `execute` and replays the output afterwards). `ProcessBuilderShellCommandExecutor` overrides them all; about 45 anonymous test doubles only override `execute`, so they silently get the degraded behavior.
- Production callers: 43 `executeWithin` (with `CommandTimeouts.GIT_READ` 30 s, `LISTING` 10 s, `NETWORK` 60 s), 36 `execute` in `core` and 2 in `cli` without limit, 2 `executeWithEnvironment`, 2 `executeStreaming` (`docker compose`, headless Gradle launch), both without limit.
- `runCatching` appears 254 times in `core` production code. 211 of them are in the 80 files that declare a suspending function; the other 43 are in files with no `suspend` at all (the `Json*`/`Nio*` stores of `versioned-json-store`, file registries, pure model code), where no cancellation can be thrown. `CheckInstallationUseCase` already has a private `runCatchingNonCancellation`; 20 files catch `CancellationException` by hand before a broad catch; 14 broad catches in suspending files don't.
- The machine-wide lock (`EnvironmentStateStore.withLock`) is a blocking, thread-bound lock (a JVM lock plus a file lock). `AcquireWorktreeResourceUseCase.attempt` holds it on an IO thread and runs `acquireLocked` inside `runBlocking`. Inside, it runs: `adb devices -l` and one `adb shell getprop sys.boot_completed` per emulator (10 s each), `xcrun simctl list -j devices` (10 s, once per staleness check and once per released simulator), and, per victim, `adb emu kill` (10 s), `simctl shutdown` and `simctl delete` (no limit), then `simctl create` for the new simulator (no limit). `PhysicalDeviceUseCases`, `WaitInLine` and `DesktopTurnUseCases` hold the same lock only around non-suspending blocks that read and write state files: no external commands run there.

## Goals / Non-Goals

**Goals:**
- No external command started by `core` or `cli` can run without a limit, and the port makes it impossible to write one that does.
- A cancellation reaching any suspending `core` code propagates instead of turning into a `Result.failure` or a fallback value, and a source test keeps it that way.
- Every command run under the machine lock is bounded, so the lock is always released in bounded time.

**Non-Goals:**
- `cli` and `desktopApp` production `runCatching` (18 and 51): their callers are composition and UI code that `shared-composition` and `desktop-coverage-worktrees` are reshaping in parallel. They go to the roadmap as a follow-up.
- Restructuring `acquireLocked` so device reads and side effects happen outside the lock (see Decision 6).
- A Git port over the domain's `git` argv: a later change. Edits to those calls here are limited to their time limit.
- The `Json*`/`Nio*` stores: they don't suspend, so the new rule doesn't reach them, and `versioned-json-store` owns them.

## Decisions

### 1. `runSuspendCatching`, an inline drop-in for `runCatching`

`inline fun <T> runSuspendCatching(block: () -> T): Result<T>` runs `runCatching(block)` and rethrows the failure if it is a `CancellationException`. Being `inline`, its block may call suspending functions when the caller is suspending, so every call site changes only its name. It lives in a new file `domain/Cancellation.kt` (package `app.luxion.shogunai.domain`), usable by `domain` and `infrastructure` alike. `CheckInstallationUseCase.runCatchingNonCancellation` is removed in favor of it.

Alternatives: a `suspend fun` taking a `suspend () -> T` (like the existing private helper) can't be used from the non-suspending lambdas (`map`, `mapNotNull`) where many `runCatching` calls sit; an extension `Result<T>.rethrowCancellation()` appended to each `runCatching` is easy to forget, which is the bug itself.

### 2. Scope of the rule: files that declare a suspending function

The rule applies to every `core` production file that contains `suspend`: no bare `runCatching`, and every `catch (…: Exception)` or `catch (…: Throwable)` must be preceded in the same `try` by a `catch (…: CancellationException)` that rethrows. All 211 calls in those files are replaced, not only those whose block suspends: telling them apart needs type information a source test doesn't have, and the replacement is harmless for a block that doesn't suspend. Files with no `suspend` keep `runCatching`, since nothing there can throw a cancellation.

A source test in `core` (`CancellationSourceTest`, its own file so it doesn't collide with the source tests `architecture-tests` adds) walks `core/src/main/kotlin` and fails naming each offending file and line. When `architecture-tests` lands first, the test moves into its harness during the merge from `develop`.

### 3. The executor port requires a limit on every method

```kotlin
interface ShellCommandExecutor {
    suspend fun executeWithin(command: List<String>, timeout: Duration, workingDirectory: String? = null, environment: Map<String, String> = emptyMap()): CommandResult
    suspend fun executeStreaming(command: List<String>, timeout: Duration, workingDirectory: String?, environment: Map<String, String>, onOutput: (OutputLine) -> Unit): CommandResult
}
```

`execute` and `executeWithEnvironment` are removed, and neither method has a default body. Keeping the name `executeWithin` leaves the 43 bounded call sites untouched (fewer conflicts with parallel changes and the future Git port); the 36 + 2 unbounded `execute` calls and the 2 `executeWithEnvironment` calls become `executeWithin` with a category, and `executeStreaming` gains a limit. `ProcessBuilderShellCommandExecutor` already supports a limit with environment and streaming in its private `run`, so it only routes the parameters.

Alternative: keep `execute` and add `timeout` to it. Renaming every bounded call to `execute` touches 43 more lines for no behavior change.

### 4. Limit categories (agreed with the user)

`CommandTimeouts` gains three values next to `LISTING` (10 s), `GIT_READ` (30 s) and `NETWORK` (60 s):

| Category | Limit | Commands |
|---|---|---|
| `LISTING` | 10 s | `defaults read` (Derived Data location) |
| `GIT_READ` | 30 s | `git rev-parse`, `show-ref`, `branch --list`, `ls-files`, `worktree list`, `merge-base`, `commit-tree` (writes one object), every other read in the moved calls |
| `LOCAL_TOOL` | 60 s | `kill`, `open -b`, `osascript` window helper, `screencapture`, Herdr and Orca CLI calls, `herdr notification`, `simctl create/shutdown/delete` |
| `LOCAL_WORK` | 5 min | `git worktree add` (checkout, LFS smudge), `git worktree remove --force`, `git branch -D`, `git worktree repair`, the rollback of a failed creation, `simctl boot`, `cp -c -R` of `Pods`, the guided tour's Git commands |
| `INSTALL` | 30 min | post-creation commands, `pod install`, `flutter pub get`, `docker compose up/build`, the headless Gradle launch |

A fixed table keeps the decision in one reviewable place; the user chose fixed categories over a per-repository setting for post-creation commands.

### 5. A timeout is not "tool not installed"

`CliHerdrClient.run` and `CliOrcaClient.run` map every `CommandExecutionException` to `HerdrNotInstalled`/`OrcaNotInstalled`. With limits on their calls, a `CommandExecutionException.TimedOut` is mapped to `HerdrCommandFailed`/`OrcaCommandFailed` with the timeout message instead. Other call sites already turn `CommandExecutionException` into a message built from it, which the `TimedOut` message ("did not finish within …") reads correctly in.

### 6. Work under the machine lock: bound it now, restructure later

After Decision 4, the worst case under the lock is bounded: listings at 10 s each, `simctl shutdown/delete/create` at 60 s each per victim. Two problems remain and are left as a roadmap follow-up because fixing them changes the acquisition algorithm:

- Device reads (`readPool.of`, `simctl list`) and side effects (stopping or deleting a victim, creating a simulator) run while every other process waits for the lock. The fix is to read devices before taking the lock, decide and record the reservation under the lock (state files only), run the side effects after releasing it, and re-validate on the next attempt.
- The block runs inside `runBlocking` on a thread holding a thread-bound lock, so a cancellation of the caller doesn't reach it until it finishes. The fix needs a lock that can be awaited without pinning a thread, which belongs to the state store `versioned-json-store` is reshaping.

`withContext(Dispatchers.IO) { stateStore.withLock(block) }` in `PhysicalDeviceUseCases`, `WaitInLine` and `DesktopTurnUseCases` only wraps file reads and writes, so it needs no change.

### 7. Test doubles implement the whole port

`core/src/testFixtures` gains `abstract class ScriptedShellCommandExecutor` with one `abstract suspend fun respond(command, workingDirectory): CommandResult`. It implements `executeWithin` by recording the limit and environment and calling `respond`, and `executeStreaming` by calling `respond` and replaying its output lines through `onOutput`. `FakeShellCommandExecutor` extends it, keeping its constructor and `timeouts`/`environments`/`executedCommands` maps. Each anonymous `object : ShellCommandExecutor { override suspend fun execute(...) }` in `core`, `cli` and `desktopApp` tests becomes `object : ScriptedShellCommandExecutor() { override suspend fun respond(...) }`; the few doubles that delegate with `by` override `executeWithin` instead of `execute`. `TmuxIntegrationTest` forwards to the real executor's `executeWithin`. The streaming replay that used to be the port's default lives only in this test fixture.

### 8. Regression tests for swallowed cancellations

A swallowed cancellation is observable when a use case returns `Result.failure(CancellationException)` (or a fallback) instead of throwing: its caller then shows an error, retries or carries on. The regression tests drive a double whose command throws `CancellationException` and assert that the use case throws it, for the cases the analysis found: `ReadWorktreeStatusUseCase`, `AcquireWorktreeResourceUseCase` (direct attempt and waiting in line), and one per broad `catch` fixed (Decision 2). Each test is written and seen failing before its fix. The remaining mechanical replacements are guarded by the source test rather than by one behavioral test each.

## Risks / Trade-offs

- [A legitimate command exceeds its category, e.g. a monorepo checkout over 5 min] → the limits are generous and the error names the command and its limit, so a report points straight at the category to raise.
- [Large mechanical diff (≈80 files, ≈45 test doubles) conflicts with parallel changes] → the edits are one-token renames per line, and `ShogunCli.kt` only gets its two call sites changed.
- [The source test misreads code (a `catch` in a string, a multi-line `try`)] → it matches `runCatching` as a call and inspects the previous `catch` clause of the same `try`; a false positive fails loudly and names the line.
- [Kover counts the inline helper's body once per call site] → it is covered by its own unit test and by every use case test that exercises a failure.

## Migration Plan

Internal only: no stored data, settings or CLI output change. Rolling back is reverting the change.
