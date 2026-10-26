# Spec Delta

## MODIFIED Requirements

### Requirement: Execution cell
The execution cell SHALL show the worktree's agent on its first line and its emulator or simulator on its second line. The agent SHALL be shown with its name and its state (working, blocked, done, idle or unknown), its icon colored by that state. With several agents, the most urgent one SHALL be shown with a "+N" count, and the tooltip SHALL list every agent with its state. The device line SHALL show the device only while it is booting or running, colored by its state. The second line SHALL also show, before the device, that the worktree holds the desktop turn or waits for it with its place in line, and after the device a running headless app; when more than one applies, it SHALL show the first with a "+N" count, and its tooltip SHALL list them all.

#### Scenario: Agent and emulator
- **WHEN** Claude is blocked in a worktree whose emulator is running
- **THEN** the first line shows Claude as blocked in the warning color and the second line shows the emulator as running

#### Scenario: Several agents
- **WHEN** a worktree has a working Codex and a blocked Claude
- **THEN** the cell shows Claude as blocked with "+1", and its tooltip lists both agents with their states

#### Scenario: Nothing running
- **WHEN** a worktree has no agent and no running device
- **THEN** the execution cell is empty and keeps its width

#### Scenario: Holding the desktop
- **WHEN** a worktree holds the desktop turn and has no running device
- **THEN** the second line shows that it has the desktop, and its tooltip says until when

#### Scenario: Waiting for the desktop with a running app
- **WHEN** a worktree is second in line for the desktop and its headless app is running
- **THEN** the second line shows that it waits for the desktop, second in line, with "+1", and its tooltip lists the wait and the running app

#### Scenario: Headless app only
- **WHEN** a worktree's headless app is running and nothing else applies
- **THEN** the second line shows the app as running in the background
