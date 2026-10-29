## MODIFIED Requirements

### Requirement: Execution cell
The execution cell SHALL show the worktree's agent on its first line and its emulator or simulator on its second line. The agent SHALL be shown with the agent indicator: a dot colored by its state, its name and its state in the shared vocabulary (working, needs you, done, idle or running). With several agents, the most urgent one SHALL be shown with a "+N" count, and the tooltip SHALL list every agent with its state, the source of the state and when it was read. The device line SHALL show the device only while it is booting or running, colored by its state.

#### Scenario: Agent and emulator
- **WHEN** Claude is blocked in a worktree whose emulator is running
- **THEN** the first line shows a warning dot with "Claude · needs you" and the second line shows the emulator as running

#### Scenario: Several agents
- **WHEN** a worktree has a working Codex and a blocked Claude
- **THEN** the cell shows "Claude · needs you" with "+1", and its tooltip lists both agents with their states

#### Scenario: Nothing running
- **WHEN** a worktree has no agent and no running device
- **THEN** the execution cell is empty and keeps its width

### Requirement: Execution cell in a narrow list
When the list is too narrow to show the execution cell's labels, the cell SHALL collapse to the agent's dot and the device's icon, with the same colors by state as the full cell, and the labels in their tooltips. Every row of the view SHALL collapse at the same time.

#### Scenario: Narrow list
- **WHEN** the window is narrowed below the width that fits the execution labels
- **THEN** every row shows only the agent's dot and the device icon, Claude's dot still in the warning color, and hovering it says Claude needs you

## ADDED Requirements

### Requirement: Monospace only for data in the row
In a worktree row, the branch, the folder, the repository alias, the Git counts and the device's identifiers SHALL use the monospaced data font, and the agent's state, the relative activity date, the marks and the project's name SHALL use the interface font.

#### Scenario: Row fonts
- **WHEN** a row shows `feature/TASK-123`, "Claude · working", "commit 3 h ago" and the project `main-repo`
- **THEN** the branch is in the data font, and the agent's state, the date and the project's name are in the interface font

### Requirement: Rows of every worktree view share the list row
The worktree rows of the Active, All, project and multirepo views, by state and by change, SHALL be drawn with the shared list row, so their hover, selection, keyboard activation and compact behavior are the same.

#### Scenario: Keyboard in the multirepo view by change
- **WHEN** a worktree row of a multirepo view by change has keyboard focus and the user presses Enter
- **THEN** the worktree is selected and its detail is shown
