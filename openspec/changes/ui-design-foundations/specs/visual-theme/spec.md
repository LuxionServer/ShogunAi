# Spec Delta

## MODIFIED Requirements

### Requirement: App-specific Material3 theme
The application SHALL apply a Material3 color scheme and typography of its own (not `MaterialTheme`'s default values) to every screen, consistently. Every color role of the light and dark color schemes, including the surface container, container and outline variant roles used by dialogs, menus, cards and selected controls, SHALL be defined by the app.

#### Scenario: Screens use the app's theme
- **WHEN** the user navigates between the app's screens and opens its dialogs and menus
- **THEN** all of them inherit the same app-specific `colorScheme` and `typography`, with none left on Material3's default values

#### Scenario: No baseline color roles
- **WHEN** a dialog, a dropdown menu, a card or a selected segmented button is shown in the light or the dark scheme
- **THEN** its colors come from roles the app defines, not from Material3's baseline palette

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

## ADDED Requirements

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
