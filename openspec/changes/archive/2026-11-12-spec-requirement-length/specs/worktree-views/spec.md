## MODIFIED Requirements

### Requirement: Each worktree has one state
Each worktree SHALL have the first state that applies: "Folder missing" when its folder is gone; "Waiting for you" when an agent is blocked or done; "Agent working" when an agent is working or runs without a known state; "Other" for an unread status or the main worktree; "Inactive"; "Unfinished" when there are uncommitted changes, commits not pushed to its upstream, or commits of its own and no upstream; then "New" with no commits of its own, "Merged", and "Stopped" otherwise.

#### Scenario: Idle agent with pending work
- **WHEN** Herdr reports a worktree's agent as idle and the worktree has 2 uncommitted changes
- **THEN** the worktree is under "Unfinished"

#### Scenario: Agent working with uncommitted changes
- **WHEN** a worktree's agent is working and the worktree has uncommitted changes
- **THEN** the worktree is under "Agent working"

#### Scenario: Blocked agent
- **WHEN** a worktree's agent is blocked
- **THEN** the worktree is under "Waiting for you", whatever its Git status

#### Scenario: Done agent
- **WHEN** a worktree's agent is done and the worktree is clean
- **THEN** the worktree is under "Waiting for you"

#### Scenario: Idle agent without pending work
- **WHEN** Herdr reports a worktree's agent as idle, the worktree is clean and its pushed branch has commits of its own
- **THEN** the worktree is under "Stopped", and its row shows the idle agent

#### Scenario: Agent without a known state
- **WHEN** a `claude` process runs in a clean worktree and no tool reports its state
- **THEN** the worktree is under "Agent working"

#### Scenario: Commits not pushed
- **WHEN** a worktree's branch is 2 commits ahead of its upstream, has no changes and no agent, and its activity date is recent
- **THEN** the worktree is under "Unfinished"

#### Scenario: Branch never pushed
- **WHEN** a worktree's branch has commits of its own, no upstream, no changes and no agent, and its activity date is recent
- **THEN** the worktree is under "Unfinished"

#### Scenario: Old unfinished work
- **WHEN** a worktree has 3 uncommitted changes, no agent, and its last commit of its own is 5 days old, with the default threshold
- **THEN** the worktree is under "Inactive", and its row still shows its 3 changes

#### Scenario: New worktree
- **WHEN** a worktree's branch has no commits the main branch lacks, isn't merged, has no changes and no agent
- **THEN** the worktree is under "New"

#### Scenario: Pushed work waiting
- **WHEN** a worktree's branch has commits of its own, all pushed to its upstream, no changes and no agent, and its activity date is recent
- **THEN** the worktree is under "Stopped"

## ADDED Requirements

### Requirement: An idle agent doesn't decide the state
An idle agent SHALL NOT decide a worktree's state: the worktree gets the state that applies to it as if it had no agent.

#### Scenario: Idle with pending work
- **WHEN** Herdr reports a worktree's agent as idle and the worktree has 2 uncommitted changes
- **THEN** the worktree is under "Unfinished"

#### Scenario: Idle without pending work
- **WHEN** Herdr reports a worktree's agent as idle, the worktree is clean and its pushed branch has commits of its own
- **THEN** the worktree is under "Stopped", and its row shows the idle agent
