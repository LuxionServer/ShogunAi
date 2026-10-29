## MODIFIED Requirements

### Requirement: Report each agent's state in status
`shogun status` SHALL show each agent's state whatever reports it, Herdr, Orca or Claude Code's hooks, and `shogun status --json` SHALL add `state` (`working`, `blocked`, `done`, `idle` or `null`) and `stateSource` (`herdr`, `orca`, `claude-code` or `null`) to each agent, keeping `herdrState` as before.

#### Scenario: Agent state from Claude Code's hooks
- **WHEN** Claude is blocked in a worktree, as recorded by its `Notification` hook, and neither Herdr nor Orca reports it
- **THEN** the line shows `claude(blocked)` and the JSON agent has `state` `blocked`, `stateSource` `claude-code` and `herdrState` `null`

#### Scenario: Agent state from Herdr
- **WHEN** Herdr reports Claude as working
- **THEN** the JSON agent has `state` `working`, `stateSource` `herdr` and `herdrState` `working`

#### Scenario: Agent state from Orca
- **WHEN** Orca reports Claude as waiting
- **THEN** the line shows `claude(blocked)` and the JSON agent has `state` `blocked`, `stateSource` `orca` and `herdrState` `null`

#### Scenario: Origin of an Orca worktree
- **WHEN** a worktree was created by Orca
- **THEN** its JSON entry has `origin` `orca`
