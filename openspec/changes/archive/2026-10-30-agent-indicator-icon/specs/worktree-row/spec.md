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

### Requirement: Execution cell in a narrow list
When the list is too narrow to show the execution cell's labels, the cell SHALL collapse to the agent's icon and the device's icon, with the same colors by state as the full cell, and the labels in their tooltips. Every row of the view SHALL collapse at the same time.

#### Scenario: Narrow list
- **WHEN** the window is narrowed below the width that fits the execution labels
- **THEN** every row shows only the agent's icon and the device icon, Claude's icon still in the warning color, and hovering it says Claude needs you
