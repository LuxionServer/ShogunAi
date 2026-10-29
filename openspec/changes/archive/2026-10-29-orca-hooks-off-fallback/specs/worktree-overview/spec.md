## ADDED Requirements

### Requirement: Show the state Claude Code's session registry reports
When a Claude process runs in a worktree and neither Herdr, Orca nor Claude Code's hooks give it a state, the agent SHALL take the state of the worktree's live sessions in Claude Code's session registry, with source `claude-code`: `busy` as working, `waiting` as blocked, and `idle` as done once the session has run a turn or as idle before its first one. With several sessions, the most urgent state SHALL win. An unknown status SHALL give no state.

#### Scenario: Orca lost track of the agent
- **WHEN** Claude runs in a worktree, Orca reports no agent there, no hook record exists, and its session in the registry is `waiting`
- **THEN** the worktree's row and detail show Claude as blocked, and the worktree is under "Needs you"

#### Scenario: Turn finished
- **WHEN** the worktree's only session is `idle` and its status changed well after it started
- **THEN** Claude is shown as done

#### Scenario: Fresh session
- **WHEN** the worktree's only session is `idle` and its status hasn't changed since it started
- **THEN** Claude is shown as idle

#### Scenario: Another source reports it
- **WHEN** Orca reports Claude as done and the registry says `busy`
- **THEN** Claude is shown as done, with Orca as the source

#### Scenario: Hook record exists
- **WHEN** Claude Code's hooks recorded the session as working and the registry says `idle`
- **THEN** Claude is shown as working

#### Scenario: Several sessions
- **WHEN** a worktree has two live sessions, one `busy` and one `waiting`
- **THEN** Claude is shown as blocked

#### Scenario: Session of another worktree
- **WHEN** the only live session in the registry belongs to a repository nested in the worktree's folder
- **THEN** the worktree's Claude gets no state from it
