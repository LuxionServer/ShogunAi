# Spec Delta

## MODIFIED Requirements

### Requirement: Agent section
The detail's Agent section SHALL show every agent running in the worktree with the agent indicator, and say so when no agent runs.

#### Scenario: Agent needing the user
- **WHEN** Claude, reported by Herdr as blocked, runs in the selected worktree
- **THEN** the Agent section shows a warning-colored agent icon with "Claude · needs you", and its tooltip says the state comes from Herdr

#### Scenario: No agent
- **WHEN** no agent runs in the selected worktree
- **THEN** the Agent section says no agent is running
