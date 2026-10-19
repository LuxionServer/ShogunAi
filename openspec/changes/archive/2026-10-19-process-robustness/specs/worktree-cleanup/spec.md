# Spec Delta

## ADDED Requirements

### Requirement: Keep worktrees with a running agent
Cleanup SHALL never select or remove a worktree where an agent is running, from the desktop app or from `shogun clean`, and SHALL say which worktrees were kept for that reason. When processes can't be inspected on the platform, cleanup SHALL say that running agents couldn't be checked.

#### Scenario: Agent started on a fresh worktree
- **WHEN** a worktree with no commits of its own qualifies as merged and Claude is running in it
- **THEN** it isn't selected or removed, and cleanup says it was kept because an agent is running

#### Scenario: Command line
- **WHEN** the user runs `shogun clean --yes` and an agent runs in one of the qualifying worktrees
- **THEN** the other worktrees are removed and that one is listed as kept, with the agent's name
