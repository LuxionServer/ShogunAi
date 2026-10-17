# Spec Delta

## ADDED Requirements

### Requirement: Sidebar views section
The sidebar SHALL show a views section above the projects, with an "All worktrees" entry showing the total number of worktrees. Project entries SHALL open the same worktree view filtered to that project.

#### Scenario: All worktrees entry
- **WHEN** the saved projects have 7 worktrees in total
- **THEN** the sidebar's views section shows "All worktrees" with 7

#### Scenario: Project entry is a filter
- **WHEN** the user selects `api` in the sidebar
- **THEN** the content area shows the worktree view with the project filter set to `api`
