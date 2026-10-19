## MODIFIED Requirements

### Requirement: Show the AI agents running in each worktree
The system SHALL detect the AI agent processes whose working directory is inside each worktree. A worktree's row SHALL show an "agent running" indicator, and its detail SHALL name each running agent. A process belongs to the worktree with the longest path that contains its working directory, so a process in a nested worktree isn't attributed to the main one. Recognized agents are Claude, Codex, Gemini, Cursor agent, OpenCode, Aider, Copilot and the project's own agent command. When processes can't be inspected on the platform, no agent information is shown, except the agents Herdr reports.

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

## ADDED Requirements

### Requirement: Show the state Herdr reports for each agent
When Herdr is running, the system SHALL read the agents Herdr tracks and attribute each to the worktree with the longest path containing the agent's current directory. Each agent shown in a row or detail SHALL carry the state Herdr reports: working, blocked, done or idle. An agent Herdr reports SHALL count as running even when the process list doesn't show it. When Herdr isn't installed or running, agents SHALL be shown as before, without a state.

#### Scenario: Blocked agent
- **WHEN** Herdr reports a `claude` agent as blocked with its current directory inside a worktree
- **THEN** the worktree's row and detail show Claude as blocked

#### Scenario: Several panes of the same agent
- **WHEN** Herdr reports two `claude` agents in the same worktree, one working and one blocked
- **THEN** the worktree shows Claude as blocked

#### Scenario: Herdr not running
- **WHEN** Herdr is installed but its server isn't running
- **THEN** agents found in the process list are shown without a state and nothing reports an error

#### Scenario: Agent only Herdr sees
- **WHEN** the process list can't be read and Herdr reports an agent in a worktree
- **THEN** that worktree shows the agent with its state, and cleanup keeps the worktree
