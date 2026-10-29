# Proposal

## Why

The control tower tells that an agent in Orca is blocked, but not what it's blocked on, what it was asked or what it last said, so the user has to switch to Orca to find out. And a worktree's detail only lists the ports ShogunAi reserves, not the ones its servers actually listen on. Orca already knows both; this is the roadmap's "What only Orca offers" step for the agent's details and the observed ports.

An exploration against Orca 1.4.243 settled what its CLI really exposes:

- **Agent details come with the states ShogunAi already reads.** Each agent of `orca worktree ps --json` carries `toolName` and `toolInput`, `prompt` and `lastAssistantMessage`. While Claude waits for permission to run a tool, its state is `waiting` with `toolName: "Bash"` and `toolInput` set to the command. Once it's done, `toolName` still names the last tool it used, so a tool only means "waiting on" while the state is `waiting`. A new prompt clears `lastAssistantMessage` until the next answer. `orca worktree show` and `orca terminal show` add nothing about the agent.
- **Observed ports come from one call.** `orca ports --all --json` lists every listener on the machine; those Orca ties to a workspace have `kind: "workspace"` and an `owner.path`, plus `processName` and, when a dev server printed it in an Orca terminal, `advertisedUrl`. The same port can appear twice (IPv4 and IPv6). `orca ports --worktree path:<abs>` gives one workspace's but fails with `selector_not_found` for a path Orca doesn't know, and a folder workspace needs `--all` anyway. Orca attributes ports by the listening process's working directory, so it also sees the ports of worktrees it only shows, such as ShogunAi's own.

## What Changes

- The worktree detail shows, for each agent Orca reports, the tool it waits on with its input while it's blocked, the prompt it was given and its last message, shortened to a few lines each. Nothing changes for agents Orca doesn't report.
- The detail's environment section shows the ports Orca sees the worktree listen on, each with its process and its address when Orca saw one, next to the ports ShogunAi reserves. Reserved ports Orca sees listening are marked as such.
- `shogun status --json` adds `activity` to each agent (`waitingOn` with `tool` and `input`, `prompt` and `lastMessage`, or `null`) and `listeningPorts` to each worktree (the ports Orca sees, or `null` when Orca can't be asked). `shogun status` adds `listening=<ports>` to a worktree's line when Orca sees it listen.
- The ports are read once per check for all worktrees, with the same rules as Orca's agents: nothing runs without Orca, Orca is never started, and a failure waits 30 seconds before the next try.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `orca-integration`: reading the ports Orca observes follows the same once-per-check, never-start, back-off rules as reading its agents.
- `worktree-overview`: each agent Orca reports carries the tool it waits on, its prompt and its last message.
- `worktree-detail`: the agent details and the ports Orca sees in the detail.
- `command-line-tool`: `activity` per agent and `listeningPorts` per worktree in `shogun status --json`, and `listening=` in its lines.

## Impact

- `core`: `OrcaClient.ports()` and its parsing in `CliOrcaClient`, agent details in `OrcaAgent` and `RunningAgent`, `OrcaWorktreeReader` reading ports, `DetectRunningAgentsUseCase` carrying the details, `FakeOrcaClient`, new fixtures `worktree-ps-tool.json` and `ports-all.json` recorded from Orca 1.4.243.
- `cli`: `StatusJson` and the status line.
- `desktopApp`: the detail's agents and environment sections, the view model's refresh, English and Spanish strings.
- Docs: the Orca page in `docs/architecture/` (both languages) and the roadmap.
