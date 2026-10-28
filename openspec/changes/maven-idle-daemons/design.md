# Design

## Context

See proposal.md for the motivation. Current state:

- `ProcessMavenDaemonSource` lists mvnd daemons with `ps -A -ww -o pid=,rss=,command=`, keeping processes with an argument in the `org.mvndaemon.mvnd.` package, and reads every daemon's process working directory at once with `lsof -a -p <pids> -d cwd -Fn`. `ListMavenDaemonsUseCase` attributes a daemon by that directory with `owningWorktree`. The spec forbids stopping daemons.
- The Gradle provider reads each daemon's state from the tail of its log (`ProcessGradleDaemonSource.parseDaemonLog`), models it as `DaemonState` (`Busy`, `Idle(since)`, `Unknown`), and stops idle daemons with `StopGradleDaemonsUseCase` (fresh listing, `kill <pid>`, `DaemonStopReport`). The CLI (`gradle stop`, `clean --stop-gradle-daemons`), the Machine view and the cleanup dialog are built on it.

What mvnd does, checked in its sources (tag 1.0.6 and `master`, the 2.x line) and in daemon logs quoted in mvnd's issue tracker (1.x with Maven 3, and 2.0.0-rc-3 with Maven 4):

- The client starts each daemon with the path of its log on the command line: `-Dorg.slf4j.simpleLogger.logFile=<storage>/daemon-<id>.log` in 1.x, `-Dmaven.logger.logFile=<storage>/daemon-<id>.log` in 2.x. `<storage>` defaults to `~/.m2/mvnd/registry/<mvnd version>` and can be changed with `mvnd.daemonStorage`.
- Every daemon logger under `org.mvndaemon.mvnd.daemon` writes lines `HH:mm:ss.SSS <level letter> <message>` at INFO by default. `Server.updateState` logs `Updating state to: <state>` on every change, before storing it in the registry. States: `Idle`, `Busy`, `Canceled`, `StopRequested`, `Stopped`, `Broken`.
- A daemon is created in state `Busy` without logging it (so no other client grabs it), and logs `Updating state to: Idle` when its first build ends.
- Each build logs `Request received: BuildRequest{args=[…], workingDir='<dir>', projectDir='<dir>'}`.
- While idle, nothing is written at INFO; the periodic expiration check logs at DEBUG only.
- The process working directory is the directory of the build that started the daemon; later builds from other directories reuse it.
- `mvnd --stop` kills every daemon with `destroyForcibly`, busy or not, and clients drop registry entries whose address no longer answers (`CleanupOnStaleAddress`), so a daemon ending outside mvnd's control is an expected case.

## Goals / Non-Goals

**Goals:**
- Same safeguards as Gradle: stop only what is idle on a fresh read, never something whose state is unknown.
- Work with mvnd 1.x and 2.x, any `mvnd.daemonStorage`, without needing `mvnd` on the `PATH`.

**Non-Goals:**
- Windows: ShogunAi doesn't list processes there for Gradle or Maven; the log reading itself would work, so the change adds nothing that blocks it later.
- A Maven daemon pool limit or automatic trim (`resource-pools`): later, if needed.
- Reading the binary registry (`registry.bin`) or running `mvnd --status`.

## Decisions

### State from the daemon log named on the command line
`parseMavenDaemons` keeps, besides pid and memory, the log path: the text after `-Dorg.slf4j.simpleLogger.logFile=` or `-Dmaven.logger.logFile=` up to the first `.log` followed by whitespace or the end of the line, so a storage folder with spaces still parses. `ProcessMavenDaemonSource` reads the last 1 MiB of that file (as Gradle does) and its last-modified time, and a pure `parseMavenDaemonLog(tail, modifiedAt, zone)` scans backwards for:
- the latest line matching `^\d{2}:\d{2}:\d{2}\.\d{3} I Updating state to: (\w+)$`: `Idle` gives `Idle(since)`; `Busy`, `Canceled`, `StopRequested`, `Stopped`, `Broken` give `Busy`; any other value, or no line, gives `Unknown`;
- the latest `Request received: BuildRequest{` line, taking `workingDir='…'` up to `', projectDir='` as the last build directory.

No log argument, a missing or unreadable file gives `Unknown` and no build directory.

Alternatives: `registry.bin` (an internal memory-mapped binary format, per mvnd version); `mvnd --status` (reads only the registry of the mvnd version that runs it, and needs that `mvnd` on the `PATH`); the process's open sockets (don't say whether a build runs).

### Idle time from the time of day and the log's modification time
Log lines carry only the time of day. `since` is the latest instant at or before the log's last-modified time whose local time (system zone) equals the marker's time. While idle the daemon writes nothing at INFO, so the modification time is the marker's own; with DEBUG on, later lines move it forward but the rule still lands on the right day while the daemon has been idle less than 24 hours (mvnd's default idle timeout is 3 hours). The idle time is only shown and used for ordering; stopping depends only on the state.

### Model reuses `DaemonState`
`MavenDaemonProcess` gains `state: DaemonState` and `lastBuildDirectory: String?`. Reusing Gradle's `DaemonState` keeps the "idle for 12 min" labels shared. `ListMavenDaemonsUseCase` attributes by `lastBuildDirectory ?: workingDirectory`; `lsof` stays for daemons with no recorded build. Unattributed listing (Machine section, outside a repository) is unchanged.

### Stopping: `StopMavenDaemonsUseCase`
Mirrors `StopGradleDaemonsUseCase`: takes optional pids, lists again, stops a daemon only if its fresh state is `Idle`, with `kill <pid>` (SIGTERM; the daemon only ignores INT and TSTP, so the JVM exits running its shutdown hooks). Returns `MavenDaemonStopReport(stopped: List<MavenDaemonProcess>, kept: List<KeptMavenDaemon(pid, reason)>, notDaemons, failures)`, reusing `DaemonKeptReason` (`BUSY`, `STATE_UNKNOWN`) and `DaemonStopFailure`. A separate report type rather than generalising `DaemonStopReport`, whose entries carry Gradle's kind and version.
Alternatives: `mvnd --stop` (stops every daemon, busy ones included, with SIGKILL); `kill -9` (skips cleanup).

### Cleanup: same flow as Gradle
The desktop cleanup and `shogun clean` list Maven daemons before the removals (attribution needs the folders), then call `StopMavenDaemonsUseCase` with the pids of idle daemons attributed to `CleanupReport.removed`. The option is shown only for repositories declaring the Maven provider, checked by default in the dialog and an explicit `--stop-maven-daemons` flag in the CLI. Results go to the cleanup report's daemon summary, never to `failures`.

### Surfaces
- CLI: `CliCommand.MavenStop(pids, json)` under the `maven` verb; `clean` gains `stopMavenDaemons`; `shogun maven` prints the state column and `--json` adds `state`/`idleSince`/`lastBuildDirectory`; `mavenStopJson` mirrors `gradleStopJson`.
- Desktop: `MachineResources` gains `stopMavenDaemon(pid)` / `stopIdleMavenDaemons()` with the same result banner as Gradle; `MavenDaemonsView` shows the state per row, a stop button per daemon and "Stop idle daemons", and drops the "never stopped" note. Strings in `Strings`, `EnglishStrings` and `SpanishStrings`.

## Risks / Trade-offs

- [The log format is mvnd's, not a public API] → The marker is one INFO line unchanged from 1.x to 2.x; if it disappears every daemon becomes `Unknown` and nothing is stopped. Tests pin the parser to log excerpts in the shape quoted in mvnd's issues.
- [A client picks an idle daemon between the fresh check and `kill`] → Milliseconds; that build's client fails to connect or loses the daemon and mvnd starts or picks another on the next run. Same known limit as Gradle.
- [A busy build writes over 1 MiB of log after its `Busy` marker] → No marker in the window gives `Unknown`, so the daemon is kept.
- [A daemon created and still on its first build has no state line] → `Unknown`, kept.
- [Logging reconfigured so daemon INFO lines aren't written] → `Unknown`, kept.
- [Idle for more than 24 hours with DEBUG logging] → The idle time shown is a day short; stopping is unaffected.
- [Not run against a live mvnd in this session's planning] → Validation of the implementation includes a real mvnd daemon on macOS: listing it busy and idle, and stopping it.

## Migration Plan

No migration: nothing stops a daemon unless the user asks. Older ShogunAi versions keep listing only.
