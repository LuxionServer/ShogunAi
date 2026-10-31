# Proposal

## Why

The UX review of the v2 interface found that the app doesn't answer "where is each agent?" at a glance: the runner an agent lives in is only hinted at in the tooltip ("State from Herdr"), and the row's agent indicator can't be clicked, so reaching the agent that needs you takes opening the context menu and choosing "Open in Herdr" or "Open in Orca". The runners already know how to focus an agent's pane or terminal; the row only has to offer it.

## What Changes

- Clicking the agent indicator of a worktree row focuses that agent's pane in its runner: its Herdr pane when Herdr reports it, or its terminal in Orca when Orca reports it, reusing the paths "Open in Herdr" and "Open in Orca" already take for an open worktree.
- Pressing Enter in the worktree list does the same for the selected worktree. When the selected worktree has no agent in a known runner, Enter keeps selecting the focused row as today.
- The agent being focused is the most urgent one that runs in a known runner, so with "Claude · needs you" and "+1" the click goes to Claude.
- The indicator's tooltip says where each agent runs and, on the row, that clicking focuses it, e.g. "Claude · needs you · in Herdr — click to focus". The indicator shows the hand pointer on hover and is a button for accessibility.
- An agent whose runner isn't known (only its process was found, or only Claude Code's hooks report it outside Herdr) leaves the indicator as it is today: not clickable, no "in …" and no hint.
- The focus action is exposed by the worktree's holder, so other places (the inspector's Agent section, owned by another change) can offer it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-indicator`: the tooltip says which runner each agent runs in, and the indicator can be clickable to focus the agent.
- `worktree-row`: the execution cell's agent indicator focuses the agent on click, and Enter in the list focuses the selected worktree's agent.

## Impact

- Desktop app: `AgentIndicator` (click handling and semantics), `AgentIndicatorModel.kt` (focus target and tooltip lines), `WorktreeRow`/`WorktreeRowModel` (wiring the click), `WorktreeListPane` (Enter), `ProjectWorktrees` (a `focusAgent` action reusing the Herdr and Orca focus paths), English and Spanish strings.
- No change to `core`, the CLI or the runners' clients.
- Other changes running in parallel touch `WorktreeRow` and the inspector; this one keeps its edits there small.
