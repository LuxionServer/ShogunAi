## MODIFIED Requirements

### Requirement: Preferences window
The app SHALL provide a Preferences window, opened from the menu or with Cmd/Ctrl+Comma, holding the theme mode, the language, a "Tools" section with the terminal and the default IDE, each chosen among the ones detected on the machine, and a "Resources" section with the capacity profile. Changes SHALL apply immediately to the main window.

#### Scenario: Change the theme from Preferences
- **WHEN** the user opens Preferences and selects Dark
- **THEN** the main window and Preferences switch to the dark scheme immediately

#### Scenario: Choose a terminal
- **WHEN** Terminal and iTerm2 are installed and the user chooses iTerm2 in Preferences
- **THEN** the worktree actions read "Open in iTerm2" and open iTerm2

#### Scenario: Nothing detected
- **WHEN** no supported terminal is installed
- **THEN** the terminal choice says none was found and "Open in terminal" isn't offered

## ADDED Requirements

### Requirement: Choose the capacity profile in Preferences
The "Resources" section SHALL offer the profiles Automatic, Light, Balanced, One per worktree and Custom, and show the limits each one gives on this machine for emulators, iOS simulators (on macOS only) and Gradle daemons. Custom SHALL let the user set each limit to a positive number or to no limit. The choice SHALL be saved with the app's settings and used by the app and the `shogun` command.

#### Scenario: Automatic shows its limits
- **WHEN** the user opens the "Resources" section on a 16 GB machine with 8 cores
- **THEN** Automatic is selected and says it gives 1 emulator, 1 iOS simulator and 2 Gradle daemons

#### Scenario: Custom limits
- **WHEN** the user chooses Custom and sets emulators to 3
- **THEN** the next emulator start from the app or from `shogun emulator start` uses a limit of 3

#### Scenario: Invalid number
- **WHEN** the user types 0 or a non-number as a Custom limit
- **THEN** the field shows that it must be a positive number or no limit, and the previous value is kept
