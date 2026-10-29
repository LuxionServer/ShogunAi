# default-runner

## Purpose
Choosing which installed runner (Herdr or Orca) the desktop app uses for its primary open action and ⌘O, remembered on this machine.

## ADDED Requirements

### Requirement: Detect the installed runners
The desktop app SHALL detect whether Herdr and Orca are installed together with the other tools. Until the first detection finishes, both SHALL be treated as installed.

#### Scenario: Only Herdr installed
- **WHEN** Herdr is installed and Orca isn't
- **THEN** the app offers opening in Herdr and doesn't offer opening in Orca anywhere

### Requirement: Choose the default runner
Preferences SHALL offer "Default runner" listing only the installed runners. The choice SHALL be remembered in the app settings on this machine. A settings file written before this option existed SHALL behave as if Herdr were chosen.

#### Scenario: Pick Orca
- **WHEN** both runners are installed and the user picks Orca as default runner
- **THEN** the detail's primary action reads "Open in Orca", ⌘O opens the selection in Orca, and the choice survives a restart

#### Scenario: Settings from an earlier version
- **WHEN** the settings file has no default runner
- **THEN** the default runner is Herdr, as before

#### Scenario: Chosen runner uninstalled
- **WHEN** the default runner is Orca and Orca is no longer installed
- **THEN** the app uses Herdr if it is installed, without changing the saved choice

#### Scenario: No runner installed
- **WHEN** neither Herdr nor Orca is installed
- **THEN** Preferences says no runner was found and the detail's primary action opens the worktree in the first available of the terminal, the IDE and its folder
