# Spec Delta

## ADDED Requirements

### Requirement: Button labels are covered by automated UI tests
The desktop app SHALL have Compose UI tests, run by `./gradlew test`, that render the shared buttons and the screens' action rows with the longest Spanish labels in a narrow width and check that every label is on one line.

#### Scenario: Shared buttons with long labels
- **WHEN** a UI test renders each shared button with a long Spanish label in a narrow container
- **THEN** the label's text layout has a single line and isn't truncated

#### Scenario: Dialog actions in Spanish
- **WHEN** a UI test renders the removal and cleanup dialogs in Spanish
- **THEN** every action's label has a single line
