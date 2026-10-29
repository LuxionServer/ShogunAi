# Spec Delta

## Purpose

Defines one row and column contract shared by every list of the desktop app (worktrees, Machine resources, multirepo repositories), so rows look, size and behave the same everywhere and a new column has a known place.

## ADDED Requirements

### Requirement: Columns with fixed places
A list row SHALL lay its content out in declared columns. Every column SHALL have either a fixed width, which it keeps when it has nothing to show, or a share of the remaining width that truncates with an ellipsis. The same column SHALL start at the same horizontal position in every row of a list.

#### Scenario: Empty cell keeps its width
- **WHEN** one row of a list has a value in a fixed column and the next row has none
- **THEN** the following columns start at the same horizontal positions in both rows

### Requirement: Compact behavior per column
Each column SHALL declare how it behaves when the list is narrower than the compact breakpoint: keep its width, shrink to a smaller width, or be hidden. Every row of a list SHALL switch to compact at the same time.

#### Scenario: Narrow list
- **WHEN** a list is narrower than the compact breakpoint
- **THEN** every row shows its shrinking columns at their compact width and its hidable columns not at all, and the remaining columns still line up

### Requirement: Row heights
A row with two lines of content SHALL be at least 48 dp tall, and a row with one line at least 32 dp tall.

#### Scenario: One-line list
- **WHEN** the Gradle daemons view lists a daemon
- **THEN** its row is 32 dp tall, not taller than a single line needs plus its padding

### Requirement: Hover, selection and keyboard
A selectable row SHALL show the hand pointer and a hover highlight, draw its selected state on the accent container, take keyboard focus with the visible accent focus ring, and be activated with Enter or Space as with a click. A row that isn't selectable SHALL take no focus and react to no click.

#### Scenario: Activate with the keyboard
- **WHEN** a selectable row has keyboard focus and the user presses Enter
- **THEN** the row is selected as if it had been clicked

#### Scenario: Read-only row
- **WHEN** a Machine list draws a daemon row
- **THEN** the row itself can't be focused or clicked, and only its own controls can

### Requirement: Action slot at the end of the row
When a row has an action slot at its end, the slot SHALL have a fixed width so it never moves the columns. Actions in the slot SHALL appear while the row is hovered or focused, and SHALL stay visible while one of them is confirming in place.

#### Scenario: Hover shows the action
- **WHEN** the user hovers a row with an action slot
- **THEN** the action appears at the row's end and no column moves

### Requirement: Column labels aligned with the rows
A list with column labels SHALL draw them in a header row that uses the same columns as its rows, in the section label style (uppercase eyebrow), so each label sits above its column.

#### Scenario: Header over rows
- **WHEN** the Android emulators view lists an emulator
- **THEN** the device, worktree and state labels start at the same positions as the device, worktree and state of the row below
