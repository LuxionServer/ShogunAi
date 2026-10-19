# Design

## Context

See proposal.md for motivation. `DetectRunningAgentsUseCase` lists working directories with `lsof` and executables with `ps`, and attributes each recognized agent to the deepest worktree containing its directory (`owningWorktree`). The watcher runs it once per check for all saved projects, paused while the window is in the background. `CliHerdrClient` runs `herdr` subcommands that return JSON. `herdr agent list` returns, per agent pane, `agent`, `agent_status` (`idle`, `working`, `blocked`, `done`, `unknown`), `cwd`, `foreground_cwd`, `pane_id` and `workspace_id`.

## Goals / Non-Goals

**Goals:**
- One extra `herdr` call per check at most, and none when Herdr isn't installed or its server isn't running.

**Non-Goals:**
- States from other multiplexers or agents' own APIs.
- Notifications when an agent becomes blocked; the row update is the signal for now.
- Sending prompts or keys to agents from ShogunAi.

## Decisions

**Herdr state on `RunningAgent`.** `RunningAgent(name, herdrState: HerdrAgentState? = null, herdrPaneId: String? = null)`. `HerdrAgentState` is `WORKING`, `BLOCKED`, `DONE`, `IDLE`; Herdr's `unknown` maps to `null`.

**Merging sources.** Detection runs the process scan and `herdr agent list` concurrently. Each Herdr agent is attributed with `owningWorktree(foreground_cwd ?: cwd)`. Within a worktree, a Herdr agent with the same name as a process-detected one enriches it; one without a match is added. Several panes of the same agent in one worktree keep the most urgent state (`BLOCKED` > `DONE` > `WORKING` > `IDLE`). A Herdr failure (not installed, server not running, timeout) yields no Herdr data and never fails detection; the process scan alone behaves as today.

**Avoiding calls when Herdr is absent.** The client resolves the executable once; when it isn't found, `listAgents` returns empty without running anything. When the server isn't running, `herdr agent list` fails fast; after a failure the next attempt waits 30 seconds, so a stopped server doesn't add a failing process to every check.

**Activity.** In `worktreeActivity`, a worktree whose agents include `BLOCKED` or `DONE` is `NeedsYou`; otherwise any running agent is `AgentWorking`, as today. `NeedsYou`'s hint and criterion strings mention agents waiting for the user.

**Presentation.** The agent chip reads "Claude · blocked" with the warning status for blocked, info for working, success for done and neutral for idle. The detail's agents section shows the same per agent.

**Focusing the agent.** "Open in Herdr" on a worktree with a Herdr agent runs `herdr agent focus <pane>` instead of opening or focusing the workspace.

**CLI.** `shogun status` prints `claude (blocked)`; `status --json` adds `agents: [{ name, herdrState }]` to each worktree object (additive, per the existing contract).

## Risks / Trade-offs

- [Herdr's JSON changes] → Fields are read leniently; a missing or unknown `agent_status` gives no state, never an error.
- [An agent's foreground directory moves outside the worktree, e.g. `cd ..`] → It's attributed where its foreground directory is, as with processes; falls back to `cwd` only when `foreground_cwd` is absent.
- [Moving blocked/done agents to "Needs you" changes where users look] → That's the intent; the hint says why.
