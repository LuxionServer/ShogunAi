# Proposal

## Why

`orca-integration` left a known gap: with Orca's agent hooks turned off (`orca agent hooks off`), Orca can stop seeing Claude, so "Open in Orca" may start a second agent or report that one didn't start, and the control tower loses the agent's state. The roadmap's Orca step lists it as a check, with a fallback through Claude Code's session registry. This change does that check and adds the fallback.

A reproduction against Orca 1.4.243 on a disposable repository settled what actually breaks:

- **A Claude started in a new Orca terminal is still identified.** With the hooks off and applied by Orca (after restarting the app), `orca terminal show` gives `agentIdentity: claude` within a second, even in an already trusted folder, and `orca worktree ps` reports `working` and `done` from what Orca observes in the terminal (`programStatus`), not from hooks.
- **A Claude that outlives an Orca restart is lost.** Orca's daemon keeps the terminal and its Claude alive across the restart, but without hooks Orca no longer identifies it: `terminal show` gives no `agentIdentity` (it flickers back only for a moment while the agent works), the terminal loses its title, and `worktree ps` has no row for it. Opening that worktree in Orca therefore starts a second agent next to the live one, and Orca reports no state for it.
- **Turning the hooks off from the CLI while Orca runs isn't applied until a restart** (`appliedBy: offline`): before restarting, Orca kept identifying Claude and reporting its states.
- **Claude Code's session registry stays right throughout.** Each live session's file in `~/.claude/sessions/` has its process id, folder, name and a `status` that goes `busy` while it works, `waiting` while it waits for the user, and `idle` otherwise; a session that never ran a turn has its `statusUpdatedAt` within milliseconds of its `startedAt`. The Claude process started in an Orca terminal carries `ORCA_TERMINAL_HANDLE`, the handle of that terminal, in its environment. ShogunAi already reads the registry (`FileClaudeSessionRegistry`) for session names, but ignores the status.

## What Changes

- **Opening in Orca finds a live Claude Orca doesn't see.** When none of the worktree's (or the multirepo folder's) Orca terminals reports an agent, ShogunAi looks for a live Claude session in that folder in Claude Code's registry and reads the terminal handle Orca gave its process. If that terminal is one of the workspace's, the open focuses it (`focused`, with the session's name) instead of starting another agent.
- **Checking that the agent started accepts the registry too.** After starting the agent in a new terminal, the open succeeds as soon as Orca reports an agent in the terminal or a live Claude session runs in that terminal or with the name ShogunAi gave it. The 10-second limit and the error stay.
- **Agent states from the registry as a last resort.** When a Claude process runs in a worktree and no other source (Herdr, Orca, Claude Code's hooks) gives it a state, it takes the state of its live sessions in Claude Code's registry: `busy` as working, `waiting` as blocked, `idle` as done after a turn and as idle before the first one, with `stateSource` `claude-code`. It applies to every Claude, not only those in Orca.
- The documentation of Orca's agent states and of the installation stops saying that the open may fail with the hooks off, and says what ShogunAi falls back to.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `orca-integration`: reusing a live agent and verifying a started agent also through Claude Code's session registry.
- `worktree-overview`: a Claude agent without a state from any other source takes the state its session reports in Claude Code's registry.
- `claude-session-names`: the registry is read for each session's status too, not only its process id, folder and name.

## Impact

- **Core:** `ClaudeSession` and `FileClaudeSessionRegistry` gain the session's status; `ClaudeSessionNaming` exposes the live sessions of a folder; a new port to read a process's environment variable, with a macOS/Linux adapter; `OrcaAgentLauncher` (used by `OpenWorktreeInOrcaUseCase` and `OpenGroupInOrcaUseCase`) and `DetectRunningAgentsUseCase` use them. `OrcaClient` doesn't change.
- **Wiring:** the CLI and the desktop app pass the new port where they build the Orca launcher.
- **Docs:** `docs/agents.md`, `docs/guide/installation.md` and their Spanish translations; the roadmap item is removed.
- No new dependency, nothing written outside ShogunAi's own data, and no change for people who don't use Orca beyond the extra state source.
