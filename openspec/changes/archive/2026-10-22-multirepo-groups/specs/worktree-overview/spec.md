## MODIFIED Requirements

### Requirement: Show the AI agents running in each worktree
The system SHALL detect the AI agent processes whose working directory is inside each worktree. A worktree's row SHALL show an "agent running" indicator, and its detail SHALL name each running agent. A process belongs to the worktree with the longest path that contains its working directory, so a process in a nested worktree isn't attributed to the main one, and a process whose working directory is inside another Git repository nested in the worktree's folder isn't attributed to the worktree. Recognized agents are Claude, Codex, Gemini, Cursor agent, OpenCode, Aider, Copilot and every agent command of the project. When processes can't be inspected on the platform, no agent information is shown, except the agents Herdr reports.

#### Scenario: Agent running in a worktree
- **WHEN** a `claude` process has its working directory inside a worktree
- **THEN** that worktree's row shows the agent running indicator and its detail shows Claude as running

#### Scenario: Agent in a nested worktree
- **WHEN** a `claude` process has its working directory inside `<repository>/.claude/worktrees/task`, which is a worktree nested in the main worktree's folder
- **THEN** Claude is shown on the nested worktree and not on the main one

#### Scenario: Agent in a nested repository
- **WHEN** a `claude` process has its working directory inside `~/projects/product/product-docs`, a separate Git repository inside the folder of the repository `~/projects/product`
- **THEN** no agent is shown on `~/projects/product`'s main worktree

#### Scenario: Non-agent processes
- **WHEN** only shells, editors or build tools have their working directory inside a worktree
- **THEN** its row and detail show no running agent

#### Scenario: Process inspection unavailable
- **WHEN** listing processes fails, for example because `lsof` isn't installed
- **THEN** no row or detail shows agent information and the rest of the list works normally

#### Scenario: Second agent of the project
- **WHEN** a project's second agent runs the command `antigravity` and an `antigravity` process works inside one of its worktrees
- **THEN** that worktree shows the agent as running
