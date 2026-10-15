## REMOVED Requirements

### Requirement: Single color scheme (no light/dark)
**Reason**: Replaced by Light/Dark/Automatic theme mode support — see the "Light/dark/automatic theme mode" and "Theme mode selection from the UI" requirements in this same capability.
**Migration**: No data migration needed. The app now starts in Automatic mode by default (following the operating system) instead of the previous single fixed dark `colorScheme`.

## ADDED Requirements

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
