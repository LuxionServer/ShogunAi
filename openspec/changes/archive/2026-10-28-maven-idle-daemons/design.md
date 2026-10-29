# Design

## Context

See proposal.md for the motivation. Current state:

- `ProcessMavenDaemonSource` lists mvnd daemons with `ps -A -ww -o pid=,rss=,command=`, keeping processes with an argument in the `org.mvndaemon.mvnd.` package, and reads every daemon's process working directory at once with `lsof -a -p <pids> -d cwd -Fn`. `ListMavenDaemonsUseCase` attributes a daemon by that directory with `owningWorktree`. The spec forbids stopping daemons.
- The Gradle provider reads each daemon's state from the tail of its log (`ProcessGradleDaemonSource.parseDaemonLog`), models it as `DaemonState` (`Busy`, `Idle(since)`, `Unknown`), and stops idle daemons with `StopGradleDaemonsUseCase` (fresh listing, `kill <pid>`, `DaemonStopReport`). The CLI (`gradle stop`, `clean --stop-gradle-daemons`), the Machine view and the cleanup dialog are built on it.

What mvnd does, checked in its sources (tag 1.0.6 and `master`, the 2.x line) and on a real mvnd 1.0.6 daemon on macOS (Homebrew, JDK 27):

- The daemon's Java process runs `org.codehaus.plexus.classworlds.launcher.Launcher` as main class; no argument is in the `org.mvndaemon.mvnd.` package, so the current detection never lists a real daemon. The client adds `-Dmvnd.id=<id>` to every daemon (1.x and 2.x); the `mvnd` client process doesn't have it.
- The client starts each daemon with the path of its log on the command line: `-Dorg.slf4j.simpleLogger.logFile=<storage>/daemon-<id>.log` in 1.x, `-Dmaven.logger.logFile=<storage>/daemon-<id>.log` in 2.x. `<storage>` defaults to `~/.m2/mvnd/registry/<mvnd version>` and can be changed with `mvnd.daemonStorage`.
- Daemon loggers write lines `HH:mm:ss.SSS <level letter> <message>` at INFO by default. `Server.updateState` logs `Updating state to: <state>` on every change. States: `Idle`, `Busy`, `Canceled`, `StopRequested`, `Stopped`, `Broken`.
- A daemon is created in state `Busy` without logging it (so no other client grabs it).
- When a build starts, Maven's logging setup reopens the log file, truncating it: the `Request received`, `Updating state to: Busy` and any earlier `Idle` lines disappear, and the log holds only that build's dispatched messages until `Daemon back to idle` / `Updating state to: Idle` at its end. Observed on the real daemon: during a second build the log restarted with no state line.
- While idle, nothing is written at INFO; the periodic expiration check logs at DEBUG only.
- mvnd changes the process working directory to each build's directory (`EnvHelper`, native `chdir`), so `lsof` reports the directory of the last build. Observed: after a build from another folder the daemon's cwd was that folder.
- `mvnd --stop` kills every daemon with `destroyForcibly`, busy or not, and clients drop registry entries whose address no longer answers (`CleanupOnStaleAddress`). Observed: after ShogunAi stopped the daemon, the next `mvnd` build started a new one normally.

## Goals / Non-Goals

**Goals:**
- Same safeguards as Gradle: stop only what is idle on a fresh read, never something busy or whose state is unknown.
- Work with mvnd 1.x and 2.x, any `mvnd.daemonStorage`, without needing `mvnd` on the `PATH`.

**Non-Goals:**
- Windows: ShogunAi doesn't list processes there for Gradle or Maven; the log reading itself would work, so the change adds nothing that blocks it later.
- A Maven daemon pool limit or automatic trim (`resource-pools`): later, if needed.
- Reading the binary registry (`registry.bin`) or running `mvnd --status`.

## Decisions

### Detection by `-Dmvnd.id=`
`parseMavenDaemons` keeps a process when an argument starts with `-Dmvnd.id=`, or (kept for safety) with `org.mvndaemon.mvnd.`. It also returns the log path: the text after `-Dorg.slf4j.simpleLogger.logFile=` or `-Dmaven.logger.logFile=` up to the first `.log` followed by whitespace or the end of the line, so a storage folder with spaces still parses.

### State from the daemon log named on the command line
`ProcessMavenDaemonSource` reads the last 1 MiB of that file (as Gradle does) and its last-modified time, and the pure `parseMavenDaemonState(tail, modifiedAt, zone)` takes the latest line matching `^\d{2}:\d{2}:\d{2}\.\d{3} I Updating state to: (\w+)$`:
- `Idle` gives `Idle(since)`; `Busy`, `Canceled`, `StopRequested`, `Stopped`, `Broken` give `Busy`; any other value gives `Unknown`;
- no such line gives `Busy`: the log is restarted when a build starts and only gets a state line when it ends, and a new daemon is `Busy` in mvnd's own registry;
- no log argument, a missing or unreadable file gives `Unknown`.

A busy daemon whose build writes over 1 MiB has no state line in the window, which also reads as `Busy`. Both `Busy` and `Unknown` are kept when stopping.
Alternatives: `registry.bin` (an internal memory-mapped binary format, per mvnd version); `mvnd --status` (reads only the registry of the mvnd version that runs it, and needs that `mvnd` on the `PATH`); the process's open sockets (don't say whether a build runs).

### Idle time from the time of day and the log's modification time
Log lines carry only the time of day. `since` is the latest instant at or before the log's last-modified time (plus a minute for file systems with second precision) whose local time (system zone) equals the marker's time. While idle the daemon writes nothing at INFO, so the modification time is the marker's own; with DEBUG on, later lines move it forward but the rule still lands on the right day while the daemon has been idle less than 24 hours (mvnd's default idle timeout is 3 hours). The idle time is only shown; stopping depends only on the state.

### Attribution stays on the process working directory
Because mvnd moves the daemon's working directory to each build's directory, the existing `lsof` attribution already names the last build's worktree. The log can't help: the `Request received` line with the build directory is erased when the build starts. `MavenDaemonProcess` gains only `state: DaemonState`, reusing Gradle's `DaemonState` so the "idle for 12 min" labels are shared.

### Stopping: `StopMavenDaemonsUseCase`
Mirrors `StopGradleDaemonsUseCase`: takes optional pids, lists again, stops a daemon only if its fresh state is `Idle`, with `kill <pid>` (SIGTERM; the daemon only ignores INT and TSTP, so the JVM exits running its shutdown hooks). Returns `MavenDaemonStopReport(stopped: List<MavenDaemonProcess>, kept: List<KeptMavenDaemon(pid, reason)>, notDaemons, failures)`, reusing `DaemonKeptReason` (`BUSY`, `STATE_UNKNOWN`) and `DaemonStopFailure`. A separate report type rather than generalising `DaemonStopReport`, whose entries carry Gradle's kind and version.
Alternatives: `mvnd --stop` (stops every daemon, busy ones included, with SIGKILL); `kill -9` (skips cleanup).

### Cleanup: same flow as Gradle
The desktop cleanup and `shogun clean` list Maven daemons before the removals (attribution needs the folders), then call `StopMavenDaemonsUseCase` with the pids of idle daemons attributed to `CleanupReport.removed`. The option is shown only for repositories declaring the Maven provider, checked by default in the dialog and an explicit `--stop-maven-daemons` flag in the CLI. Stopped and kept Maven daemons are added to the cleanup report's daemon counts, never to `failures`.

### Surfaces
- CLI: `CliCommand.MavenStop(pids, json)` under the `maven` verb; `clean` gains `stopMavenDaemons`; `shogun maven` prints the state column and `--json` adds `state`/`idleSince`; `mavenStopJson` mirrors `gradleStopJson`; the clean JSON gains `providers.mavenDaemonsStopped`.
- Desktop: `MachineResources` gains `stopMavenDaemon(pid)` / `stopIdleMavenDaemons()` with a result notice like Gradle's; `MavenDaemonsView` shows the state per row, a stop button per daemon and "Stop idle daemons", and drops the "never stopped" note. Strings in `Strings`, `EnglishStrings` and `SpanishStrings`.

## Risks / Trade-offs

- [The log format and the restart of the log are mvnd's behaviour, not a public API] → If the state line disappears, every daemon reads as `Busy` and nothing is stopped; if the log stops being restarted, the `Busy` line written when a build starts is then kept and still read as busy. Either way a busy daemon is never read as idle.
- [A client picks an idle daemon between the fresh check and `kill`] → Milliseconds; that build's client loses the daemon and mvnd starts or picks another on the next run. Same known limit as Gradle.
- [Logging reconfigured so daemon INFO lines aren't written] → No state line, `Busy`, kept.
- [Idle for more than 24 hours with DEBUG logging] → The idle time shown is a day short; stopping is unaffected.
- [Validated on mvnd 1.0.6 only] → 2.x shares the same `Server`, logger and command line construction in its sources (log argument renamed, handled); its behaviour on a real daemon is unverified.

## Migration Plan

No migration: nothing stops a daemon unless the user asks. Older ShogunAi versions keep listing only (and, through the detection bug, list no real daemon).
