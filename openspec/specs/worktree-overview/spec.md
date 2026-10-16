# worktree-overview Specification

## Purpose
Gives the user a full picture of a project's worktrees, whoever created them: where each one comes from, which AI agents are working in it, how much disk it uses and whether it has been abandoned.

## Requirements

### Requirement: Show where each worktree comes from
The system SHALL classify every non-main worktree by origin and show it in its row: created by Claude Code when its path is inside a `.claude/worktrees/` folder, created by ShogunAi when it is inside the project's worktrees folder, and "other location" otherwise. The main worktree has no origin label.

#### Scenario: Claude Code worktree
- **WHEN** a worktree's path is inside `<repository>/.claude/worktrees/`
- **THEN** its row shows it was created by Claude Code, even if that folder is also inside the project's worktrees folder

#### Scenario: ShogunAi worktree
- **WHEN** a worktree's path is inside the project's worktrees folder and not inside `.claude/worktrees/`
- **THEN** its row shows it was created by ShogunAi

#### Scenario: Other location
- **WHEN** a worktree's path is neither inside `.claude/worktrees/` nor inside the project's worktrees folder
- **THEN** its row shows it is in another location

### Requirement: Show the AI agents running in each worktree
The system SHALL detect the AI agent processes whose working directory is inside each worktree and show their names in its row. A process belongs to the worktree with the longest path that contains its working directory, so a process in a nested worktree isn't attributed to the main one. Recognized agents are Claude, Codex, Gemini, Cursor agent, OpenCode, Aider, Copilot and the project's own agent command. When processes can't be inspected on the platform, no agent information is shown.

#### Scenario: Agent running in a worktree
- **WHEN** a `claude` process has its working directory inside a worktree
- **THEN** that worktree's row shows Claude as running

#### Scenario: Agent in a nested worktree
- **WHEN** a `claude` process has its working directory inside `<repository>/.claude/worktrees/task`, which is a worktree nested in the main worktree's folder
- **THEN** Claude is shown on the nested worktree and not on the main one

#### Scenario: Non-agent processes
- **WHEN** only shells, editors or build tools have their working directory inside a worktree
- **THEN** its row shows no running agent

#### Scenario: Process inspection unavailable
- **WHEN** listing processes fails, for example because `lsof` isn't installed
- **THEN** no row shows agent information and the rest of the list works normally

### Requirement: Show each worktree's disk usage
The system SHALL compute each worktree's disk usage in the background, without blocking the list or its statuses, and show it in human-readable units in its row. Folders of other worktrees nested inside a worktree SHALL NOT be counted in it. The list header SHALL show the total of the sizes computed so far.

#### Scenario: Size shown
- **WHEN** a worktree's files add up to 1,288,490,189 bytes
- **THEN** its row shows "1.2 GB"

#### Scenario: Nested worktrees excluded
- **WHEN** the main worktree's folder contains another worktree under `.claude/worktrees/`
- **THEN** the main worktree's size doesn't include the nested worktree's files

#### Scenario: Missing folder
- **WHEN** a worktree's folder no longer exists
- **THEN** no size is shown for it

### Requirement: Flag inactive worktrees
The system SHALL flag a non-main worktree as inactive when it has no uncommitted changes, no AI agent running in it, and its last commit is older than 14 days.

#### Scenario: Abandoned worktree
- **WHEN** a clean worktree with no running agent had its last commit 20 days ago
- **THEN** its row shows it as inactive

#### Scenario: Recent work
- **WHEN** a clean worktree with no running agent had its last commit 3 days ago
- **THEN** it isn't flagged as inactive

#### Scenario: Agent running or pending changes
- **WHEN** a worktree whose last commit is 20 days old has uncommitted changes or an agent running in it
- **THEN** it isn't flagged as inactive

### Requirement: Reload the overview with the list
The system SHALL detect running agents and compute disk usage again every time the worktree list is reloaded.

#### Scenario: Reload
- **WHEN** the user reloads the worktree list or the window regains focus
- **THEN** agents and sizes are read again for the listed worktrees

### Requirement: Show each worktree's reserved ports
The system SHALL show in each worktree's row the ports already reserved for it, each with the variable or key it belongs to. Showing them SHALL NOT reserve ports; a worktree without reservations SHALL show nothing.

#### Scenario: Ports reserved
- **WHEN** a worktree has `PORT` reserved as 20001 and the key `api` as 20002
- **THEN** its row shows `PORT 20001` and `api 20002`

#### Scenario: Nothing reserved yet
- **WHEN** a worktree created by another tool has never had its environment resolved
- **THEN** its row shows no ports and none are reserved by opening the screen
