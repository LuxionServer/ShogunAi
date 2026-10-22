## MODIFIED Requirements

### Requirement: Sidebar lists the projects
The sidebar SHALL list every saved project by name, alphabetically, with an "Add project" action. Projects that belong to a multirepo SHALL be listed inside that multirepo's folder instead of at the top level. The selected entry SHALL be visually marked.

#### Scenario: Projects listed
- **WHEN** the app has the projects `api`, `mobile` and `main-repo` saved
- **THEN** the sidebar lists `api`, `main-repo` and `mobile`, in that order, followed by "Add project"

#### Scenario: Projects of a multirepo
- **WHEN** `api` and `mobile` belong to the multirepo `product`
- **THEN** the sidebar lists `main-repo` and the folder `product` with `api` and `mobile` inside

### Requirement: Activity in the sidebar's projects
Each project in the sidebar SHALL show its number of active worktrees, and an indicator in the info color when an agent is running in any of its worktrees. Projects with no active worktree SHALL show no count. A multirepo's folder SHALL show the sum of its projects' counts and the indicator when any of them has it.

#### Scenario: Project with an agent
- **WHEN** `api` has two active worktrees and an agent running in one of them
- **THEN** its sidebar entry shows 2 and the agent indicator

#### Scenario: Multirepo folder
- **WHEN** `api` and `mobile` belong to `product` and have two and one active worktrees, with an agent in `api`
- **THEN** `product` shows 3 and the agent indicator
