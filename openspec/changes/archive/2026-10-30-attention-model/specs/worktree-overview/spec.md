## MODIFIED Requirements

### Requirement: Flag inactive worktrees
The system SHALL flag a non-main worktree as inactive, in its row and its detail, when it has commits of its own, isn't merged, has no AI agent running in it, and its activity date is older than the inactivity threshold, whether or not it has uncommitted changes or unpushed commits. The threshold SHALL default to 3 days. A worktree without commits of its own SHALL NOT be flagged as inactive.

#### Scenario: Abandoned worktree
- **WHEN** a clean worktree with no running agent had its last commit of its own 5 days ago, with the default threshold
- **THEN** its row and detail show it as inactive

#### Scenario: Recent work
- **WHEN** a clean worktree with no running agent had its last commit of its own 2 days ago, with the default threshold
- **THEN** it isn't flagged as inactive

#### Scenario: Agent running or pending changes
- **WHEN** a worktree whose last commit of its own is 20 days old has an agent running in it
- **THEN** it isn't flagged as inactive

#### Scenario: Abandoned with pending changes
- **WHEN** a worktree with no running agent whose last commit of its own is 20 days old has 2 uncommitted changes
- **THEN** it is flagged as inactive, and its row and detail still show the 2 changes

#### Scenario: Old base commit in a new worktree
- **WHEN** a worktree was created today from a branch whose last commit is 30 days old, and has no commits of its own
- **THEN** it isn't flagged as inactive and is shown as new

### Requirement: Show the state Claude Code's hooks report
When a Claude process runs in a worktree and a live session of that worktree has a recorded state, the agent SHALL carry that state. A record SHALL be live while its session's Claude process runs. A blocked or done record whose session's transcript was written after it SHALL read as working. When Herdr or Orca reports the same agent, the state they report SHALL be used.

#### Scenario: Blocked outside Herdr
- **WHEN** Claude runs in a worktree outside Herdr and Orca and its session was recorded as blocked
- **THEN** the worktree's row and detail show Claude as blocked, and the worktree is under "Waiting for you"

#### Scenario: Permission granted
- **WHEN** a session recorded as blocked writes to its transcript afterwards
- **THEN** Claude is shown as working

#### Scenario: Stale record
- **WHEN** a worktree has a record of a session whose Claude process no longer runs
- **THEN** the record is ignored, and no agent is shown unless a process, Herdr or Orca reports one

#### Scenario: Herdr also reports it
- **WHEN** Herdr reports Claude as working and the hook record says done
- **THEN** Claude is shown as working

#### Scenario: Orca also reports it
- **WHEN** Orca reports Claude as waiting and the hook record says working
- **THEN** Claude is shown as blocked

### Requirement: Show the state Orca reports for each agent
When Orca is running, the system SHALL read the agents Orca reports for each worktree, matched to ShogunAi's worktrees by path. Each agent SHALL carry Orca's state: working as working, waiting as blocked, done as done. An agent Orca reports SHALL count as running even when the process list doesn't show it. When Orca isn't installed or running, agents SHALL be shown without an Orca state.

#### Scenario: Waiting agent
- **WHEN** Orca reports a `claude` agent as waiting in a worktree
- **THEN** the worktree's row and detail show Claude as blocked, and the worktree is under "Waiting for you"

#### Scenario: Several agents in the worktree
- **WHEN** Orca reports two `claude` agents in the same worktree, one working and one waiting
- **THEN** the worktree shows Claude as blocked

#### Scenario: Agent only Orca sees
- **WHEN** the process list can't be read and Orca reports an agent in a worktree
- **THEN** that worktree shows the agent with its state, and cleanup keeps the worktree

#### Scenario: Orca not running
- **WHEN** Orca is installed but its app isn't running
- **THEN** agents found in the process list are shown without an Orca state, and nothing reports an error

### Requirement: Show the state Claude Code's session registry reports
When a Claude process runs in a worktree and neither Herdr, Orca nor Claude Code's hooks give it a state, the agent SHALL take the state of the worktree's live sessions in Claude Code's session registry, with source `claude-code`: `busy` as working, `waiting` as blocked, and `idle` as done once the session has run a turn or as idle before its first one. With several sessions, the most urgent state SHALL win. An unknown status SHALL give no state.

#### Scenario: Orca lost track of the agent
- **WHEN** Claude runs in a worktree, Orca reports no agent there, no hook record exists, and its session in the registry is `waiting`
- **THEN** the worktree's row and detail show Claude as blocked, and the worktree is under "Waiting for you"

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
