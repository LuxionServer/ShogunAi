# Design

## Context

- `RunningAgent` already carries what tells its runner apart: `herdrPaneId` when Herdr reports the pane, and `stateSource == ORCA` when Orca reports the state (`DetectRunningAgentsUseCase`). An agent reported only by Claude Code's hooks, or only found as a process, has neither.
- `ProjectWorktrees.openInHerdr` already focuses a pane (`useCases.herdr.focusAgent(pane)`) when the worktree has an agent in Herdr, preferring one that waits for the user. `ProjectWorktrees.openInOrca` calls `useCases.orca.open`, which focuses the worktree's terminal when it is already open (`OrcaOutcome.FOCUSED`). Both record errors and successes on the worktree and guard against double opens.
- `AgentIndicator` is a plain composable inside `WithTooltip`; `agentIndicator(...)` builds its model (label, `+N`, tooltip lines). The row, the inspector's Agent section, the multirepo headers and the sidebar all use it.
- `WorktreeListPane` handles the arrow keys in an `onPreviewKeyEvent` on the list, moving the board's selection; a focused `DataRow` selects itself on Enter.

## Goals / Non-Goals

**Goals:**
- One pure function decides the agent to focus and its runner, used by the click, Enter and the tooltip, so they never disagree.
- The focus goes through the existing runner paths, with their error reporting and success notices.

**Non-Goals:**
- Making the indicator clickable in the inspector, the multirepo headers or the sidebar. The inspector's Agent section is being reworked by the calmer-list-and-inspector change, which can wire `ProjectWorktrees.focusAgent` into it.
- Focusing a specific Orca terminal per agent: Orca focuses the worktree's terminal.
- Telling where an agent reported only by Claude Code's hooks runs.

## Decisions

### A focus target computed from the agents

`agentFocusTarget(agents): AgentFocusTarget?` sorts the agents by urgency as the indicator does and returns the first with a known runner, with its name, its `RunnerId` and its Herdr pane id when it has one. Herdr wins over Orca when an agent has both a pane and an Orca state, since the pane is what can be focused exactly.

Alternative: reuse `agentToFocus()` from `openInHerdr`, which prefers an agent waiting for the user. It only knows Herdr and its order differs from the indicator's, so the click could focus an agent other than the one shown. `openInHerdr` keeps its own choice; the row uses the target.

### `ProjectWorktrees.focusAgent(worktree): Boolean`

It computes the target from the holder's agents and returns `false` when there is none. For Herdr it calls `useCases.herdr.focusAgent(paneId)` under the same `openingInHerdr` guard and success/error handling `openInHerdr` uses, extracted into a shared private function. For Orca it calls `openInOrca(worktree)`, whose open already focuses the existing terminal. Returning a boolean lets Enter fall back to the row's usual behavior.

Alternative: call `openInHerdr` for Herdr too. It would focus its own preferred agent, not the target, as explained above.

### Tooltip lines

`agentIndicator` gains a `clickToFocus: Boolean = false` parameter and a `focusTarget` field in `AgentIndicatorModel`. Every agent with a known runner gets " · in Herdr"/" · in Orca" after its label, everywhere the indicator is shown, since that answers "where is each agent?". Only when `clickToFocus` is true and there is a target does the target's line also get " — click to focus". The row passes `true`; the inspector, multirepo headers and sidebar keep the default until they offer the action.

### Clickable indicator

`AgentIndicator` gains an optional `onClick`. When it is set, the indicator uses the shared `interactive` modifier (hand pointer, hover, focus ring) and its semantics become a button with the same content description. The row passes `onClick` only when the model has a focus target; the click selects the row and calls `holder.focusAgent(worktree)`. The compact cell, which only shows the icon, is clickable the same way.

### Enter in the list

`WorktreeListPane`'s `onPreviewKeyEvent` handles Enter and NumPadEnter without modifiers: it calls `board.focusSelectedAgent()`, which finds the selected worktree's holder and calls `focusAgent`. The event is consumed only when that returns `true`; otherwise it reaches the focused row, which selects itself as before. The selection is the list's cursor (the arrows move it without moving keyboard focus), so Enter acts on it rather than on the focused row.

## Risks / Trade-offs

- [The keyboard focus is on a row other than the selected one, e.g. after Tab, and the selection has a focusable agent] → Enter focuses the selected worktree's agent, not the focused row. Clicking and the arrows keep focus and selection together, so this needs a deliberate Tab; accepted.
- [Conflicts with the parallel changes on `WorktreeRow`, `WorktreeListPane` and the roadmap] → edits there are kept to wiring a callback and one key; the logic lives in `AgentIndicatorModel.kt` and `ProjectWorktrees`.
- [An Orca agent in a worktree whose terminal was closed] → `openInOrca` opens a new terminal with the worktree's agent instead of focusing, as the context menu does today.
