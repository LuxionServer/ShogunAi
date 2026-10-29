# Design

## Context

`orca-integration` reads Orca's agents through `OrcaWorktreeReader.read()`, which `DetectRunningAgentsUseCase` calls once per detection: it skips Orca when the binary is missing, checks `status()` without ever starting Orca, runs `orca worktree ps` within 2 seconds and backs off 30 seconds after a failure. `CliOrcaClient` only keeps `agentType` and `state` from each agent. After each read the reader also answers `origins()` from what it last read, and both the desktop app and `shogun status` ask it after detecting agents.

The detail's environment section shows the reserved ports from `useCases.readPorts`, one map of key to port per worktree, loaded with the overview. The fields Orca 1.4.243 actually returns are in the proposal; the recordings are `core/src/test/resources/orca/worktree-ps-tool.json` (Claude waiting on `Bash`) and `ports-all.json` (a Python dev server with `advertisedUrl`, plus listeners outside any workspace), both with placeholder paths.

## Goals / Non-Goals

**Goals:**
- Show Orca's agent details and observed ports without any extra Orca call per worktree or per project.

**Non-Goals:**
- Details from other sources (Herdr, Claude Code's hooks). Herdr has no equivalent today, and the hooks record only a state.
- Acting on the details: approving the waiting tool, opening the advertised address or stopping a listener. The detail only shows them.
- Ports of worktrees Orca doesn't know, or a port scan of ShogunAi's own. Without Orca nothing changes.
- Orca's `programStatus` (`blocked`, `kind: "permission"`, `message`), which only repeats the tool and its input for permission prompts.

## Decisions

### Agent details travel with the agent
A domain `AgentActivity(waitingOn: AgentTool?, prompt: String?, lastMessage: String?)`, with `AgentTool(name, input: String?)`, is added to `OrcaAgent` and, as `activity: AgentActivity? = null`, to `RunningAgent`. `CliOrcaClient` builds it from `toolName`, `toolInput`, `prompt` and `lastAssistantMessage`, treating blank strings as missing. `waitingOn` is set only when `state` is `waiting`, because a done agent keeps its last tool's name. An agent with nothing left is `activity = null`.

`mergeOrca` already picks the most urgent of several Orca agents of one name; it copies that agent's `activity` together with its state. When a Herdr state at least as urgent is kept, the Orca details are still attached, since Herdr has none.

*Alternative:* a separate map of details next to the agents map. Rejected: every view that shows an agent (row tooltip, detail, `status`) would have to join it again by path and name.

### Ports are read with the agents, answered like `origins()`
`OrcaClient.ports()` runs `orca ports --all --json` and returns `OrcaPort(port, process, worktreePath, url)` for each row with `kind: "workspace"` and an `owner.path`, from `processName` and `advertisedUrl`; other rows are dropped. `--all` is one call for every worktree, and per-worktree `--worktree path:` fails for paths Orca doesn't know.

`OrcaWorktreeReader.read()` runs `agentsByWorktree()` and `ports()` concurrently once `status()` says Orca is running, each within the same 2-second limit. It keeps the ports of the last successful read, and `ports(worktrees)` answers from them, like `origins()` does: a map from each listed worktree's path to its `ObservedPort(port, process, url)` list, matched by canonical path, deduplicated by port and sorted, or `null` when the last read had no ports. A failed ports read has its own 30-second back-off, so a ports failure never hides agents, and the agents' back-off covers both.

*Alternative:* a separate use case that calls `orca ports` on its own schedule. Rejected: it would duplicate the installed/running/back-off logic, add a second `status()` call per check and run once per project instead of once per check.

### Where they show
- **Detail, agents section:** each agent's label, then its details as muted lines: "Waiting on <tool>: <input>", "Prompt: …" and "Last message: …". Each is cut to three lines by a pure `shortened(text, maxLines = 3)` that appends "…" when it cuts; the `Text` also limits itself to three lines with an ellipsis, for long wrapped lines.
- **Detail, environment section:** the reserved ports' line marks each port also seen listening ("PORT 20001 (listening)"), then a "Listening, seen by Orca" label and one line per observed port: "8765 · Python · http://127.0.0.1:8765". The section shows when there are only observed ports.
- **View model:** `ProjectWorktrees` keeps `observedPorts`, set from `useCases.orca.ports(listed)` right after `detectAgents`, with `origins()`. It refreshes with every check, so the detail follows without reopening.
- **`shogun status`:** `agentJson` adds `activity`, `worktreeJson` adds `listeningPorts` (always present, `null` when the reader has no ports), and the text line adds `listening=8765,10011`. Prompts and messages stay out of the text line: they're long and multi-line, and the JSON has them.

## Risks / Trade-offs

- [Prompts and last messages can be long, private or multi-line] → They're shown only on this machine, in the detail and in local JSON, shortened in the detail; nothing is logged or stored.
- [`orca ports --all` scans the machine's listeners on every check] → Measured at about 0.2 s against 1.4.243, run alongside `worktree ps` within the existing 2-second limit and skipped entirely while Orca is closed or backing off.
- [Orca attributes ports by working directory, so a process started from a worktree but serving another one is attributed to the first] → It's what Orca shows too; the label says "seen by Orca".
- [Orca's fields change between versions] → Tolerant parsing: missing fields mean missing details or no address, never an error. The fixtures pin 1.4.243's shape.

## Migration Plan

Additive: new JSON fields only, nothing changes without Orca. Rollback is reverting the change.
