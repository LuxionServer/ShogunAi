# Design

## Context

See proposal.md - Why. Today's facts the approach builds on:

- `WorkflowRules` is the single catalog: each rule has one text and an `inSessionStart` flag. `shogun hook session-start` injects the applicable flagged rules, and `shogun guide` prints all of them. The desktop-turn rule has condition `ALWAYS` and is flagged, so it reaches every session in every repository.
- `ClaudeCodeHooks.DESIRED` holds one hook per event, and the diff logic indexes it by event (`associateBy { it.event }`). `PreToolUse` runs `shogun hook desktop-turn` with matcher `Bash` and condition `Bash(*computer *)`.
- `hook desktop-turn` already acquires or renews the turn, and denies with a reason when another worktree holds it or others wait (`OrcaComputerUse.drivesDesktop` classifies the command). It never blocks because of its own failures.
- A no-op `shogun hook desktop-turn` takes about 0.15 s; a call that resolves the repository takes about 0.3 s.
- The Gradle daemon log records every build it receives, for example: `Starting executing command: Build{id=…, currentDir=<dir>} with connection: socket connection from /127.0.0.1:<daemon port> to /127.0.0.1:<client port>`. `ProcessGradleDaemonSource` already reads the log's tail for the state and the last build directory.
- `AgentSessionRecord` stores the agent's process id for each session of a worktree.

## Goals / Non-Goals

**Goals:**

- The session-start context only carries rules an agent needs before it acts. Rules about rare actions are enforced, or explained, at the moment the action happens.
- Whoever finds a busy daemon can see which build holds it and which session started it, without a thread dump.

**Non-Goals:**

- Blocking, warning about or intercepting `./gradlew` runs, wherever they run.
- A "service" concept for long-running dev servers, or ports for them.
- Changing the pool's limits, how it is counted, or how it trims daemons.
- Showing the holding build in the desktop app. It is a follow-up for the roadmap.

## Decisions

### Session-start text separate from the guide text

`WorkflowRule` gains an optional session-start text. `sessionStartLines` uses it when present and the full text otherwise. `shogun guide` always prints the full text. The desktop-app rule gets the short form: "Check the desktop app hidden, without computer use, with `shogun headless start`; see `shogun guide resources` for driving and capturing it." The desktop-turn and `worktrees.base` rules lose their `inSessionStart` flag.

The alternative was two rules per topic, one short and one long. It was rejected because it duplicates ids, conditions and the guide's listing.

### Claude's computer use goes through the same hook

`ClaudeCodeHooks` allows several ShogunAi hooks per event, each identified by its event and matcher. The diff, install, status and uninstall compare lists instead of single entries. `PreToolUse` gets a second group that runs `shogun hook desktop-turn` with a matcher listing exactly the computer-use tools that act on the desktop, for both prefixes Claude uses (`mcp__computer-use__` locally and `mcp__remote-devices__computer_` for a cloud session linked to the machine). Acting tools are clicks, mouse moves and drags, typing, keys, scrolling, opening an application, writing the clipboard and batches of actions. Screenshots, zoom, cursor position, waiting, switching the display, reading the clipboard, listing apps and requesting access are left alone.

The list of acting tools lives in one place in `core` (next to `OrcaComputerUse`), which both builds the matcher and classifies the hook's input, so the hook still lets a call through when it receives a tool outside the list. The matcher keeps screenshots, the most frequent call, from paying the hook's launch.

A single group with a combined matcher was rejected: the `if` condition that filters `Bash` to `*computer *` would also apply to the MCP tools.

The denial reuses today's reason text, so it already says who holds the turn, until when and to run `shogun desktop acquire --wait` in the background. The session-start rule is no longer needed, because the agent learns the rule when its first acting call is denied, and a free desktop costs it nothing.

### The base of a new worktree, printed instead of prescribed

`shogun new` prints `branched from <ref> at <short sha> <subject>` (or `at <short sha> <subject>` when the base is the current HEAD) on standard error after creating a new branch. Standard output keeps only the path, which scripts and hooks parse. With `--branch` it prints nothing new.

### Finding the build that holds a busy daemon

For a daemon reported busy, the source parses the latest `Starting executing command` entry of the log's tail it already reads: start time, build directory and client port. The client is found in three steps:

- The process that owns the client end of that loopback connection, found with `lsof -nP -iTCP:<client port> -sTCP:ESTABLISHED -Fpc`, taking the process that isn't the daemon.
- Its command line, from the same `ps` listing, which gives the tasks, the arguments after the Gradle entry point.
- Its ancestors, walked through `ps -o pid=,ppid=` until one matches the process id of a recorded agent session, which gives the session and its worktree.

Every step is optional. When `lsof` is missing or fails (Windows, sandboxed shells), the holding build still shows its directory and start time. When no ancestor matches a session, the session is omitted. Listing the daemons never fails because of this lookup.

The model adds a `HoldingBuild(directory, startedAt, client: GradleClient?)` to busy `DaemonProcess`es, where `GradleClient(pid, tasks, session: AgentSessionRef?)`. `shogun gradle` prints it as an indented line under the daemon, for example: `held by pid 81234: ./gradlew benchRun in <dir>, 2 h 14 min, session apps-TASK-123 (TASK-123)`. The JSON adds a `holder` object.

Asking the daemon over its protocol, or `jps`/`jcmd` on the client, was rejected: the first is version-specific, the second needs a JDK on the `PATH`, and both are slower than reading the log ShogunAi already reads.

## Risks / Trade-offs

- [The computer-use tool names change in a Claude release] → the matcher misses a new acting tool, and that call runs without the turn, which is today's behaviour. The tool list is covered by tests, and the hooks read as outdated when it changes.
- [The log tail doesn't contain the latest build entry, for example after a very long build with a lot of logging] → the holder is reported without client, from the directory alone. The tail is already 1 MiB.
- [The client's port is reused by another process after the build ends] → the lookup only runs for daemons currently busy, and it checks that the owner is a Gradle client before reporting it.
- [Agents relied on the desktop-turn rule in the session context] → the first acting call is denied only when there is a conflict, with the same instructions, so the outcome is the same, one step later.

## Migration Plan

Users run `shogun hooks install` after upgrading. `shogun hooks status`, and the desktop app's outdated notice, already report the `PreToolUse` change. Until then, Claude's computer use runs without the turn, as it does today.

## Open Questions

- Does renewing the device lease while the session works (`renewLeases` on `UserPromptSubmit`) make the rule "run `shogun emulator start` before every use of the emulator" unnecessary in the session context? A long session that never sends a prompt can still lose its emulator. This change doesn't touch the emulator rule. To be decided after watching it in use, possibly by having `adb` failures point to `shogun emulator start` instead.
