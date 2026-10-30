## MODIFIED Requirements

### Requirement: Group header with a combined state
Each change group's header SHALL show as its title the group's branch without the prefix up to its last `/` (`TASK-12`), with the full branch as detail, its repositories' aliases and a combined state: "Merged" only when every worktree of the group is merged, and otherwise the most urgent state among its worktrees in the order of the worktree states.

#### Scenario: One worktree needs the user
- **WHEN** `app`'s worktree on `feature/TASK-1` is "Agent working" and `api`'s is "Waiting for you"
- **THEN** the header shows `TASK-1`, the detail `feature/TASK-1` and "Waiting for you"

#### Scenario: Agent working before unfinished work
- **WHEN** `app`'s worktree on `feature/TASK-1` is "Unfinished" and `api`'s is "Agent working"
- **THEN** the header shows "Agent working"

#### Scenario: Partly merged
- **WHEN** `app`'s worktree of the group is "Merged" and `api`'s is "Stopped"
- **THEN** the header shows "Stopped", not "Merged"

#### Scenario: All merged
- **WHEN** every worktree of the group is "Merged"
- **THEN** the header shows "Merged"
