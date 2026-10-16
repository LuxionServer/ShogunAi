# Spec Delta

## MODIFIED Requirements

### Requirement: Show where each worktree comes from
The system SHALL classify every non-main worktree by origin and show it in its row: created by Claude Code when its path is inside a `.claude/worktrees/` folder, created by Herdr when it is inside Herdr's worktrees folder, created by ShogunAi when it is inside the project's worktrees folder, and "other location" otherwise. The main worktree has no origin label.

#### Scenario: Claude Code worktree
- **WHEN** a worktree's path is inside `<repository>/.claude/worktrees/`
- **THEN** its row shows it was created by Claude Code, even if that folder is also inside the project's worktrees folder

#### Scenario: Herdr worktree
- **WHEN** a worktree's path is inside Herdr's worktrees folder, the `directory` of the `[worktrees]` table in Herdr's `config.toml` or `~/.herdr/worktrees` when Herdr's configuration doesn't set it
- **THEN** its row shows it was created by Herdr

#### Scenario: Herdr configuration unreadable
- **WHEN** Herdr's configuration file is missing or can't be parsed
- **THEN** `~/.herdr/worktrees` is used as Herdr's worktrees folder and the classification of other worktrees is unaffected

#### Scenario: ShogunAi worktree
- **WHEN** a worktree's path is inside the project's worktrees folder and not inside `.claude/worktrees/` or Herdr's worktrees folder
- **THEN** its row shows it was created by ShogunAi

#### Scenario: Other location
- **WHEN** a worktree's path is neither inside `.claude/worktrees/`, Herdr's worktrees folder nor the project's worktrees folder
- **THEN** its row shows it is in another location
