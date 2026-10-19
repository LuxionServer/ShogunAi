## ADDED Requirements

### Requirement: IDE for a project on this machine
The project settings' Agent section SHALL let the user choose the IDE the project's worktrees open in on this machine, among the detected ones, or leave it on the default from Preferences. The choice SHALL be stored with the project on this machine, not in the repository.

#### Scenario: Override the IDE
- **WHEN** the user chooses Android Studio for a project and saves
- **THEN** that project's worktrees open in Android Studio while other projects keep the default IDE

#### Scenario: Overridden IDE uninstalled
- **WHEN** the IDE chosen for a project is no longer installed
- **THEN** the settings say it wasn't found, and the worktrees open in the default IDE
