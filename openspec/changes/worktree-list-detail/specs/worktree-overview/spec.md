# Spec Delta

## MODIFIED Requirements

### Requirement: Show where each worktree comes from
The system SHALL classify every non-main worktree by origin and show it next to the folder name in its row and in its detail: created by Claude Code when its path is inside a `.claude/worktrees/` folder, created by ShogunAi when it is inside the project's worktrees folder, and "other location" otherwise. The main worktree has no origin label.

#### Scenario: Claude Code worktree
- **WHEN** a worktree's path is inside `<repository>/.claude/worktrees/`
- **THEN** its row and detail show it was created by Claude Code, even if that folder is also inside the project's worktrees folder

#### Scenario: ShogunAi worktree
- **WHEN** a worktree's path is inside the project's worktrees folder and not inside `.claude/worktrees/`
- **THEN** its row and detail show it was created by ShogunAi

#### Scenario: Other location
- **WHEN** a worktree's path is neither inside `.claude/worktrees/` nor inside the project's worktrees folder
- **THEN** its row and detail show it is in another location

### Requirement: Show the AI agents running in each worktree
The system SHALL detect the AI agent processes whose working directory is inside each worktree. A worktree's row SHALL show an "agent running" indicator, and its detail SHALL name each running agent. A process belongs to the worktree with the longest path that contains its working directory, so a process in a nested worktree isn't attributed to the main one. Recognized agents are Claude, Codex, Gemini, Cursor agent, OpenCode, Aider, Copilot and the project's own agent command. When processes can't be inspected on the platform, no agent information is shown.

#### Scenario: Agent running in a worktree
- **WHEN** a `claude` process has its working directory inside a worktree
- **THEN** that worktree's row shows the agent running indicator and its detail shows Claude as running

#### Scenario: Agent in a nested worktree
- **WHEN** a `claude` process has its working directory inside `<repository>/.claude/worktrees/task`, which is a worktree nested in the main worktree's folder
- **THEN** Claude is shown on the nested worktree and not on the main one

#### Scenario: Non-agent processes
- **WHEN** only shells, editors or build tools have their working directory inside a worktree
- **THEN** its row and detail show no running agent

#### Scenario: Process inspection unavailable
- **WHEN** listing processes fails, for example because `lsof` isn't installed
- **THEN** no row or detail shows agent information and the rest of the list works normally

### Requirement: Show each worktree's disk usage
The system SHALL compute each worktree's disk usage in the background, without blocking the list or its statuses, and show it in human-readable units in its detail. Folders of other worktrees nested inside a worktree SHALL NOT be counted in it. The project summary SHALL show the total of the sizes computed so far.

#### Scenario: Size shown
- **WHEN** a worktree's files add up to 1,288,490,189 bytes
- **THEN** its detail shows "1.2 GB"

#### Scenario: Nested worktrees excluded
- **WHEN** the main worktree's folder contains another worktree under `.claude/worktrees/`
- **THEN** the main worktree's size doesn't include the nested worktree's files

#### Scenario: Missing folder
- **WHEN** a worktree's folder no longer exists
- **THEN** no size is shown for it

### Requirement: Flag inactive worktrees
The system SHALL flag a non-main worktree as inactive, in its row and its detail, when it has no uncommitted changes, no AI agent running in it, and its last commit is older than 14 days.

#### Scenario: Abandoned worktree
- **WHEN** a clean worktree with no running agent had its last commit 20 days ago
- **THEN** its row and detail show it as inactive

#### Scenario: Recent work
- **WHEN** a clean worktree with no running agent had its last commit 3 days ago
- **THEN** it isn't flagged as inactive

#### Scenario: Agent running or pending changes
- **WHEN** a worktree whose last commit is 20 days old has uncommitted changes or an agent running in it
- **THEN** it isn't flagged as inactive

### Requirement: Show each worktree's reserved ports
The system SHALL show in each worktree's detail the ports already reserved for it, each with the variable or key it belongs to. Showing them SHALL NOT reserve ports; a worktree without reservations SHALL show nothing.

#### Scenario: Ports reserved
- **WHEN** a worktree has `PORT` reserved as 20001 and the key `api` as 20002
- **THEN** its detail shows `PORT 20001` and `api 20002`

#### Scenario: Nothing reserved yet
- **WHEN** a worktree created by another tool has never had its environment resolved
- **THEN** its detail shows no ports and none are reserved by opening the view

### Requirement: Show and control each worktree's emulator
For a repository that declares the emulator provider, each worktree's detail SHALL show the state of its emulator (stopped, booting or running), with a Start action when stopped and a Stop action otherwise, and refresh the state with the view. Its row SHALL show a device indicator only while the emulator is booting or running. Showing the detail SHALL NOT reserve a port or start anything.

#### Scenario: Running emulator
- **WHEN** a worktree's `emulator-5556` is online and booted
- **THEN** its row shows the device indicator and its detail shows the emulator as running with a Stop action

#### Scenario: Start from the row
- **WHEN** the user activates Start in a worktree's detail for a stopped emulator
- **THEN** the emulator starts, and the detail and the row show it booting, then running

#### Scenario: Port not reserved yet
- **WHEN** a worktree has never had its environment resolved
- **THEN** its detail shows a Start action, and starting reserves the port first

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the emulator provider
- **THEN** neither rows nor details show emulator information

### Requirement: Show and control each worktree's iOS simulator
For a repository that declares the iOS simulator provider, on macOS, each worktree's detail SHALL show its simulator's state (shut down, booting or booted), with Start when shut down and Stop otherwise, refreshed with the view. Its row SHALL show a device indicator only while the simulator is booting or booted. Showing the detail SHALL NOT create a simulator.

#### Scenario: Booted simulator
- **WHEN** a worktree's simulator is booted
- **THEN** its row shows the device indicator and its detail shows it as booted with a Stop action

#### Scenario: No simulator yet
- **WHEN** a worktree created by another tool never had its environment resolved
- **THEN** its detail shows a Start action, and starting creates and boots its simulator
