# Tasks

## 1. Reading the state of a Maven daemon

- [x] 1.1 Add `state: DaemonState` to `MavenDaemonProcess` (default `Unknown` so fakes and callers keep compiling); verified by `./gradlew :core:test` compiling and passing.
- [x] 1.2 Detect daemons by their `-Dmvnd.id=` argument (real daemons run the Classworlds launcher) and return each daemon's log path from `-Dorg.slf4j.simpleLogger.logFile=` or `-Dmaven.logger.logFile=` (up to the first `.log` followed by whitespace or end of line); tests in `ProcessMavenDaemonSourceTest` for a real 1.0.6 command line, 1.x, 2.x, a storage path with spaces and no argument.
- [x] 1.3 Add `parseMavenDaemonState(tail, modifiedAt, zone)` per design.md (latest `Updating state to:` line; `Idle` → `Idle(since)`, `Busy`/`Canceled`/`StopRequested`/`Stopped`/`Broken` → `Busy`, other → `Unknown`, no line → `Busy`; `since` = latest instant ≤ `modifiedAt` with the marker's local time); tests pinned to excerpts of a real daemon log: idle after a build, log restarted by a running build, busy marker, cancelled/broken, marker before midnight, second-precision modification time.
- [x] 1.4 In `ProcessMavenDaemonSource.list`, read the last 1 MiB and last-modified time of each daemon's log and fill state and last build directory; missing argument, missing file or read error gives `Unknown`; tests with a temporary log file and a fake executor.
- [x] 1.5 Keep attributing by the process working directory, which mvnd moves to each build's folder (checked on a real daemon); existing `ListMavenDaemonsUseCaseTest` cases still pass.

## 2. Stopping idle Maven daemons

- [x] 2.1 Add `MavenDaemonStopReport` and `KeptMavenDaemon` (reusing `DaemonKeptReason` and `DaemonStopFailure`) and `StopMavenDaemonsUseCase(source, executor)`: fresh listing, `kill <pid>` only for `Idle`, kept with `BUSY`/`STATE_UNKNOWN`, `notDaemons` for unknown pids, failures with stderr or exit code; tests covering every scenario of "Stop idle Maven daemons" with `FakeMavenDaemonSource` and a fake executor.

## 3. Command line

- [x] 3.1 `shogun maven` prints the state column ("busy", "idle for N min", "unknown") and `--json` adds `state` and `idleSince`; `ShogunCliTest` covers an idle and a busy daemon.
- [x] 3.2 Parse `shogun maven stop [<pid>...] [--json]` into `CliCommand.MavenStop`, implement it with `StopMavenDaemonsUseCase` and `mavenStopJson`, exit 1 on pids that aren't Maven daemons; update `CliHelp`; tests for stop all idle, busy by id, and not a daemon.
- [x] 3.3 Add `--stop-maven-daemons` to `shogun clean`: list Maven daemons before the removals, stop the idle ones attributed to removed worktrees after `--yes`, report stopped and kept, and list the would-be-stopped daemons without `--yes`; include the flag wherever the clean command line is echoed; tests for the three scenarios of the spec.

## 4. Desktop app

- [x] 4.1 Wire `StopMavenDaemonsUseCase` into `AppContainer` (Machine use cases and the worktree `MavenUseCases`), with `listDaemons`/`stopDaemons` for the cleanup; verified by `./gradlew :desktopApp:test`.
- [x] 4.2 `MachineResources`: `stopMavenDaemon(pid)` and `stopIdleMavenDaemons()` with a result banner that stays until dismissed, then refresh; `MavenDaemonsView` shows state per row, a stop button per daemon and "Stop idle daemons", and drops the "never stopped" note; strings in `Strings`, `EnglishStrings`, `SpanishStrings`; tests in `MavenAndComposeMachineTest` for stopping idle and an unknown daemon.
- [x] 4.3 Cleanup: `CleanupOptions.stopMavenDaemons` (default true), a checked "also stop their idle Maven daemons" option in `CleanupDialog` shown only for Maven repositories, stopping after removals in `ProjectWorktrees.cleanUp` from the list taken before, and the counts in `WorktreeSuccess.CleanedUp` and its message; tests for option shown/hidden, daemon of a removed worktree stopped, kept worktree not stopped, busy kept.

## 5. Documentation and validation

- [x] 5.1 Update the Maven provider and CLI pages in `docs/` (English and Spanish) to describe the state, `shogun maven stop`, `--stop-maven-daemons` and the cleanup option; verified by `mkdocs build --strict` or reviewing the pages.
- [x] 5.2 Run `./gradlew test` and confirm it passes with no warnings.
- [x] 5.3 Validate against a real mvnd daemon on macOS: start one with a build, check `shogun maven` shows it busy during the build and idle after, `shogun maven stop` stops it and keeps a busy one; record the result. Done with mvnd 1.0.6 (Homebrew) on macOS: busy during a build and kept by `maven stop`, idle afterwards and stopped; mvnd started a new daemon on the next build; `clean --stop-maven-daemons` on a merged worktree removed it and stopped its idle daemon.

## Workflow follow-up

- Archive the change (`/opsx:archive`) and update `docs/roadmap.md` and `docs/roadmap.es.md` (remove the last item of point 1 and the point if it's empty, renumber, update "Direction").
- Implementation commit, branch up to date with `origin/develop`, pull request against `develop` assigned to CiroFeliu.
