# Spec Delta

## ADDED Requirements

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
