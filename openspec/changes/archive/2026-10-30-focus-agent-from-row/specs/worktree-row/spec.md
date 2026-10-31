# Spec Delta

## MODIFIED Requirements

### Requirement: Execution cell
The execution cell SHALL show the worktree's agent on its first line and its emulator or simulator on its second line. The agent SHALL be shown with the agent indicator: the agent icon colored by its state, its name and its state in the shared vocabulary (working, needs you, done, idle or running). With several agents, the most urgent one SHALL be shown with a "+N" count, and the tooltip SHALL list every agent with its state, the source of the state and when it was read. The device line SHALL show the device only while it is booting or running, colored by its state.

#### Scenario: Agent and emulator
- **WHEN** Claude is blocked in a worktree whose emulator is running
- **THEN** the first line shows a warning-colored agent icon with "Claude · needs you" and the second line shows the emulator as running

#### Scenario: Several agents
- **WHEN** a worktree has a working Codex and a blocked Claude
- **THEN** the cell shows "Claude · needs you" with "+1", and its tooltip lists both agents with their states

#### Scenario: Nothing running
- **WHEN** a worktree has no agent and no running device
- **THEN** the execution cell is empty and keeps its width

#### Scenario: Focus the agent from the row
- **WHEN** Claude needs the user in a Herdr pane and the user clicks the row's agent indicator, in the full or the compact cell
- **THEN** the row is selected and Claude's pane is focused in Herdr, as "Open in Herdr" does for that worktree

#### Scenario: Focus an agent in Orca
- **WHEN** Codex works in a terminal Orca reports and the user clicks the row's agent indicator
- **THEN** the row is selected and the worktree's terminal is focused in Orca, as "Open in Orca" does for that worktree

## ADDED Requirements

### Requirement: Enter focuses the selected worktree's agent
In the worktree list, pressing Enter without modifiers SHALL focus the selected worktree's agent in its runner, as clicking its agent indicator does. When the selected worktree has no agent in a known runner, Enter SHALL keep its usual behavior on the focused row.

#### Scenario: Enter on a worktree whose agent needs you
- **WHEN** the selected worktree has Claude needing the user in Herdr and the user presses Enter in the list
- **THEN** Claude's pane is focused in Herdr

#### Scenario: Enter without a known runner
- **WHEN** the selected worktree has no agent, or only agents whose runner is unknown, and the user presses Enter on a focused row
- **THEN** nothing is focused in a runner and the focused row is selected as with a click

#### Scenario: Focus fails
- **WHEN** the user focuses an agent from the row and the runner reports an error
- **THEN** the error is shown on the worktree as for "Open in Herdr" or "Open in Orca"
