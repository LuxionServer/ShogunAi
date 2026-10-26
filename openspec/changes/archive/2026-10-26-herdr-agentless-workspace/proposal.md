# Proposal

## Why

`shogun open <id> --herdr` treats any existing Herdr workspace of the worktree as "already open" and only focuses it, even when no pane in it runs an agent any more (the user quit the agent, or it crashed). The command prints `focused … (already open)` and exits with code 0, so a person or an orchestrating agent believes there's a session to talk to when there isn't. The same happens when opening a multirepo group and the multirepo agent. A fresh open has a related blind spot: it types the agent command into a pane and reports success without checking that the agent actually started.

## What Changes

- Opening a worktree whose Herdr workspace already exists focuses it only when one of its panes has a live agent (Herdr's `agent_status` other than `unknown`), and focuses that agent's pane.
- When the existing workspace has no live agent, ShogunAi starts one exactly as a fresh open does: a new pane in that workspace with the worktree's environment, running the agent's launch command. The other panes are left as they are. The outcome is reported as `started-agent`.
- After typing the agent command (fresh open or `started-agent`), ShogunAi waits up to 10 seconds for Herdr to detect an agent in that pane. If it doesn't, the open fails with a typed error that names the pane and the command, leaves the pane open, and the CLI exits with a non-zero code.
- The same rules apply to every worktree of a multirepo group (`shogun open <id> --herdr` in the multirepo folder, "Open all in Herdr") and to the multirepo agent (`shogun open --herdr` in the multirepo folder, "Open multirepo agent in Herdr").
- Text and `--json` output distinguish `opened`, `started-agent` and `focused`, and always carry the pane id of the agent and the agent's name, so an orchestrator knows which session to write to. **BREAKING (JSON)**: `shogun open --herdr --json` reports `agent` also when focused (it was `null`), and `herdr` gains the value `started-agent`.
- The desktop app reports the three outcomes with their own English and Spanish texts, and describes the new error.
- ShogunAi reads each pane's agent and `agent_status` from `herdr pane list`, and can list the panes of one workspace.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `herdr-integration`: reusing an open workspace now depends on a live agent; an agentless workspace gets a new agent pane; every launched agent is verified to start; the desktop app reports the three outcomes.
- `command-line-tool`: `shogun open --herdr` output and JSON distinguish `opened`, `started-agent` and `focused`, include `pane_id` and `agent`, and fail when the agent doesn't start.
- `multirepo-groups`: opening a group's worktrees and the multirepo agent follows the same rules and reports the same outcomes.
- `multirepo-desktop`: "Open multirepo agent in Herdr" reports `started-agent` and the start failure ("Open all in Herdr" reuses each worktree's "Open in Herdr", covered by `herdr-integration`).

## Impact

- `core`: `HerdrPane` (agent, agent status), `HerdrClient.listPanes` (optional workspace), `CliHerdrClient`, `HerdrOpening` (outcome, pane, agent), a new `WorktreeError` for an agent that doesn't start, `OpenWorktreeInHerdrUseCase`, `OpenGroupInHerdrUseCase`, `OpenMultirepoAgentInHerdrUseCase`, `FakeHerdrClient` and their tests.
- `cli`: `ShogunCli.open` / `openInMultirepo`, `openJson` in `CommandJson.kt`, `groupOpenJson` and `multirepoAgentOpenJson` in `MultirepoJson.kt`, CLI tests.
- `desktopApp`: `ProjectWorktrees.kt` (`WorktreeSuccess.OpenedInHerdr`), `WorktreeBoard.kt` and `MultirepoNotice.AgentOpened`, `WorktreeScreen.kt`, `NewChangeDialog.kt`, `Strings`/`EnglishStrings`/`SpanishStrings`, `EnglishErrors`/`SpanishErrors`/`ErrorDescription`, and their tests.
- Depends on Herdr 0.9.5's `pane list` fields (`agent`, `agent_status`) and its `--workspace` filter.
