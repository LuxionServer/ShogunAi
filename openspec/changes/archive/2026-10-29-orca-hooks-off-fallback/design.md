# Design

## Context

`OrcaAgentLauncher` drives every open in Orca (a worktree, a group member, the multirepo agent):

- `focusLiveAgent` asks Orca, terminal by terminal, for an `agentIdentity`, and focuses the first terminal that has one.
- Otherwise `start` creates a terminal and polls `terminalAgent` for up to 10 seconds, then waits for the named session in Claude Code's registry through `ClaudeSessionNaming.awaitSession`.

`DetectRunningAgentsUseCase` merges, per worktree:

1. the process scan (`lsof`/`ps`);
2. the states recorded by ShogunAi's own Claude Code hooks;
3. Herdr's and Orca's agents;
4. the session names from Claude Code's registry (`withClaudeSessions`).

The reproduction (see the proposal) found that with Orca's hooks off, Orca 1.4.243 still identifies a Claude started in a new terminal, but loses a Claude whose terminal outlived an Orca restart: no `agentIdentity`, no title, and no `worktree ps` row. In the same reproduction, Claude Code's registry file for that session kept a correct `status`, and the process carried `ORCA_TERMINAL_HANDLE=<handle>`, the handle `orca terminal list` gives for its terminal.

## Goals / Non-Goals

**Goals:**
- An open in Orca never starts a second agent next to a live Claude that Orca doesn't report, as long as ShogunAi can tell which terminal it runs in.
- An open doesn't fail with `OrcaAgentNotStarted` when the agent did start and Claude Code's registry shows it.
- The control tower keeps a state for a Claude agent that no other source describes.

**Non-Goals:**
- Changing Orca's hook setting, or telling the user to. ShogunAi works with either.
- Non-Claude agents with Orca's hooks off: they have no registry. They behave as today.
- Orca's own view of the lost agent. ShogunAi can't give Orca its identity back.
- Windows: there, ShogunAi can't read another process's environment, so focusing falls back to today's behaviour. Confirming a start by session name still works.

## Decisions

### Read the registry's status, not only names
`ClaudeSession` gains:
- `status: ClaudeSessionStatus?`, parsed from `busy`, `waiting` and `idle`, where anything else is `null`;
- `statusUpdatedAt: Long?`.

A domain property maps them to `AgentState`:

| Status | State |
|---|---|
| `busy` | `WORKING` |
| `waiting` | `BLOCKED` |
| `idle`, `statusUpdatedAt` more than 5 seconds after `startedAt` | `DONE` |
| `idle` otherwise | `IDLE` |
| `null` | no state |

The 5-second margin separates a session that ran a turn from one waiting for its first prompt: the reproduction showed 83 ms for a fresh session against tens of seconds after a turn. Without either timestamp, an `idle` session is `IDLE`.

*Alternative:* map `idle` to `DONE` as Orca does. Rejected: a fresh session would land under "Needs you" before the user has asked anything, which ShogunAi's own hooks never do (they record nothing until the first prompt).

### Registry state as the last source, labelled `claude-code`
`withClaudeSessions` already filters the registry to sessions whose process is a Claude found in the scan, and attributes each to its worktree with the same rules as processes (deepest worktree, not across a nested repository, exact folder for the multirepo agent). It now also keeps the most urgent registry state per worktree, from every attributed session, named or not. A `claude` agent there that still has no state after the hooks, Herdr and Orca get that state with `AgentStateSource.CLAUDE_CODE`.

So every other source keeps its precedence, and nothing changes where today's sources already give a state.

*Alternative:* a new `AgentStateSource.CLAUDE_SESSIONS`. Rejected: the state comes from Claude Code either way, and a new source would reach the CLI JSON, the UI strings and their tests for no difference users can act on.

### Find the terminal of a session through its process's environment
Orca exports `ORCA_TERMINAL_HANDLE` to the processes of each terminal. A new port, `ProcessEnvironmentReader`, has one method: `suspend fun variable(pid: Long, name: String): String?`.

Its adapter, `SystemProcessEnvironmentReader`, works per platform:
- **macOS:** runs `ps -E -ww -o command= -p <pid>` as argv through `ShellCommandExecutor` with the listing timeout, and takes the `NAME=` token. The handles have no spaces.
- **Linux:** reads `/proc/<pid>/environ`.
- **Windows, or any failure:** returns `null`.

*Alternatives considered:*
- Match terminals by title. Rejected: a restored terminal loses its title.
- Remember the handles ShogunAi created. Rejected: it misses agents started by hand, and adds a store for something the process already carries.
- Focus the worktree's only terminal. Rejected: it guesses wrong as soon as a dev server shares the worktree.

### Fallback in `focusLiveAgent`
When no terminal reports an agent:
1. Take the folder's live sessions from `ClaudeSessionNaming.liveSessionsIn(folder)`, the same liveness check and exact-folder match as `focusedSessionName`.
2. Order them with the expected name first, then the newest `startedAt`.
3. Read each one's `ORCA_TERMINAL_HANDLE`.
4. The first handle that is one of the workspace's terminals gets `switchTo` and the window raised. The result is `OrcaOpening(FOCUSED, handle, "claude", session.name)`.
5. If none matches, start an agent as today.

The environment reader goes into `ClaudeSessionNaming`, an optional constructor parameter next to the registry, as `terminalOf(session)`. That way the three Orca use cases and their callers keep their signatures, and only the places that build `ClaudeSessionNaming` for Orca (the CLI's `claudeSessionNaming` and the desktop app's three Orca actions) pass it.

### Confirming a start through the registry
The polling loop in `start` ends when any of these holds:
- `terminalAgent(handle)` is non-null;
- a live session in the folder has the name ShogunAi gave it;
- a live session in the folder has `ORCA_TERMINAL_HANDLE` equal to the new handle.

`freeName` already makes the name unique among live sessions, so a matching name is ours. The timeout, the error and the later `awaitSession` stay as they are.

## Risks / Trade-offs

- [Claude Code's registry format isn't a public contract] → Parsed tolerantly like today. An unknown or missing status gives no state, and every path falls back to today's behaviour.
- [`ps -E` costs one process per session when Orca reports no agent] → It only runs on an explicit open, only for live sessions in that one folder, and with the listing timeout.
- [A session's environment shows where it started] → `ORCA_TERMINAL_HANDLE` is set when the process starts, and a Claude doesn't move between terminals, so it holds. A handle that isn't one of the workspace's current terminals is ignored.
- [The 5-second rule could read a long-idle fresh session as done if its status is rewritten without a turn] → The rule follows what Claude Code writes today. It's covered by tests, and the worst case is a "done" instead of an "idle".

## Migration Plan

Additive: nothing changes when Orca reports its agents, and people without Orca only gain the registry state for Claude agents that had none. Rollback is reverting the change.

## Open Questions

None.
