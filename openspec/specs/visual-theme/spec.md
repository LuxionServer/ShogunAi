# visual-theme Specification

## Purpose
Defines the app's own Material 3 look instead of the library defaults.

## Requirements

### Requirement: App-specific Material3 theme
The application SHALL apply a Material3 color scheme and typography of its own (not `MaterialTheme`'s default values) to every screen, consistently. Every color role of the light and dark color schemes, including the surface container, container and outline variant roles used by dialogs, menus, cards and selected controls, SHALL be defined by the app.

#### Scenario: Screens use the app's theme
- **WHEN** the user navigates between the app's screens and opens its dialogs and menus
- **THEN** all of them inherit the same app-specific `colorScheme` and `typography`, with none left on Material3's default values

#### Scenario: No baseline color roles
- **WHEN** a dialog, a dropdown menu, a card or a selected segmented button is shown in the light or the dark scheme
- **THEN** its colors come from roles the app defines, not from Material3's baseline palette

### Requirement: Light/dark/automatic theme mode
The app SHALL support three theme modes: `Light`, `Dark` and `Automatic`. In `Automatic` mode, the app SHALL use the `colorScheme` (light or dark) matching the operating system's active theme. In `Light` or `Dark` mode, the app SHALL always use the `colorScheme` for that mode, regardless of the operating system's theme.

#### Scenario: Operating system in dark mode with Automatic mode
- **WHEN** the theme mode is Automatic and the operating system has dark mode enabled
- **THEN** the app shows its dark `colorScheme`

#### Scenario: Operating system in light mode with Automatic mode
- **WHEN** the theme mode is Automatic and the operating system has light mode enabled
- **THEN** the app shows its light `colorScheme`

#### Scenario: Manual mode overrides the operating system
- **WHEN** the user sets the theme mode to Light or Dark
- **THEN** the app always shows that `colorScheme`, whatever the operating system's mode

#### Scenario: The operating system changes theme while Automatic mode is active
- **WHEN** the theme mode is Automatic and the user changes the operating system's theme while the app is open
- **THEN** the app updates its `colorScheme` without needing a restart

### Requirement: Theme mode selection from the UI
The app SHALL expose a control reachable from every screen (`ProjectListScreen`, `ProjectConfigScreen`, `WorktreeScreen`) that lets the user choose between Light, Dark and Automatic.

#### Scenario: User changes the theme mode
- **WHEN** the user selects a different mode in the theme control
- **THEN** the app applies the new `colorScheme` immediately, without restarting

### Requirement: Theme preference persistence
The app SHALL remember the theme mode chosen by the user across app restarts. If there is no stored preference (first launch, or a missing/corrupt preference file), the default mode SHALL be Automatic.

#### Scenario: Restart after choosing a manual mode
- **WHEN** the user chooses Dark and restarts the app
- **THEN** the app starts in Dark mode

#### Scenario: First launch without a stored preference
- **WHEN** the app starts for the first time without a theme preference file
- **THEN** the app starts in Automatic mode

### Requirement: Use theme color tokens for status and accent colors
UI elements that convey status SHALL use the theme's semantic status colors (success, warning, info, danger, neutral) instead of hardcoded values. Each status color SHALL keep one meaning across the app, and the brand accent SHALL NOT be used to convey a status.

#### Scenario: Error text reflects the active color scheme
- **WHEN** an error message is shown on any screen
- **THEN** its color comes from the theme's danger color, instead of a fixed literal color

#### Scenario: Success feedback reflects the active color scheme
- **WHEN** a success message is shown on any screen
- **THEN** its color comes from the theme's success color, instead of a fixed literal color

#### Scenario: A positive state is not shown in the brand accent
- **WHEN** a worktree is shown as merged, or a device is shown as running
- **THEN** the state uses the success color, not the brand accent

#### Scenario: A warning state is not shown like an error
- **WHEN** a worktree has uncommitted changes
- **THEN** the state uses the warning color, distinct from the danger color used for errors

### Requirement: Disabled controls remain visually distinguishable
Disabled buttons and inputs SHALL use a disabled-state color with sufficient contrast against both their enabled state and the surrounding surface, consistent across screens.

#### Scenario: Disabled "Save"/"Create worktree" button
- **WHEN** a primary action button (e.g. "Save", "Create worktree") is disabled because required input is missing
- **THEN** the button is visually distinguishable as disabled while remaining legible against its background

### Requirement: Text and indicators meet contrast targets
In both color schemes, body and label text SHALL have a contrast ratio of at least 4.5:1 against the surface it is drawn on, and large text, icons and status indicators at least 3:1.

#### Scenario: Text on the brand accent
- **WHEN** a primary button shows its label on the brand accent color
- **THEN** the label's contrast ratio against the accent is at least 4.5:1, in light and dark

#### Scenario: Status text on a surface
- **WHEN** a status label is drawn in a status color on the background or a surface container
- **THEN** its contrast ratio is at least 4.5:1, in light and dark

### Requirement: Data text is monospaced with tabular figures
Paths, branch names, ports, process ids, sizes, counts and durations SHALL be shown in the app's monospaced family with tabular figures, so values that refresh do not shift the layout.

#### Scenario: Size updates in place
- **WHEN** a worktree's size changes from "1.2 GB" to "1.8 GB" during a refresh
- **THEN** the digits keep the same width and the surrounding text doesn't move

#### Scenario: Branch name
- **WHEN** a branch name is shown
- **THEN** it uses the monospaced family

### Requirement: Same type families on every platform
The app SHALL bundle its UI and monospaced type families, so text renders with the same fonts on macOS, Windows and Linux. The bundled fonts SHALL be distributed under a license that allows redistribution, shipped with the app.

#### Scenario: Linux without the fonts installed
- **WHEN** the app runs on a Linux machine that doesn't have the app's fonts installed
- **THEN** text renders in the bundled families, not in a system fallback
