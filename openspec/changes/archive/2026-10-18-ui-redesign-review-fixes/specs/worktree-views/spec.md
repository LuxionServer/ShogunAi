# Spec Delta

## ADDED Requirements

### Requirement: Selection scrolls into view without jumping
Selecting a worktree SHALL scroll the list only when its row isn't fully visible, and the row SHALL end up fully visible below the sticky group header. A row that is already fully visible SHALL NOT move the list.

#### Scenario: Move the selection with the arrows
- **WHEN** the user moves the selection with the arrows between rows that are fully visible
- **THEN** the list doesn't scroll

#### Scenario: Select a worktree outside the visible area
- **WHEN** a worktree outside the visible area is selected, for example from the palette
- **THEN** the list scrolls until its row is fully visible and not covered by its group's header

### Requirement: Row context keeps the project in cross-project views
In views spanning several projects, a worktree row SHALL show its project's name and SHALL truncate the origin and the other context before the project's name.

#### Scenario: Narrow row in All worktrees
- **WHEN** a row in "All worktrees" doesn't fit its context and badges
- **THEN** the project's name is still shown in full or truncated last
