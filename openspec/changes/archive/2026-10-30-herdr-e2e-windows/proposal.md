# Proposal

## Why

The CLI's end-to-end tests open worktrees in Herdr against a fake `herdr` on Linux and macOS, but skip those scenarios on Windows: Herdr's client only runs `herdr.exe` there, and the fake is installed as a shell script (or, for Orca, a `.cmd` file), which can't stand in for an `.exe`. Opening in Herdr is therefore never exercised end to end on Windows, which is the kind of gap roadmap point 1 (reliability on any machine) wants closed.

## What Changes

- The end-to-end tests get a stand-in `herdr.exe` on Windows: a small native launcher, built from the test sources, that starts the existing Kotlin fake runner with the arguments it receives.
- The three "open in Herdr" end-to-end scenarios (two in `RunnerEndToEndTest`, one in `MultirepoEndToEndTest`) stop skipping Windows, so the Windows job of the Tests workflow runs them.
- The architecture docs of the CLI's end-to-end tests stop saying Herdr's tests are skipped on Windows, and the roadmap drops the item.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cli-end-to-end-testing`: opening a worktree in Herdr is covered on every operating system, Windows included, with a stand-in executable named as Herdr's client looks for it.

## Impact

- `cli/src/e2eTest`: the launcher's source, `FakeRunner.kt` installing it as `herdr.exe` on Windows, and the Herdr scenarios without their Windows skip.
- `cli/build.gradle.kts`: a Windows-only task that compiles the launcher, which `e2eTest` depends on.
- No production code changes. The Windows job of the Tests workflow already runs the end-to-end tests through `./gradlew test`.
- Docs: `docs/architecture/cli.md` and `cli.es.md`, `docs/roadmap.md` and `roadmap.es.md`.
