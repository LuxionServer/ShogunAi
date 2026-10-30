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

To be filled in during implementation: file, branch and why no input reaches it.

## Risks / Trade-offs

- **Parallel changes in `cli`**: `herdr-e2e-windows` adds end-to-end tests, which run in the `test` task and can only raise coverage; `doctor-provider-checks` adds production code to `shogun doctor`, which has to arrive with every branch tested once this change is in `develop`. Whichever merges second brings in `develop`, re-runs `./gradlew test koverVerify` and covers what the other added.
- **`coverage-core-branches`** modifies the same requirement of `test-coverage` and raises `core`'s floor; the conflict in the spec is merged by keeping both thresholds.
- **Kotlin-generated branches** (null checks of `!!`, `when` on sealed classes, `?.let`) count as branches: some are only reachable by values a fake has to provide, which the fakes in `core`'s test fixtures already allow.
