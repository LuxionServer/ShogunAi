## ADDED Requirements

### Requirement: Reusable selectors are covered by automated UI tests
The desktop app SHALL have Compose UI tests, run by `./gradlew test`, for `DropdownSelector` and `SegmentedSelector`.

#### Scenario: Disabled dropdown option ignores selection
- **WHEN** a UI test performs a click semantics action on a disabled option of an open `DropdownSelector`
- **THEN** the selection callback is not invoked and the menu stays open

#### Scenario: Enabled dropdown option selects and closes
- **WHEN** a UI test clicks an enabled option of an open `DropdownSelector`
- **THEN** the selection callback receives that option and the menu closes

#### Scenario: Segmented labels are rendered in full
- **WHEN** a UI test renders a `SegmentedSelector` with long labels
- **THEN** every label's text is displayed without truncation

### Requirement: Creation form guard is covered by an automated UI test
The "Create worktree" action in "Existing branch" mode SHALL be verified by a UI test to be enabled only for an available branch.

#### Scenario: Unavailable branch keeps the action disabled
- **WHEN** the selected existing branch already has a worktree
- **THEN** the "Create worktree" action is disabled

#### Scenario: Available branch enables the action
- **WHEN** the selected existing branch has no worktree
- **THEN** the "Create worktree" action is enabled
