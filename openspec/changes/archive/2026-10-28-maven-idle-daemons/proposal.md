# Proposal

## Why

The Maven provider lists the Maven daemons (mvnd) with their memory and worktree but never stops them, because their busy or idle state was thought to live only in mvnd's binary registry. Every worktree where an agent ran `mvnd` leaves a daemon of several hundred megabytes behind for hours (mvnd's default idle timeout is three hours), including daemons of worktrees already removed. Reading mvnd's sources (1.0.6 and the 2.x line) and running a real mvnd 1.0.6 daemon show each daemon records its state changes in its own log file, whose path is on the daemon's command line, so the state can be read the way the Gradle provider reads it. This is the last open item of roadmap point 1.

## What Changes

- Each Maven daemon is reported as busy, idle (with the time it became idle) or unknown, read from the latest `Updating state to:` line of the daemon log named by its `-Dorg.slf4j.simpleLogger.logFile=` (mvnd 1.x) or `-Dmaven.logger.logFile=` (mvnd 2.x) argument. A readable log with no state line is busy (mvnd restarts the log when a build starts, and creates daemons busy); a daemon without that argument, or a missing or unreadable log, gives unknown.
- Maven daemons are found by their `-Dmvnd.id=` argument. Real daemons run with the Plexus Classworlds launcher as main class, so the current detection (an argument in the `org.mvndaemon.mvnd.` package) never found them.
- Idle Maven daemons can be stopped, checking the state again just before stopping each one; busy and unknown ones are kept and reported with the reason.
- `shogun maven` shows each daemon's state; new `shogun maven stop [<pid>...]`; `shogun clean --stop-maven-daemons` stops the idle daemons of removed worktrees.
- The Machine section's Maven daemons view shows the state and offers stopping one daemon or every idle one; the cleanup dialog offers, checked by default for repositories that declare the Maven provider, to also stop the idle Maven daemons of the worktrees it removes.
- **BREAKING** (behavior): the requirement "Maven daemons are never stopped" is removed. Nothing is stopped without an explicit user action.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `maven-provider`: adds reading a daemon's state and stopping idle daemons; removes "Maven daemons are never stopped".
- `command-line-tool`: `shogun maven` shows the state; adds `shogun maven stop` and `shogun clean --stop-maven-daemons`.
- `machine-resources`: adds the Maven daemons view with state and stop actions.
- `worktree-cleanup`: adds the option to stop the Maven daemons of removed worktrees.

## Impact

- Core: `MavenDaemonProcess` gains a state; `ProcessMavenDaemonSource` reads the log tail; new `StopMavenDaemonsUseCase`.
- CLI: `CliCommand`, `ShogunCli`, `CommandJson`, `CliHelp`.
- Desktop: `MachineResources`, `MavenDaemonsView`, the cleanup dialog and its flow in `ProjectWorktrees`/`WorktreeBoard`, strings in English and Spanish.
- Docs: Maven provider pages (`docs/`, both languages) and the roadmap.
- Windows stays out, as for Gradle: ShogunAi doesn't list processes there.
