# Design

## Context

See proposal.md for the motivation. Today:

- `OpenWorktreeInHerdrUseCase` calls `herdr worktree open … --focus`. When Herdr answers `already_open: true` it returns `HerdrOpening.FOCUSED` without looking at the workspace's panes. Otherwise it resolves the worktree's environment, splits the root pane with `--env` variables (closing the root pane) when there are any, and types `agent.resolvedCommand()` with `herdr pane run`.
- `OpenGroupInHerdrUseCase` runs that use case once per member; `OpenMultirepoAgentInHerdrUseCase` looks for a plain workspace (no `worktree`) with a pane in the multirepo folder and focuses it, or creates a workspace and types the agent command into its root pane.
- `HerdrOpening` is an enum `{ OPENED, FOCUSED }`. The CLI prints `opened … with <agent>` / `focused … (already open)`; `openJson` prints `herdr` and `agent` (`null` when focused); `groupOpenJson` prints `result`; `multirepoAgentOpenJson` prints `opened: Boolean`.
- In the desktop app, `ProjectWorktrees.openInHerdr` already focuses the pane of an agent the agent poll knows about (`focusAgent`) and otherwise calls the use case. `WorktreeSuccess.OpenedInHerdr(path, focused)` and `MultirepoNotice.AgentOpened(focused)` carry a boolean.
- Herdr 0.9.5's `herdr pane list [--workspace <id>]` returns, per pane, `agent_status` (`unknown`, `idle`, `working`, `blocked`, `done`) and, only when an agent is detected, `agent` (e.g. `claude`). `CliHerdrClient.listPanes()` ignores both and has no workspace filter.

## Goals / Non-Goals

**Goals:**
- One place that decides "focus a live agent, or start one", shared by the worktree, group and multirepo-agent opens.
- Every typed agent command is followed by a bounded check that Herdr sees the agent.
- The result carries enough (outcome, pane id, agent name) for the CLI, JSON and the desktop app.

**Non-Goals:**
- Reusing a free shell pane of the workspace, or `herdr agent start --kind` (only knows Herdr's agent kinds and can't pass environment variables).
- Restarting or killing a hung agent, or closing the pane after a failed start.
- Making the time limit configurable by the user.
- Detecting agents Herdr can't recognise by other means.

## Decisions

### Pane model and listing

`HerdrPane` gains `agent: String?` and `agentStatus: String` (raw Herdr value, `unknown` when the field is missing) plus a computed `hasLiveAgent` (`agentStatus != "unknown"`). `HerdrClient.listPanes(workspaceId: String? = null)` adds `--workspace <id>` when given. Keeping the raw status (rather than mapping to `AgentState?`) avoids losing the difference between "no agent" and a status ShogunAi doesn't model; `done` and `idle` count as live because the agent process is still there.

Alternative: use `herdr agent list` filtered by workspace. Rejected: it lists agents only, so it can't tell which panes belong to the workspace, and the multirepo case already reads panes.

### Result type

`HerdrOpening` becomes `data class HerdrOpening(val outcome: HerdrOutcome, val paneId: String, val agent: String?)` with `enum class HerdrOutcome { OPENED, STARTED_AGENT, FOCUSED }`. `agent` is the started agent's display name, or Herdr's `agent` of the focused pane. Each consumer switches on `outcome`; the JSON value is `opened`, `started-agent`, `focused`.

Alternative: add `STARTED_AGENT` to the enum and keep the pane elsewhere. Rejected: every caller needs the pane, and the desktop success/notice types would otherwise need a second channel.

### Shared launch step

A small internal collaborator (e.g. `HerdrAgentLauncher`, in the use-case package) owns:

1. `focusLiveAgent(workspaceId)`: `listPanes(workspaceId)`, pick the first pane with a live agent (the focused one first if Herdr marks it), `focusAgent(pane)` and return `FOCUSED`; `null` when there's none.
2. `launch(paneId, command)`: `runInPane`, then poll `listPanes(workspaceId)` every 500 ms until that pane `hasLiveAgent` or 10 seconds pass; on timeout throw `WorktreeError.HerdrAgentNotStarted(paneId, command)`.

The time limit and poll interval are constructor parameters with those defaults so tests drive them with `runTest`'s virtual time; production code uses `delay` and `withTimeoutOrNull`, no `@OptIn`.

`OpenWorktreeInHerdrUseCase`:
- `already_open` → `focusLiveAgent` or else split a new pane from `rootPaneId` with the resolved environment (always split, even with no variables, and never close existing panes) → `launch` → `STARTED_AGENT`.
- Fresh → as today (split + close root when there are variables, root pane otherwise) → `launch` → `OPENED`.

`OpenMultirepoAgentInHerdrUseCase`: the found plain workspace → `focusLiveAgent` or else split the found pane in the multirepo folder with no variables → `launch` → `STARTED_AGENT`; otherwise create + `launch` the root pane → `OPENED`. `OpenGroupInHerdrUseCase` needs no logic of its own; its `GroupOpening.result` now carries the richer `HerdrOpening`.

Alternative for an agentless workspace: type into an existing idle shell pane. Rejected: that shell may lack the worktree's environment or be in another directory, and a user may have work in it.

### Error

`WorktreeError.HerdrAgentNotStarted(val paneId: String, val command: String)` with a message like "the agent `claude` didn't start in Herdr pane w1:p2 within 10 seconds; the pane is left open". The CLI already maps `WorktreeError` thrown from `getOrThrow()` to exit code 1 on standard error; groups report it per member through `failureReason`. The desktop app adds English/Spanish `ErrorTexts` and `TechnicalDetails` (pane, command) for it.

### CLI and JSON

- Text: `opened <path> in Herdr with <agent> (pane <id>)`, `started <agent> in <path>'s open Herdr workspace (pane <id>)`, `focused <path> in Herdr (already open, pane <id>)`; same shape for the multirepo folder and each group member.
- `openJson`: `path`, `branch`, `herdr`, `pane_id`, `agent` (now non-null when focused if Herdr names the agent).
- `groupOpenJson`: `result` gains `started-agent`; each entry adds `pane_id` and `agent` (`null` on failure).
- `multirepoAgentOpenJson`: keeps `opened` (true unless focused) for compatibility and adds `herdr` and `pane_id`.

### Desktop app

- `WorktreeSuccess.OpenedInHerdr(path, outcome)` replaces the `focused` boolean; `WorktreeScreen.successMessage` picks `openedInHerdr`, a new `agentStartedInHerdr(name)` or `focusedInHerdr`. The `focusAgent` shortcut in `ProjectWorktrees.openInHerdr` maps to `FOCUSED` with the known pane.
- `MultirepoNotice.AgentOpened(outcome)` replaces the boolean; new `multirepoAgentStarted` string.
- New strings in `Strings`, `EnglishStrings`, `SpanishStrings`; `StringsTest` keeps both translations complete.

### Test fake

`FakeHerdrClient` gains a `startsAgents: Boolean = true` switch: after `runInPane(pane, command)` it reports that pane with an agent (`agent` = the command's last word, status `idle`) in `listPanes`; with `false` the pane stays `unknown`, to test the timeout. `listPanes(workspaceId)` filters the configured panes plus the ones it created.

## Risks / Trade-offs

- [Herdr doesn't recognise the agent (a custom command, or a wrapper such as `headroom wrap claude` if Herdr doesn't see the child)] → the open fails although the agent runs. Accepted: failing loudly is better than a false success; the error says the pane is left open. Check `headroom wrap claude` by hand during implementation and note the result in the change.
- [10 seconds is too short on a loaded machine] → the error leaves the pane running, so the user loses nothing; rerunning `shogun open` then focuses it. The constant is easy to raise later.
- [Every open now costs extra `herdr pane list` calls] → at most one per 500 ms for up to 10 s, only during an open.
- [JSON consumers that relied on `agent: null` meaning "focused"] → `herdr` remains the field that tells the outcome; noted as BREAKING in the proposal.

## Migration Plan

No data migration. Ships with the next release; rollback is reverting the change.
