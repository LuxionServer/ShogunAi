# Spec Delta

## MODIFIED Requirements

### Requirement: Theme mode selection from the UI
The app SHALL let the user choose between Light, Dark and Automatic in the Preferences window.

#### Scenario: User changes the theme mode
- **WHEN** the user selects a different mode in Preferences
- **THEN** the app applies the new `colorScheme` immediately to every open window, without restarting
