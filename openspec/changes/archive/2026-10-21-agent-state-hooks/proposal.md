# Proposal

## Why

Outside Herdr, ShogunAi only knows that an agent process runs in a worktree, not whether it is working or waiting for the user, so a Claude session waiting on a permission prompt sits in "Agent working" instead of "Needs you". Claude Code's hooks can report that, but today every ShogunAi hook is configured by copying JSON from the docs into a settings file by hand, and the user wants as little to configure as possible.

## What Changes

- A new `shogun hook agent-state` command, called by Claude Code's `UserPromptSubmit`, `Notification`, `Stop` and `SessionEnd` hooks, records each session's state (working, blocked, done) in the worktree's Git administrative folder, outside the working tree.
- Agent detection reads those records: a Claude process in a worktree takes the state of its live session, and a record whose session is gone is ignored. Herdr's state still wins when Herdr reports the agent.
- A blocked or done record whose session's transcript was written after it reads as working, so a granted permission doesn't leave the agent shown as blocked.
- `shogun hooks install` and `shogun hooks uninstall` add or remove every ShogunAi hook in Claude Code's user settings (or the repository's with `--project`), merging with the hooks already there, idempotent, and showing what changes before writing. `shogun hooks status` says whether they are installed and current.
- The desktop app's Settings shows the hooks' status with "Install" and "Update" actions that do the same, and the worktree view suggests installing them once when it sees a Claude agent without a known state.
- `shogun status` and its JSON report the agent's state whatever its source: JSON gains `agents[].state` and `agents[].stateSource` (`herdr` or `claude-code`); `herdrState` stays.

## Capabilities

### New Capabilities
- `claude-code-hooks`: installing, updating, checking and removing ShogunAi's Claude Code hooks, from the command line and the desktop app.

### Modified Capabilities
- `command-line-tool`: the `hook agent-state` command, the `hooks` command, and the agent state in `shogun status`.
- `worktree-overview`: agents carry the state reported by Claude Code's hooks.

## Impact

- `core`: a port and a file-backed store for agent session records, `DetectRunningAgentsUseCase` (merging records), `HerdrAgentState` generalized to an agent state with its source, a use case that merges hooks into Claude Code's `settings.json`; tests.
- `cli`: `hook agent-state`, `hooks install|uninstall|status`, `status` output and JSON; help.
- `desktopApp`: Settings section for the hooks, the one-time suggestion, the row and detail using the general agent state; strings in English and Spanish.
- `docs/`: `docs/agents.md` (install with one command instead of copying JSON), `docs/guide/cli.md` and the architecture pages, in both languages.
- Depends on `worktree-states-redesign`, which defines how agent states decide a worktree's state; it should be merged first.
