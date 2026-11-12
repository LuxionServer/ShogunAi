# Tasks

## 1. Executor port with mandatory limits

- [ ] 1.1 Add `LOCAL_TOOL` (60 s), `LOCAL_WORK` (5 min) and `INSTALL` (30 min) to `CommandTimeouts`; reshape `ShellCommandExecutor` to the two abstract methods of design Decision 3 and route them in `ProcessBuilderShellCommandExecutor`; verify with `ProcessBuilderShellCommandExecutor` tests that a streaming command and a command with an environment both time out and receive their environment.
- [ ] 1.2 Add `ScriptedShellCommandExecutor` to `core/src/testFixtures`, make `FakeShellCommandExecutor` extend it, and move every anonymous executor double in `core`, `cli` and `desktopApp` tests to it (Decision 7); rewrite `ShellCommandExecutorTest` for the fixture's streaming replay; verify `./gradlew test` compiles and passes.
- [ ] 1.3 Move every `execute`, `executeWithEnvironment` and `executeStreaming` call in `core` and `cli` to a bounded call with the category of Decision 4; verify that each touched use case or adapter test asserts the limit through `FakeShellCommandExecutor.timeouts` (post-creation, `pod install`, `flutter pub get`, `docker compose`, headless launch, `simctl`, Herdr, Orca, worktree add/remove).
- [ ] 1.4 Map `CommandExecutionException.TimedOut` to `HerdrCommandFailed`/`OrcaCommandFailed` in `CliHerdrClient` and `CliOrcaClient` (Decision 5); regression tests written first and seen failing: a timed-out Herdr or Orca command isn't reported as not installed.

## 2. Cancellation propagates

- [ ] 2.1 Add `runSuspendCatching` in `domain/Cancellation.kt` with unit tests (success, ordinary failure, cancellation rethrown), and replace `CheckInstallationUseCase.runCatchingNonCancellation` with it; verify the tests pass.
- [ ] 2.2 Regression tests first, seen failing: `ReadWorktreeStatusUseCase`, `AcquireWorktreeResourceUseCase` (direct attempt and waiting in line) throw `CancellationException` when a command is cancelled; then fix them with `runSuspendCatching` and verify the tests pass.
- [ ] 2.3 Add `CancellationSourceTest` (Decision 2) and see it fail listing the remaining offenders; replace every `runCatching` in `core` production files that declare a suspending function, and add a rethrowing `CancellationException` catch before each unguarded broad catch, with a regression test first for each broad catch whose fix changes behavior; verify the source test and `./gradlew :core:test` pass.

## 3. Documentation and integration

- [ ] 3.1 Add the follow-ups to `docs/roadmap.md` and `docs/roadmap.es.md` (machine lock restructuring and cancellable lock from Decision 6; cancellation handling in `cli` and `desktopApp`), one sentence per line; verify `mkdocs build --strict` passes.
- [ ] 3.2 Run `./gradlew test koverVerify` and `openspec validate --specs --strict` plus `openspec validate cancellation-and-timeouts --strict`; verify all pass with no compiler warnings.

## Workflow follow-up

- Archive the change (`/opsx:archive`) after validation, before opening the pull request against `develop`.
