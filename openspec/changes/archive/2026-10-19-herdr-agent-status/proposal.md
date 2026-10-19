# Proposal

## Why

ShogunAi already tells which worktrees have an agent running, from the process list, but not what the agent is doing. Herdr knows: it tracks each agent pane as working, blocked (waiting for the user, e.g. a permission prompt), done or idle, and exposes it through `herdr agent list`. Now that worktrees open in Herdr from the app, showing that state in each row turns the control tower into the place to see which agent needs you. This is the Herdr item of the roadmap's "Launching the agent and tools directly"; the terminal and IDE launching is `direct-launch`.

## What Changes

- When Herdr is running, each worktree's row and detail show the state Herdr reports for the agents working in it: working, blocked, done or idle.
- A worktree whose agent is blocked or done moves to the "Needs you" group, since it waits for the user; one whose agent is working stays in "Agent working".
- Agents Herdr reports in a worktree count as running agents even when the process list can't see them, so cleanup's agent guard also covers them.
- From a worktree with a Herdr agent, "Open in Herdr" focuses that agent's pane.

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-overview`: running agents carry the state Herdr reports.
- `worktree-views`: "Needs you" includes worktrees whose agent is blocked or done.
- `herdr-integration`: reading agent states from Herdr, and focusing the agent's pane.

## Impact

- `core`: `HerdrClient.listAgents()` over `herdr agent list`, `RunningAgent` gains an optional Herdr state, agent detection merges both sources, `worktreeActivity` uses the state; `HerdrClient.focusAgent()`.
- `desktopApp`: row chips, detail agents section, state grouping hints, strings.
- `cli`: `shogun status` shows the Herdr state next to the agent name, and `status --json` adds it as a new field.
- Docs: `docs/agents.md`, `docs/guide/desktop-app.md` and Spanish versions.
