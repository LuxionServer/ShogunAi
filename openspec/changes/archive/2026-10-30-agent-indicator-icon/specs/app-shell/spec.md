# Spec Delta

## MODIFIED Requirements

### Requirement: Activity in the sidebar's projects
Each project in the sidebar SHALL show its number of active worktrees, and the agent icon in the info color, next to the count, when an agent is running in any of its worktrees, with the running agents in its tooltip and accessible name. Projects with no active worktree SHALL show no count. A multirepo's folder SHALL show the sum of its projects' counts and the agent icon when any of them has it.

#### Scenario: Project with an agent
- **WHEN** `api` has two active worktrees and an agent running in one of them
- **THEN** its sidebar entry shows 2 and the agent icon in the info color

#### Scenario: Multirepo folder
- **WHEN** `api` and `mobile` belong to `product` and have two and one active worktrees, with an agent in `api`
- **THEN** `product` shows 3 and the agent icon in the info color
