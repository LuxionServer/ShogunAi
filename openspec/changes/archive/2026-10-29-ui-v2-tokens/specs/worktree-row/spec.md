# Spec Delta

## MODIFIED Requirements

### Requirement: State badge
The state badge SHALL be a circle in the color of the worktree's state with an icon inside that is distinct for each state, so the state can be told without seeing color. The "New" state SHALL be an outlined circle in the neutral status color with its icon in that color, not a filled circle in a brand or primary color. Hovering it SHALL show the state's name, and assistive technologies SHALL read the state's name.

#### Scenario: Needs you
- **WHEN** a worktree needs the user
- **THEN** its badge shows the "needs you" icon in the warning color, and its tooltip and accessible name say "Needs you"

#### Scenario: Same color, different icon
- **WHEN** two states share a similar color
- **THEN** their badges still differ by their icon

#### Scenario: New worktree badge
- **WHEN** a worktree has no commits of its own and isn't merged
- **THEN** its badge is an outlined neutral circle with the "new" icon, and its tooltip and accessible name say "New"

## ADDED Requirements

### Requirement: Row identifiers and selection colors
A row's repository alias and group tag SHALL be drawn in the secondary text color, not in the primary or brand color, and the selected row SHALL be drawn on the accent container.

#### Scenario: Multirepo row
- **WHEN** a row of a multirepo view shows its repository alias and its group tag
- **THEN** both use the secondary text color

#### Scenario: Selected row
- **WHEN** the user selects a row
- **THEN** its background is the accent container
