# Proposal

## Why

`core` swallows coroutine cancellation and lets external commands run without limit. 211 `runCatching` calls in files that suspend (and 14 broad `catch (Exception)` blocks) turn a `CancellationException` into an ordinary `Result.failure`, so a cancelled operation keeps running or reports a bogus error; for example `ReadWorktreeStatusUseCase` and `AcquireWorktreeResourceUseCase.attempt` return a failure instead of being cancelled. 36 `executor.execute(...)` calls in `core` (and 2 in `cli`) have no time limit, and the executor port's default methods silently drop the limit (`executeWithin`) or the environment (`executeWithEnvironment`) for any implementation that doesn't override them. While the machine-wide state lock is held, `AcquireWorktreeResourceUseCase` runs `simctl create/shutdown/delete` with no limit, so one hung tool blocks every other process that needs the lock.

## What Changes

- A cancellation-aware `runSuspendCatching` in the domain, used instead of `runCatching` in every `core` production file that declares a suspending function; broad `catch (Exception|Throwable)` blocks in those files rethrow `CancellationException` first. A source test keeps bare `runCatching` and unguarded broad catches out of those files.
- **BREAKING** (internal port): `ShellCommandExecutor` loses `execute` and `executeWithEnvironment` and all its default methods. It keeps two abstract methods, both requiring a limit: `executeWithin(command, timeout, workingDirectory, environment)` and `executeStreaming(command, timeout, workingDirectory, environment, onOutput)`.
- New limit categories in `CommandTimeouts`, as agreed with the user: `LOCAL_TOOL` (60 s), `LOCAL_WORK` (5 min, for Git worktree add/remove/repair, branch deletion, `simctl boot`, cloning `Pods`) and `INSTALL` (30 min, for `pod install`, `flutter pub get`, post-creation commands, `docker compose` and the headless Gradle launch). Every former unbounded call gets the category that fits it.
- A command that times out where the code used to read any start failure as "tool not installed" (Herdr, Orca) is reported as a failed command naming the timeout, not as a missing tool.
- Test doubles: `FakeShellCommandExecutor` and a new `ScriptedShellCommandExecutor` base in `core` test fixtures implement the whole port, and every anonymous executor double in `core`, `cli` and `desktopApp` tests moves to it.
- Work under the machine lock: every command run while it's held becomes bounded; moving device reads and simulator/emulator side effects out of the lock, and making the lock cancellable, is documented as a roadmap follow-up.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `command-execution`: every external command has a time limit (long-running work included, with generous limits), and a cancelled operation ends as cancelled instead of as a failure.

## Impact

- `core/src/main/.../domain/executor/ShellCommandExecutor.kt`, `infrastructure/ProcessBuilderShellCommandExecutor.kt` and every caller of the executor in `core` and `cli` (`ShogunCli.kt`: two call sites, signature-only edits).
- About 80 `core` production files for the `runCatching` replacement; the `Json*`/`Nio*` stores of `versioned-json-store` are untouched because they don't suspend.
- `core/src/testFixtures` (`FakeShellCommandExecutor`, new `ScriptedShellCommandExecutor`) and the executor doubles in `core`, `cli` and `desktopApp` tests.
- `docs/roadmap.md` and `docs/roadmap.es.md`: the follow-up for the machine lock, and for `cli`/`desktopApp` cancellation handling.
- No user-visible change except that commands which used to hang now fail with a timeout error naming the command.
