# Tasks

## 1. Reading the state of a Maven daemon

- [ ] 1.1 Add `state: DaemonState` and `lastBuildDirectory: String?` to `MavenDaemonProcess` (defaults `Unknown`/`null` so fakes and callers keep compiling); verified by `./gradlew :core:test` compiling and passing.
- [ ] 1.2 Extend `parseMavenDaemons` to return each daemon's log path from `-Dorg.slf4j.simpleLogger.logFile=` or `-Dmaven.logger.logFile=` (up to the first `.log` followed by whitespace or end of line, so paths with spaces work); tests in `ProcessMavenDaemonSourceTest` for 1.x, 2.x, a storage path with spaces and no argument.
- [ ] 1.3 Add `parseMavenDaemonLog(tail, modifiedAt, zone)` returning state, idle-since and last build directory per design.md (latest `Updating state to:` line; `Idle` → `Idle(since)`, `Busy`/`Canceled`/`StopRequested`/`Stopped`/`Broken` → `Busy`, other or none → `Unknown`; `since` = latest instant ≤ `modifiedAt` with the marker's local time; `workingDir='…'` of the latest `Request received: BuildRequest{`); tests pinned to log excerpts in mvnd's format: idle after a build, busy, fresh daemon with only `Daemon started`, cancelled, marker before midnight with mtime after midnight, DEBUG lines after the marker, a build line with `args` containing quotes.
- [ ] 1.4 In `ProcessMavenDaemonSource.list`, read the last 1 MiB and last-modified time of each daemon's log and fill state and last build directory; missing argument, missing file or read error gives `Unknown`; tests with a temporary log file and a fake executor.
- [ ] 1.5 `ListMavenDaemonsUseCase` attributes by `lastBuildDirectory ?: workingDirectory`; tests in `ListMavenDaemonsUseCaseTest` for a daemon reused by another worktree, no build recorded, and unknown directory.

## 2. Stopping idle Maven daemons

- [ ] 2.1 Add `MavenDaemonStopReport` and `KeptMavenDaemon` (reusing `DaemonKeptReason` and `DaemonStopFailure`) and `StopMavenDaemonsUseCase(source, executor)`: fresh listing, `kill <pid>` only for `Idle`, kept with `BUSY`/`STATE_UNKNOWN`, `notDaemons` for unknown pids, failures with stderr or exit code; tests covering every scenario of "Stop idle Maven daemons" with `FakeMavenDaemonSource` and a fake executor.

## 3. Command line

- [ ] 3.1 `shogun maven` prints the state column ("busy", "idle for N min", "unknown") and `--json` adds `state`, `idleSince` and `lastBuildDirectory`; `ShogunCliTest` covers an idle and a busy daemon.
- [ ] 3.2 Parse `shogun maven stop [<pid>...] [--json]` into `CliCommand.MavenStop`, implement it with `StopMavenDaemonsUseCase` and `mavenStopJson`, exit 1 on pids that aren't Maven daemons; update `CliHelp`; tests for stop all idle, busy by id, and not a daemon.
- [ ] 3.3 Add `--stop-maven-daemons` to `shogun clean`: list Maven daemons before the removals, stop the idle ones attributed to removed worktrees after `--yes`, report stopped and kept, and list the would-be-stopped daemons without `--yes`; include the flag wherever the clean command line is echoed; tests for the three scenarios of the spec.

## 4. Desktop app

- [ ] 4.1 Wire `StopMavenDaemonsUseCase` into `AppContainer` (Machine use cases and the worktree `MavenUseCases`), with `listDaemons`/`stopDaemons` for the cleanup; verified by `./gradlew :desktopApp:test`.
- [ ] 4.2 `MachineResources`: `stopMavenDaemon(pid)` and `stopIdleMavenDaemons()` with a result banner that stays until dismissed, then refresh; `MavenDaemonsView` shows state per row, a stop button per daemon and "Stop idle daemons", and drops the "never stopped" note; strings in `Strings`, `EnglishStrings`, `SpanishStrings`; tests in `MavenAndComposeMachineTest` for stopping idle and an unknown daemon.
- [ ] 4.3 Cleanup: `CleanupOptions.stopMavenDaemons` (default true), a checked "also stop their idle Maven daemons" option in `CleanupDialog` shown only for Maven repositories, stopping after removals in `ProjectWorktrees.cleanUp` from the list taken before, and the counts in `WorktreeSuccess.CleanedUp` and its message; tests for option shown/hidden, daemon of a removed worktree stopped, kept worktree not stopped, busy kept.

## 5. Documentation and validation

- [ ] 5.1 Update the Maven provider and CLI pages in `docs/` (English and Spanish) to describe the state, `shogun maven stop`, `--stop-maven-daemons` and the cleanup option; verified by `mkdocs build --strict` or reviewing the pages.
- [ ] 5.2 Run `./gradlew test` and confirm it passes with no warnings.
- [ ] 5.3 Validate against a real mvnd daemon on macOS: start one with a build, check `shogun maven` shows it busy during the build and idle after, `shogun maven stop` stops it and keeps a busy one; record the result.

## Workflow follow-up

- Archive the change (`/opsx:archive`) and update `docs/roadmap.md` and `docs/roadmap.es.md` (remove the last item of point 1 and the point if it's empty, renumber, update "Direction").
- Implementation commit, branch up to date with `origin/develop`, pull request against `develop` assigned to CiroFeliu.
