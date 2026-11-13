# Design

## Context

Core already identifies the build that holds a busy Gradle daemon (`gradle-provider`, "Identify the build that holds a busy daemon"): `ProcessGradleDaemonSource` fills `DaemonProcess.holder` (`HoldingBuild`: directory, start, and the `GradleClient` with its process id and tasks when the connection can be inspected), and `ListGradleDaemonsUseCase`, given an `AgentSessionStore` and the worktrees whose sessions to look in, adds the `AgentSessionRef` of the session the client descends from. `shogun gradle` builds that use case with `ports.agentSessions` and every known worktree path, and prints the holder as an indented line under the daemon.

The desktop app's machine view reads the daemons through `MachineUseCases.listDaemons`, wired in `AppContainer` as `gradleDaemonSource.list()`. That already carries the holder's directory, start and client, but never the session, because it skips the use case. `GradleDaemonsView` only shows "busy" in the state column.

Constraints: `MachineResources.kt`, `AppContainer.kt` and the three i18n `Strings` files are at their file length caps; other changes run in parallel on `ui/shell`, `XcodeDerivedDataView`, the CLI and core, so this one stays in the Gradle view, its holder (`MachineResources`) and their wiring.

## Goals / Non-Goals

**Goals:**

- Show, for each busy daemon whose holding build core identifies, the build's tasks, how long ago it started and the agent session that launched it.
- Get the session exactly as `shogun gradle` does, through `ListGradleDaemonsUseCase` with the session store, without a second lookup in the app.

**Non-Goals:**

- Changing what core identifies, or the CLI's output.
- Showing the client's process id or the build's directory: the owner column already names the worktree of the daemon's build, and a process id isn't actionable from the app.
- Linking the session to its worktree or to the agent's terminal.
- The worktree screen's own Gradle section (`GradleUseCases.listDaemons`), which lists a project's daemons and isn't the machine's Gradle view.

## Decisions

### A detail line under the busy daemon's row

The build is shown as a secondary line right under the daemon's row, only for daemons with a holding build (core sets one only on busy Gradle daemons), with the same left padding as the row's cells: `building :app:assembleDebug · started 3 minutes ago · agent session claude 1a2b3c4d`. It mirrors `shogun gradle`, which prints the holder indented under the daemon, and keeps the row itself on one line like every machine list (`Machine lists share the list row`).

Alternatives considered: a tooltip on the "busy" state hides the answer behind hovering and is unreachable from the keyboard; a detail pane would be the only one in the machine views; packing it into the state column (170 dp) truncates any real task list.

Each part is left out when core lacks it: no tasks (no client found, or a client that asked for none) reads "running a build"; no start leaves out "started …"; no session leaves out the session. The start uses the app's `relativeTime`, as the idle state does ("idle · 5 minutes ago"). The session shows its agent and the first eight characters of its id, enough to tell sessions apart in a line.

The words live in one new `Strings.daemonBuild(tasks, started, session)` next to the other daemon texts, in English and Spanish; the view passes it data only (the joined tasks, the relative time, and `agent` plus the short id).

### Listing through the use case, as the CLI does

`MachineUseCases.listDaemons` changes from `suspend () -> Result<List<DaemonProcess>>` to `suspend (worktreePaths: List<String>) -> Result<List<DaemonProcess>>`. `AppContainer` wires it as `ListGradleDaemonsUseCase(gradleDaemonSource, fileManager, ports.agentSessions)(null) { worktreePaths }` mapped to the processes; with `null` worktrees the use case attributes no owner (the view keeps attributing owners across every saved project, as today) and only adds the sessions. `MachineResources` passes the paths of every saved project's listed worktrees, which is the same set `shogun gradle` searches (`knownWorktreePaths`). The use case only reads the session store when some busy daemon has a client with ancestors, so idle refreshes cost nothing more.

The holder stays the `DaemonProcess` the view already receives (`MachineDaemon.process.holder`), so `MachineResources` gains no state.

### File length caps

The only lines this change adds to capped files are the new text: one declaration in `Strings.kt` (956 → 957) and a two-line override in `EnglishStrings.kt` (1129 → 1131) and `SpanishStrings.kt` (1127 → 1129). The task asks to keep texts next to the other Gradle daemon texts in those files, and splitting them into a new file just for this text would be a refactor outside this change. `AppContainer.kt` and `MachineResources.kt` change lines in place and keep their caps.

## Risks / Trade-offs

- [Long task lists make the line wrap] → it's a secondary text under the row, so wrapping only makes that daemon's block taller; the row's cells keep their widths.
- [The session lookup reads the session store of every saved worktree on a refresh with a traced client] → the same cost `shogun gradle` pays, and only while a build runs; failures reading one worktree's sessions are ignored by the use case.
- [Changing `MachineUseCases.listDaemons`'s signature touches tests that build it] → their lambdas take no named parameter, so they keep compiling with the implicit parameter.
- [The first listing can run before the saved projects' worktrees are loaded, so it finds no session] → the next refresh of the shown view passes the worktrees and adds the sessions, as checked in the running app; the tasks and start show from the first listing.
