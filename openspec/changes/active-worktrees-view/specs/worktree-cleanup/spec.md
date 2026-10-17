# Spec Delta

## ADDED Requirements

### Requirement: Clean up from the merged group
When a worktree view is grouped by state, the "Merged" group's header SHALL offer "Clean up", covering the cleanup candidates of the projects in the view, with the same confirmation as the view's "Clean up".

#### Scenario: Clean up merged worktrees
- **WHEN** "All worktrees" is grouped by state and the user activates "Clean up" on the "Merged" group
- **THEN** the cleanup confirmation lists the candidates grouped by project, as the view's "Clean up" does
