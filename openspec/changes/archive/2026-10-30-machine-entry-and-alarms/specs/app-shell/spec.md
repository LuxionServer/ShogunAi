# Spec Delta

## ADDED Requirements

### Requirement: Preferences from the sidebar
The sidebar SHALL end with a gear button, in both the expanded sidebar and the rail, labelled "Preferences" for its tooltip and assistive technologies, that opens the Preferences window like the menu and Cmd/Ctrl+Comma do.

#### Scenario: Open Preferences from the gear
- **WHEN** the user activates the gear at the bottom of the sidebar
- **THEN** the Preferences window opens

#### Scenario: Gear in the rail
- **WHEN** the window is narrow enough for the sidebar to become the rail
- **THEN** the gear stays at the bottom of the rail with its "Preferences" tooltip
