## MODIFIED Requirements

### Requirement: Show where each worktree comes from
The system SHALL classify every non-main worktree by origin and show it next to the folder name in its row and in its detail: created by Claude Code when its path is inside a `.claude/worktrees/` folder, created by Herdr when it is inside Herdr's worktrees folder, created by Orca when Orca created it, created by ShogunAi when it is inside the project's worktrees folder, and "other location" otherwise. The main worktree has no origin label.

#### Scenario: Claude Code worktree
- **WHEN** a worktree's path is inside `<repository>/.claude/worktrees/`
- **THEN** its row and detail show it was created by Claude Code, even if that folder is also inside the project's worktrees folder

#### Scenario: Herdr worktree
- **WHEN** a worktree's path is inside Herdr's worktrees folder, the `directory` of the `[worktrees]` table in Herdr's `config.toml` or `~/.herdr/worktrees` when Herdr's configuration doesn't set it
- **THEN** its row and detail show it was created by Herdr

#### Scenario: Herdr configuration unreadable
- **WHEN** Herdr's configuration file is missing or can't be parsed
- **THEN** `~/.herdr/worktrees` is used as Herdr's worktrees folder and the classification of other worktrees is unaffected

#### Scenario: Orca worktree
- **WHEN** Orca lists a worktree among the ones it created, not among the external worktrees it shows
- **THEN** its row and detail show it was created by Orca

#### Scenario: Orca not running
- **WHEN** Orca can't be asked and a worktree's path is inside the folder where Orca last reported creating worktrees, or `~/orca/workspaces` when it never reported one
- **THEN** its row and detail show it was created by Orca

#### Scenario: ShogunAi worktree opened in Orca
- **WHEN** a worktree inside the project's worktrees folder, created by `shogun new`, is shown in Orca because ShogunAi opened it there
- **THEN** its row and detail still show it was created by ShogunAi

#### Scenario: ShogunAi worktree
- **WHEN** a worktree's path is inside the project's worktrees folder and not inside `.claude/worktrees/` or Herdr's worktrees folder, and Orca didn't create it
- **THEN** its row and detail show it was created by ShogunAi

#### Scenario: Other location
- **WHEN** a worktree's path is neither inside `.claude/worktrees/`, Herdr's worktrees folder nor the project's worktrees folder, and Orca didn't create it
- **THEN** its row and detail show it is in another location

### Requirement: Show the state Claude Code's hooks report
When a Claude process runs in a worktree and a live session of that worktree has a recorded state, the agent SHALL carry that state. A record SHALL be live while its session's Claude process runs. A blocked or done record whose session's transcript was written after it SHALL read as working. When Herdr or Orca reports the same agent, the state they report SHALL be used.

#### Scenario: Blocked outside Herdr
- **WHEN** Claude runs in a worktree outside Herdr and Orca and its session was recorded as blocked
- **THEN** the worktree's row and detail show Claude as blocked, and the worktree is under "Needs you"

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

## ADDED Requirements

### Requirement: Show the state Orca reports for each agent
When Orca is running, the system SHALL read the agents Orca reports for each worktree, matched to ShogunAi's worktrees by path. Each agent SHALL carry Orca's state: working as working, waiting as blocked, done as done. An agent Orca reports SHALL count as running even when the process list doesn't show it. When Orca isn't installed or running, agents SHALL be shown without an Orca state.

#### Scenario: Waiting agent
- **WHEN** Orca reports a `claude` agent as waiting in a worktree
- **THEN** the worktree's row and detail show Claude as blocked, and the worktree is under "Needs you"

#### Scenario: Several agents in the worktree
- **WHEN** Orca reports two `claude` agents in the same worktree, one working and one waiting
- **THEN** the worktree shows Claude as blocked

#### Scenario: Agent only Orca sees
- **WHEN** the process list can't be read and Orca reports an agent in a worktree
- **THEN** that worktree shows the agent with its state, and cleanup keeps the worktree

#### Scenario: Orca not running
- **WHEN** Orca is installed but its app isn't running
- **THEN** agents found in the process list are shown without an Orca state, and nothing reports an error
