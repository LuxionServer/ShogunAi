## ADDED Requirements

### Requirement: Find a live Claude that Orca doesn't report
When none of a workspace's Orca terminals reports an agent, opening it in Orca SHALL look in Claude Code's session registry for a live session in the workspace's folder whose process runs in one of those terminals. It SHALL focus that terminal and report `focused` with the session's name, and SHALL NOT start another agent. It SHALL prefer the session with the expected name, then the most recently started one.

#### Scenario: Orca lost track of the agent
- **WHEN** the user opens in Orca a worktree whose terminal runs a live Claude session named `main-repo-task-123`, but Orca reports no agent in any of its terminals
- **THEN** that terminal is focused, the result is `focused` with `session_name` `main-repo-task-123`, and no terminal is created

#### Scenario: Session outside Orca
- **WHEN** a live Claude session runs in the worktree's folder but not in any of the worktree's Orca terminals, for example in another terminal app
- **THEN** a new Orca terminal runs the agent command as in a fresh open

#### Scenario: Terminal unknown
- **WHEN** the terminal a live session runs in can't be determined, for example because the system doesn't expose another process's environment
- **THEN** a new Orca terminal runs the agent command as in a fresh open

#### Scenario: Multirepo agent
- **WHEN** the user opens the multirepo agent in Orca and its folder's terminal runs a live Claude session that Orca doesn't report
- **THEN** that terminal is focused and no other agent is started

## MODIFIED Requirements

### Requirement: Verify the agent started in Orca
After starting the agent command in a new Orca terminal, ShogunAi SHALL wait up to 10 seconds for Orca to report an agent in that terminal, or for Claude Code's session registry to show a live session in the workspace's folder that runs in that terminal or has the name given to the new session. When the time runs out, the open SHALL fail with an error naming the terminal and the command, and the terminal SHALL stay open.

#### Scenario: Agent detected
- **WHEN** a new terminal runs `claude` and Orca reports an agent in it two seconds later
- **THEN** the open succeeds

#### Scenario: Agent only in the registry
- **WHEN** a new terminal runs `claude -n main-repo-task-123`, Orca reports no agent in it, and a live session named `main-repo-task-123` appears in the worktree's folder
- **THEN** the open succeeds

#### Scenario: Agent never detected
- **WHEN** neither Orca nor Claude Code's session registry shows the agent 10 seconds after the new terminal started
- **THEN** the open fails with an error naming the terminal and the command, and the terminal stays open

#### Scenario: Agent only focused
- **WHEN** the open only focuses a live agent
- **THEN** nothing is started and nothing is waited for
