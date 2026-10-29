# Spec Delta

## ADDED Requirements

### Requirement: Read worktree statuses concurrently in status
`shogun status` SHALL read the statuses of the repository's worktrees, the running agents and the recorded base branches concurrently, with a bounded number of reads at a time, and SHALL print the same lines and JSON, in the same order, as reading them one after another.

#### Scenario: Many worktrees
- **WHEN** a repository has 11 worktrees and `shogun status` runs
- **THEN** several worktree statuses are read at the same time, and the lines appear in the order the worktrees are listed

#### Scenario: One worktree fails
- **WHEN** one worktree's status can't be read while the others can
- **THEN** its line reports the failure as before, and the other lines are unaffected
