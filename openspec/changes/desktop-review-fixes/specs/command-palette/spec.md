# Spec Delta

## ADDED Requirements

### Requirement: The palette shows its secondary action
When the highlighted result is a worktree, the palette SHALL show that Cmd/Ctrl+Enter copies its launch command.

#### Scenario: Worktree highlighted
- **WHEN** the user highlights a worktree in the palette
- **THEN** a hint shows that Cmd/Ctrl+Enter copies its launch command

#### Scenario: Project highlighted
- **WHEN** the highlighted result is a project
- **THEN** the hint isn't shown
