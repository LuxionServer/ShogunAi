## MODIFIED Requirements

### Requirement: Detail of the selected worktree
The detail pane SHALL show the selected worktree's branch (or that it is detached), its origin and its full path as selectable text with a copy action, then its sections in the inspector's fixed order: Status, Agent, Environment, Resources and Provisioning. Each section SHALL show its data as properties, draw placeholder rows until its data is first read, and not be drawn when it doesn't apply to the worktree.

#### Scenario: Worktree with an agent and ports
- **WHEN** the selected worktree has Claude running and `PORT` reserved as 20001
- **THEN** the Agent section shows Claude with its state and the Environment section shows `PORT` with `20001`

#### Scenario: Copy the path
- **WHEN** the user activates the copy action next to the path
- **THEN** the worktree's full path is copied to the clipboard

#### Scenario: Main worktree
- **WHEN** the main worktree is selected
- **THEN** the detail shows no Provisioning section

## ADDED Requirements

### Requirement: Agent section
The detail's Agent section SHALL show every agent running in the worktree with the agent indicator, and say so when no agent runs.

#### Scenario: Agent needing the user
- **WHEN** Claude, reported by Herdr as blocked, runs in the selected worktree
- **THEN** the Agent section shows a warning dot with "Claude · needs you", and its tooltip says the state comes from Herdr

#### Scenario: No agent
- **WHEN** no agent runs in the selected worktree
- **THEN** the Agent section says no agent is running

### Requirement: Status section summary
The detail's Status section SHALL show the uncommitted changes, the divergence from the upstream and from the main branch, whether the branch is merged and the last commit or creation date as properties, and summarize the changes and commits ahead in its label line.

#### Scenario: Changes and commits ahead
- **WHEN** the selected worktree has 4 uncommitted changes and is 2 commits ahead of its upstream
- **THEN** the Status section's label line reads "4 changes · ↑2" and its properties show the upstream and main branch divergences and the last commit's date
