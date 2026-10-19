## MODIFIED Requirements

### Requirement: Group by state
"Active" SHALL group its worktrees by state, in this order: "Agent working" (an agent is running and none is blocked or done), "Needs you" (an agent is blocked or done, or there are uncommitted changes or commits not pushed to its upstream and no agent), and "In progress" (any other active worktree). When "All worktrees" is grouped by state, it SHALL also show "Merged", "Inactive" and "Folder missing" groups after those, and a trailing "Other" group for the clean main worktrees and the worktrees whose status isn't read yet, so that no worktree disappears. Empty groups SHALL be hidden.

#### Scenario: Active groups
- **WHEN** one active worktree has an agent, one has uncommitted changes and one is clean and unmerged
- **THEN** "Active" shows them under "Agent working", "Needs you" and "In progress", in that order

#### Scenario: All worktrees by state
- **WHEN** "All worktrees" is grouped by state and two worktrees are merged
- **THEN** a "Merged" group lists them after the active groups

#### Scenario: Agent waiting for the user
- **WHEN** Herdr reports a worktree's agent as blocked
- **THEN** the worktree is listed under "Needs you" instead of "Agent working"
