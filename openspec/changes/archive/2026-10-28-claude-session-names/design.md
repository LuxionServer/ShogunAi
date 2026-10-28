# Design

## Context

`shogun open --herdr` types the agent command into a Herdr pane through `HerdrAgentLauncher.launch`, which three use cases share: `OpenWorktreeInHerdrUseCase` (one worktree, also used per member by `OpenGroupInHerdrUseCase`) and `OpenMultirepoAgentInHerdrUseCase`. The command comes from `AgentLaunchConfig.resolvedCommand()`, free text that may be prefixed with `headroom wrap`. `shogun status` gets agents from `DetectRunningAgentsUseCase`, which scans `lsof`/`ps`, attributes each process to a worktree with `attributedWorktree` (deepest owning worktree, rejecting folders inside a nested repository, exact match for the multirepo folder) and already collects the pids of live `claude` processes (`claudePids`) to filter `AgentSessionStore` records.

Claude Code 2.1.312 writes `<config dir>/sessions/<pid>.json` per running session, with `pid`, `sessionId`, `cwd`, `name`, `nameSource` (`derived` or `user`), `status` and more (`peerProtocol` 1). `claude -n <name>` sets the name exactly and accepts duplicates. The file is removed on a normal exit and can be left behind when the process is killed. The same folder holds other files (`<pid>.<hash>.key`). The config dir is `$CLAUDE_CONFIG_DIR` or `~/.claude`, as `ClaudeCodeHooks.userSettingsPath` already resolves.

## Goals / Non-Goals

**Goals:**
- Every Claude session ShogunAi launches in Herdr has a predictable, unique, shell-safe name.
- `shogun open --herdr` and `shogun status` tell a coordinating agent which session runs where.
- Reading the undocumented registry can never break `open` or `status`.

**Non-Goals:**
- Showing session names in the desktop app's views (the desktop app only gets the naming, through the shared use cases).
- Renaming sessions that are already running, or sessions not launched by ShogunAi.
- Naming agents other than Claude Code.
- Writing to Claude Code's registry or depending on its other fields (`status`, `messagingSocketPath`).

## Decisions

### A new port, `ClaudeSessionRegistry`, separate from `AgentSessionStore`

`AgentSessionStore` is ShogunAi's own hook-fed state, keyed by worktree and written by ShogunAi. The registry is Claude Code's, read-only and machine-wide. Mixing them would make one port both writable and foreign. The port is `fun list(): List<ClaudeSession>` with `data class ClaudeSession(val pid: Long, val cwd: String, val name: String?, val startedAt: Long? = null)`. `FileClaudeSessionRegistry(directory: String)` lists `*.json` files only, parses each with a lenient `Json { ignoreUnknownKeys = true }` into a nullable-field DTO, drops entries without `pid` or `cwd`, and swallows every per-file and directory error (returning what it could read). The directory is built at the composition roots (`ShogunCli` and `AppContainer`) from `CLAUDE_CONFIG_DIR` or the user home, so tests pass a temporary folder and never touch the real home.

Alternative considered: asking Claude Code for its sessions through a command. There's no such command; `ListAgents` lives inside Claude Code only.

### Liveness: `claudePids` for status, a process check for open

`status` already has the set of live `claude` pids from `ps`, so a session counts only if its `pid` is in it; this also rejects a reused pid that now belongs to another program. `open` doesn't scan processes, so the registry consumers in the launcher take an `isClaudeProcessAlive: (Long) -> Boolean` defaulting to `ProcessHandle.of(pid)` being alive with a command ending in `claude`, and tests inject a fake. Liveness stays out of the port so the file implementation is a pure reader.

### Naming rules live in the domain, next to `AgentLaunchConfig`

- `sessionName(vararg parts)`: lowercase, every run of characters outside `[a-z0-9]` becomes `-`, trim hyphens, join parts with `-`, cap at 64 characters (trimming a trailing hyphen again). Names never need shell quoting because `runInPane` types them into a shell.
- Names: group member `<alias>-<group.title>` (title is the branch after its last `/`); single repository `<project>-<worktree folder>`, where `<project>` is the base repository's folder name as `ProjectDefaults` derives it; main worktree (`Worktree.isMain`) `<project>`; multirepo agent `multirepo.name`.
- `namedClaudeCommand(command, name): String?` returns the command with ` -n <name>` appended, or `null` when it must be left alone. It tokenizes on whitespace and rejects the command when any token is a shell control operator (`;`, `&&`, `||`, `|`, `&`) or contains one, or when it has `-n`/`--name`/`--name=…`. It then skips leading `NAME=value` tokens, an `env` token, and `headroom wrap`, and accepts only when the next token's basename is exactly `claude`. Appending at the end is right for both `claude …` and `headroom wrap claude …`, because Headroom passes the rest to Claude.
- Uniqueness: before launching, the launcher reads the live sessions' names and appends `-2`, `-3`… to the first free one. Duplicate names are only possible between live sessions, so dead registry files don't consume names.

Alternative considered: quoting the name instead of sanitizing. Rejected: the name is also a messaging address and sanitized names are easier to type and compare.

### `HerdrAgentLauncher` does the naming and the wait

`launch` gains `sessionName: String?`. When it's non-null, the registry is set and `namedClaudeCommand` accepts the command, the launcher makes the name unique, types the named command, waits for the agent as today, and then polls the registry (same `pollInterval`, up to `AGENT_START_SECONDS`) for a live session with that name whose `cwd` is the pane's folder (`sameDirectory`). The confirmed name goes into `HerdrOpening.sessionName: String?`; a timeout leaves it `null` and doesn't fail the open, because Herdr already confirmed the agent. `focusLiveAgent` gains the folder and fills `sessionName` from the live sessions whose `cwd` is that folder: the expected name when one matches, otherwise the most recently started one (highest `pid` is not reliable, so the registry's `startedAt` is kept in `ClaudeSession` as an optional field for this ordering).

The use cases only compute the name and pass the folder; the launcher constructor gains `registry: ClaudeSessionRegistry? = null`, so existing callers and tests keep working and the desktop app gets naming by passing the registry in `AppContainer`. With no registry, the launcher still appends `-n` (without the uniqueness check) and reports the typed name as unconfirmed `null`.

### Status: sessions on `RunningAgent`

`RunningAgent` gains `sessions: List<ClaudeSessionRef> = emptyList()` with `ClaudeSessionRef(name: String, pid: Long)`. `DetectRunningAgentsUseCase` takes `registry: ClaudeSessionRegistry? = null`; after `withSessionStates`, it reads the registry once, keeps sessions with a name and a `pid` in `claudePids`, attributes each with the existing `attributedWorktree(cwd, worktrees, exact)`, and attaches the sorted list to that worktree's `claude` agent. Sessions are attached only to an existing `claude` agent (one found by the process scan, which is what established the pid is live in that worktree). Registry errors are already swallowed by the implementation; the use case additionally wraps the read in `runCatching` so a broken registry never fails `status`.

`agentLabel` becomes `name(state, n1, n2)` with any of the parts present: `claude(working, app-task-123)`, `claude(app-task-123)` when there's no state, `claude(working)` when there are no names. `agentJson` adds `sessions: [{name, pid}]`, additive as the status JSON contract requires; `name` stays the agent's name, which is why the session data goes in a nested list instead of top-level `name`/`pid`.

### Open output

`herdrOpeningLine` appends ` as session <name>` when the name is known. `openJson`, each entry of `groupOpenJson` and `multirepoAgentOpenJson` add `session_name` (string or `null`), following those objects' snake_case keys.

## Risks / Trade-offs

- [The registry format is internal and may change] → Every field is optional, unknown fields are ignored, and failures degrade to "no names": `open` and `status` keep working, only the new fields go `null`/empty.
- [A future Claude Code may reject `-n` or change its meaning] → The agent-start check would then fail the open as today with the typed command in the error, which makes the cause visible; a project can avoid it by passing its own `-n` or wrapping the command.
- [Waiting for the name adds up to 10 seconds to an open] → Claude Code writes the file at startup, before Herdr reports the agent, so in practice the first poll finds it; the wait only runs when a name was typed.
- [`ProcessHandle` liveness on Windows and for wrapped processes] → The command check accepts paths ending in `claude` or `claude.exe`; when the command can't be read the process counts as alive only if `ProcessHandle` says so, which errs towards treating a name as taken and adding a suffix, never towards reusing a live name.
- [Two different projects with the same repository folder name and task id] → The uniqueness suffix keeps their names apart.
