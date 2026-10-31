# Design

## Context

Baseline measured with Kover 0.9.11 on macOS on `develop` at 24b64c40, with the exclusions of the archived `coverage-kover` change (only `MainKt` in `cli`):

| File | Missed branches |
|---|---|
| `ShogunCli.kt` | 379 |
| `CommandJson.kt` | 44 |
| `CliCommand.kt` | 38 |
| `MultirepoJson.kt` | 37 |
| `StatusJson.kt` | 13 |
| `DesktopTurnText.kt` | 3 |
| `HeadlessAppText.kt` | 2 |
| `CliHelp.kt`, `DeviceWaitText.kt`, `GuideText.kt` | 1 each |
| **Total** | **519 of 3,087 (83%)** |

## Goals / Non-Goals

**Goals:** 100% of `cli`'s branches, enforced by `koverVerify`, without new exclusions.

**Non-Goals:** branches of `core` and the desktop app (their own changes); refactoring `ShogunCli.kt` beyond removing unreachable branches.

## Decisions

### Tests through the command, not through private helpers

Missed branches are covered by running `ShogunCli` with the arguments and fakes that reach them, in the existing test file of the command family (`ProjectAndAppCommandsTest`, `MultirepoCommandsTest`, `DoctorCommandTest`, …). Private helpers stay private; making them `internal` only to test them is avoided unless a branch can't be reached through a command in a reasonable test.

### Unreachable branches are removed, not tested around

A branch no input can reach (a `?.` on a value that is never `null`, an `else` of an exhaustive `when`, a default that every caller overrides) is removed by simplifying the code: the behavior doesn't change and the coverage reflects only paths that exist. Each removal is listed below with its reason.

### The floor follows the figure

The `cli branches` rule in `cli/build.gradle.kts` is raised to each new whole percentage as tests land, and ends at 100.

## Unreachable branches removed

No production behavior changed. Kotlin emits a null check for some constructs whose null side no input can reach; those were rewritten so the check disappears, and the branches that depended on an invariant of the code calling them were simplified:

- **A safe call followed by a value that is never null.** `x?.let(f) ?: default` where `f` never returns `null`, and chains like `a?.b?.c` where `b` isn't nullable, leave a branch for a `null` that can't happen. They became `if (x == null) default else …`, `x?.let { it.b.c }`, `x?.list.orEmpty()`, or a call to `orJsonNull()` (a new helper in `StatusJson.kt`) for JSON values, in `StatusJson`, `CommandJson`, `MultirepoJson`, `DesktopTurnText` and `ShogunCli` (multirepo show's worktrees column, the multirepo review, status and group lines, opening lines, `failureReason`, `worktreesRootOf`, the hook input's `string`, the Compose report in `clean`, pool limits, `configFor`).
- **`?:` with a message that is never null.** `UsageException` now has a non-null `message`; the JSON failures of `clean` read `WorktreeError.message!!`, whose constructor always passes a message; `appendToEnvironmentFile` reads the message `File.appendText` always gives its `FileNotFoundException`.
- **Help for a command that always exists.** `CliHelp.forCommand` is only called with a name from `commandNames`, so it returns a `String` and looks the section up with `getValue`.
- **JSON output for a command that always has it.** Every command in `JSON_COMMANDS` implements `JsonOutput`, so `withJsonOutput` casts instead of checking.
- **Values the caller already guarantees.** A new worktree without `--branch` always has a task id (the parser requires one of them), so `new` and `newGroup` read `command.taskId!!`; a created group repository always has a path; the guide's `else` target is only reached with `--file`; a leased pool holder always has a lease expiry (`HolderProtection.LEASED` requires it); after `multirepo workspace detect` the workspace repository always exists and is never a default repository, since `defaultRepos` may only name declared repositories; a group worktree that isn't missing always has a status read, so `groupWorktreeLine` folds it; a status line without a status always has its problem; a recorded base always comes with its name.
- **Lookups that always find their key.** The pool's waiting tickets are read with `getValue` for the kinds they were built from, and `newGroup` reads each created repository's configuration from a map of the members instead of searching the list again.
- **Checks already made before.** `multirepo` dispatches `init` and `trust` before resolving the context, so the `when` no longer lists them; `multirepo init` reads the declaration once instead of twice; Orca only reports `STARTED_AGENT` and `FOCUSED`, so its opening line no longer handles `OPENED`; `hook session-start` reads the multirepo with the `Result` `ResolveMultirepoUseCase` already returns; the note about an ignored declaration no longer checks it isn't approved, since an approved declaration above the directory is always the resolved context.
- **DerivedData sizes.** Folders listed with `DerivedDataMeasure.ALL` are always measured, so their size is never `null` in `xcode` and `xcode trim`; every place that formats a size now calls `DerivedDataFolder.sizeOrZero()`, whose `null` side the JSON tests cover.
- **The operating system.** `absolute` became `absolutePath`, which takes the absolute-path check as a parameter (Java's by default), so the Windows drive-letter path is covered on the macOS job, as `AGENTS.md` asks of code that depends on the system. `claudeAncestorPid` takes its starting process and how to read a process's command as parameters (today's behavior by default), so both its sides are tested.
- `unresolvedReason` became a top-level function: a port that isn't reserved can't be reached through a command, since the CLI always resolves environments reserving their ports, but the message is part of the sealed type's mapping and is tested directly.

## Test fixtures

`core`'s test fixtures, excluded from coverage, gained what the tests needed to provoke failures (the prunable worktree of `FakeGitRepositories` arrived from `doctor-provider-checks` too, and its version is kept): failures listing and deleting simulators in `FakeAppleSimulators`, a failure stopping an emulator in `FakeAndroidSdk`, a failure on the second listing in `FakeGradleDaemonSource` and `FakeMavenDaemonSource`, Compose standard output in `FakeComposeProjectSource`, and a failure loading the settings in `FakeResourceLimitsRepository`. Nothing in `core`'s production code changed.

## Risks / Trade-offs

- **Parallel changes in `cli`**: `herdr-e2e-windows` adds end-to-end tests, which run in the `test` task and can only raise coverage; `doctor-provider-checks` adds production code to `shogun doctor`, which has to arrive with every branch tested once this change is in `develop`. Whichever merges second brings in `develop`, re-runs `./gradlew test koverVerify` and covers what the other added.
- **`coverage-core-branches`** modifies the same requirement of `test-coverage` and raises `core`'s floor; the conflict in the spec is merged by keeping both thresholds.
- **Kotlin-generated branches** (null checks of `!!`, `when` on sealed classes, `?.let`) count as branches: some are only reachable by values a fake has to provide, which the fakes in `core`'s test fixtures already allow.
