## ADDED Requirements

### Requirement: Rule for overlapping changes
The `worktrees` topic SHALL tell agents to run `shogun overlap` before opening a pull request and, when it lists files, to tell the person which files and worktrees they share. The rule SHALL NOT be in the session-start context.

#### Scenario: Guide
- **WHEN** the user runs `shogun guide worktrees`
- **THEN** the output says to run `shogun overlap` before opening a pull request and to tell the person about the files it lists

#### Scenario: Session context
- **WHEN** a Claude Code session starts in a worktree
- **THEN** the context doesn't include the rule about `shogun overlap`
