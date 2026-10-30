## MODIFIED Requirements

### Requirement: Active is the main view
The sidebar's views section SHALL list "Active" first, above "All worktrees", with the number of worktrees waiting for the user. When no view was saved from a previous session, the app SHALL open on "Active".

#### Scenario: First launch with projects
- **WHEN** the app starts with saved projects and no saved view
- **THEN** it opens on "Active"

#### Scenario: Count
- **WHEN** four worktrees are active across all projects and one of them is "Waiting for you"
- **THEN** the sidebar shows "Active" with 1

#### Scenario: Nothing waiting
- **WHEN** worktrees are active but none is "Waiting for you"
- **THEN** the sidebar shows "Active" without a count

### Requirement: Activity in the sidebar's projects
Each project in the sidebar SHALL show its number of worktrees waiting for the user, and the agent icon in the info color, next to the count, when an agent is running in any of its worktrees, with the running agents in its tooltip and accessible name. Projects with no worktree waiting for the user SHALL show no count. A multirepo's folder SHALL show the sum of its projects' counts and the agent icon when any of them has it.

#### Scenario: Project with an agent
- **WHEN** `api` has two worktrees waiting for the user and an agent running in another one
- **THEN** its sidebar entry shows 2 and the agent icon in the info color

#### Scenario: Unfinished work isn't counted
- **WHEN** `api`'s only active worktree is "Unfinished"
- **THEN** its sidebar entry shows no count

#### Scenario: Multirepo folder
- **WHEN** `api` and `mobile` belong to `product` and have two and one worktrees waiting for the user, with an agent in `api`
- **THEN** `product` shows 3 and the agent icon in the info color
