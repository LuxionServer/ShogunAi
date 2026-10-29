# Design

## Context

`OpenWorktreeInHerdrUseCase` asks Herdr to open the worktree (`herdr worktree open`). When the workspace was already open, `HerdrAgentLauncher.focusLiveAgent` focuses a pane with a live agent if there is one; otherwise the use case always splits the root pane with the worktree's environment and types the agent command into the new pane, leaving every other pane in place. `OpenMultirepoAgentInHerdrUseCase` does the same for the multirepo agent's plain workspace (split without extra variables). `OpenGroupInHerdrUseCase` runs `OpenWorktreeInHerdrUseCase` per worktree, so it inherits whatever that use case does.

On a fresh open the workspace keeps a single pane: the root pane when the environment is empty, or a pane split with the environment from which the root pane is then closed. So a workspace whose agent was quit usually holds exactly one pane, in the worktree, whose shell already carries the worktree's `shogun env` variables.

### What Herdr reports about a pane (checked with Herdr 0.9.5)

`herdr pane list` returns per pane: `pane_id`, `workspace_id`, `tab_id`, `terminal_id`, `cwd`, `foreground_cwd`, `focused`, `revision`, `scroll`, `agent_status` and, only when an agent is detected, `agent`, `agent_session` and `terminal_title`. It has no field telling what runs in the foreground, so from the list alone a shell at its prompt can't be told apart from one running `npm run dev`.

`herdr pane process-info --pane <id>` does tell them apart. It returns `shell_pid`, `foreground_process_group_id` and `foreground_processes` (each with `pid`, `name`, `argv`, `cmdline`, `cwd`):

- A shell at its prompt: `{"foreground_process_group_id":17820,"foreground_processes":[{"argv":["-zsh"],"name":"zsh","pid":17820,…}],"shell_pid":17820}`; the foreground process group is the shell's own.
- A pane running a program (here `claude` with its children): `foreground_process_group_id` 12517, `shell_pid` 12511; the foreground group belongs to another process.

## Goals / Non-Goals

**Goals:**

- Reuse the workspace's pane when doing so can't interfere with anything the user is running, so returning to a worktree doesn't pile up panes.
- Keep the current split as the fallback whenever the pane can't be proven free.

**Non-Goals:**

- Closing panes, or reusing a pane when the workspace has several (the user may have arranged them on purpose).
- Refreshing the environment of a reused shell.
- Changing how a fresh workspace is opened or how a live agent is focused.
- The Orca integration, which has its own rules.

## Decisions

### A pane is reusable when it is the workspace's only pane, has no live agent, is in the folder and its shell is at its prompt

`HerdrAgentLauncher` gains `reusablePane(workspaceId, folder): String?`, called only after `focusLiveAgent` found no live agent. It lists the workspace's panes and returns the pane id only when:

1. the workspace has exactly one pane;
2. that pane has no live agent (`agent_status` is `unknown`);
3. its directory (`foreground_cwd`, else `cwd`) is the folder, compared with `sameDirectory`;
4. `herdr pane process-info` reports `foreground_process_group_id == shell_pid`.

Otherwise it returns `null` and the caller splits as today.

Alternatives considered:

- *Only "single agentless pane in the worktree"* (the direction first agreed): a dev server started by hand in that pane, with the agent quit, would receive the agent command typed into it. `process-info` closes that gap at the cost of one extra `herdr` call, so it's worth it.
- *Reuse any idle pane, even among several*: more aggressive; with several panes the user has a layout of their own and typing into one of them is less predictable. Kept out on purpose; the split remains.
- *Close the idle pane and split a fresh one with the environment* (the user's rejected option 2, made safe by `process-info`): it would refresh the environment, but it throws away the pane's scrollback, including Claude's "Resume this session with…" line, and still closes a pane the user didn't ask to close.

### Failure to read process info falls back to splitting

If `process-info` fails (older Herdr without the subcommand, a platform where it isn't supported, an unexpected response), or `shell_pid` or `foreground_process_group_id` is missing, the pane counts as busy. The open never fails because of this read; it just splits as before. The port method returns `null` for "unknown", and the launcher treats only an explicit equality as free.

### Port shape

`HerdrClient` gains `suspend fun paneProcesses(paneId: String): HerdrPaneProcesses?`, with `data class HerdrPaneProcesses(val shellPid: Long?, val foregroundProcessGroupId: Long?)` and an `atPrompt` property (`shellPid != null && shellPid == foregroundProcessGroupId`). `CliHerdrClient` runs `listOf(herdr, "pane", "process-info", "--pane", paneId)` and parses `result.process_info`; any `herdr` error is caught there and returned as `null`. The process list itself isn't modelled because the decision only needs the two ids.

`FakeHerdrClient` reports every pane at its prompt by default and takes `busyPanes: Set<String>` and `unknownProcessPanes: Set<String>` to simulate a running program and a failed read; it records `processReads` so tests can check the read only happens when the other conditions hold.

### No environment resolution when reusing

When the pane is reused, `OpenWorktreeInHerdrUseCase` doesn't call `ResolveWorktreeEnvironmentUseCase`: the pane's shell got the environment when the workspace was first opened (ports were reserved then and stay reserved for the worktree), and resolving it only to throw the result away could fail the open for nothing. Resolution keeps happening before any split, as today.

### Outcome and session naming are unchanged

A reused pane goes through `HerdrAgentLauncher.launch` like a split one, with outcome `STARTED_AGENT`, the same session name and the same 10-second detection. The pane id in the result is the reused pane's, so the CLI prints e.g. `started Claude in …'s open Herdr workspace (pane w1:p1)`.

### Same rule for the multirepo agent

`OpenMultirepoAgentInHerdrUseCase` already shares `focusLiveAgent` and `launch`; it calls `reusablePane` with the multirepo folder before splitting. The group open gets it through `OpenWorktreeInHerdrUseCase`. Keeping the rule in the launcher keeps the three entry points consistent.

## Risks / Trade-offs

- [The reused shell has the environment of the first open; if the repository's `env` declaration or a provider changed since, its variables are stale] → Ports stay reserved per worktree, so the common case doesn't change; the user can close the pane and open again to get a fresh one with the current environment. Documented in the guide.
- [The user `cd`'d elsewhere or exported variables in that shell] → a different directory fails condition 3 and splits; exported variables are the user's own doing and are kept, as in any shell they type into.
- [Race: the user starts something in the pane between the check and the typed command] → the window is a few milliseconds of a single open; the same race exists today with the agent focus check. Accepted.
- [An extra `herdr` call on every reopen without an agent] → only after the cheap conditions pass, and only on this path, never while polling.
- [Herdr changes `process-info`'s shape] → parsing failures return `null` and fall back to splitting; `CliHerdrClientTest` pins the shape observed in 0.9.5.
