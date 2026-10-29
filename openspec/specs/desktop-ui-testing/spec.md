# desktop-ui-testing Specification

## Purpose
Headless Compose UI tests for the reusable selectors and the creation form guard, run by the regular test task.

## Requirements

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

### Requirement: Button labels are covered by automated UI tests
The desktop app SHALL have Compose UI tests, run by `./gradlew test`, that render the shared buttons and the screens' action rows with the longest Spanish labels in a narrow width and check that every label is on one line.

#### Scenario: Shared buttons with long labels
- **WHEN** a UI test renders each shared button with a long Spanish label in a narrow container
- **THEN** the label's text layout has a single line and isn't truncated

#### Scenario: Dialog actions in Spanish
- **WHEN** a UI test renders the removal and cleanup dialogs in Spanish
- **THEN** every action's label has a single line

### Requirement: Shared form and state components are covered by automated UI tests
The desktop app SHALL have Compose UI tests, run by `./gradlew test`, for the shared field, dialog, empty state, error state, placeholder rows, in-place confirmation and disabled-reason components.

#### Scenario: Field shows error instead of help
- **WHEN** a UI test renders a field with help text and an error
- **THEN** the label and the error are displayed and the help text isn't

#### Scenario: Dialog title is a heading
- **WHEN** a UI test renders a dialog
- **THEN** its title node has heading semantics and the footer shows the dismiss action before the confirm action

#### Scenario: Error state folds the raw message
- **WHEN** a UI test renders an error state with a raw message
- **THEN** the raw message isn't displayed until "Technical details" is clicked

#### Scenario: In-place confirmation reverts
- **WHEN** a UI test clicks a copy control and advances the clock by 1.5 seconds
- **THEN** the control shows "Copied" right after the click and its original label afterwards

#### Scenario: Disabled reason is exposed
- **WHEN** a UI test renders a disabled control with a reason
- **THEN** the control is disabled and its reason is shown when the pointer rests on it

### Requirement: Notice grouping and headings are covered by automated tests
The grouping of notices SHALL be covered by unit tests, and the headings of views, group headers, form sections and dialogs by UI tests.

#### Scenario: Grouping by kind and source
- **WHEN** a test groups notices of two kinds from one source and one kind from another source
- **THEN** it gets one group per kind and source, each with its count and its notices in order

#### Scenario: Headings in the worktree view
- **WHEN** a UI test renders the worktree view header and a project group header
- **THEN** both titles have heading semantics
