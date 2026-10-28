# Spec Delta

## MODIFIED Requirements

### Requirement: Rules for creating and keeping worktrees
The `worktrees` topic SHALL tell agents that every change goes in its own worktree created with `shogun new`, with `--fix`, `--from <ref>`, `--fetch` and `--branch`; that the base is the current worktree's HEAD unless `--from` is given; to check the new worktree's `git log -1`; that the worktree where a session starts never changes branch; to give the person the new worktree's path; to consult `shogun status` before saying what worktrees exist; and to open a pull request against the branch `shogun base` prints, passing it explicitly, recording it with `shogun base --set <branch>` or asking the person when none is recorded.

#### Scenario: Branching from an epic
- **WHEN** the user runs `shogun guide worktrees`
- **THEN** the output says to use `shogun new <id> --from <ref>` to branch from something other than the current worktree's HEAD, such as an epic's branch

#### Scenario: Base of a pull request
- **WHEN** the user runs `shogun guide worktrees`
- **THEN** the output says to open a pull request against the branch `shogun base` prints, passing it with `--base`, and what to do when `shogun base` has none recorded
