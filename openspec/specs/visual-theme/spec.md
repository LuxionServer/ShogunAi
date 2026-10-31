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
The app SHALL let the user choose between Light, Dark and Automatic in the Preferences window.

#### Scenario: User changes the theme mode
- **WHEN** the user selects a different mode in Preferences
- **THEN** the app applies the new `colorScheme` immediately to every open window, without restarting

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

#### Scenario: A new worktree is not shown in a brand or primary color
- **WHEN** a worktree with no commits of its own is shown
- **THEN** its state uses the neutral status color, not the brand red nor the primary ink

### Requirement: Disabled controls remain visually distinguishable
Disabled buttons and inputs SHALL use a disabled-state color with sufficient contrast against both their enabled state and the surrounding surface, consistent across screens. The text and icon color of a disabled control SHALL reach a contrast ratio of at least 3:1 against every surface and against the disabled container, in both color schemes.

#### Scenario: Disabled "Save"/"Create worktree" button
- **WHEN** a primary action button (e.g. "Save", "Create worktree") is disabled because required input is missing
- **THEN** the button is visually distinguishable as disabled while remaining legible against its background

#### Scenario: Disabled label on a surface
- **WHEN** a disabled text button such as "Clean up" is drawn on the background or any surface container, in light or dark
- **THEN** its label's contrast ratio against that surface is at least 3:1

#### Scenario: Disabled filled button
- **WHEN** a disabled primary or danger button is drawn, in light or dark
- **THEN** its label's contrast ratio against its disabled container is at least 3:1, and its container differs from the enabled one

### Requirement: Text and indicators meet contrast targets
In both color schemes, body and label text SHALL have a contrast ratio of at least 4.5:1 against the surface it is drawn on, and large text, icons and status indicators at least 3:1.

#### Scenario: Text on the brand accent
- **WHEN** a primary button shows its label on the primary color, which is ink instead of the brand accent
- **THEN** the label's contrast ratio against the primary color is at least 4.5:1, in light and dark

#### Scenario: Status text on a surface
- **WHEN** a status label is drawn in a status color on the background or a surface container
- **THEN** its contrast ratio is at least 4.5:1, in light and dark

#### Scenario: Accent text and focus on a surface
- **WHEN** a link or a focus ring is drawn in the accent color on the background or any surface container
- **THEN** a link reaches 4.5:1 and a focus ring at least 3:1, in light and dark

#### Scenario: Text on the selection
- **WHEN** a selected row shows its text, its secondary text and its status colors on the accent container
- **THEN** each of them reaches 4.5:1, in light and dark

### Requirement: Data text is monospaced with tabular figures
Paths, branch names, ports, process ids, and sizes, counts and durations written as lone figures SHALL be shown in the app's monospaced family with tabular figures, so values that refresh do not shift the layout. Counts inside sentences (such as "5 worktrees · 1.2 GB" or "2 ports · 5 vars") SHALL be shown in the UI family with tabular figures. States written as words (such as "working", "busy" or "idle"), relative dates written as text and project names SHALL be shown in the UI family, not in the monospaced one.

#### Scenario: Size updates in place
- **WHEN** a worktree's size changes from "1.2 GB" to "1.8 GB" during a refresh
- **THEN** the digits keep the same width and the surrounding text doesn't move

#### Scenario: Branch name
- **WHEN** a branch name is shown
- **THEN** it uses the monospaced family

#### Scenario: Agent state
- **WHEN** a row shows "Claude · working" or a Gradle daemon shows "idle · 12 minutes ago"
- **THEN** that text uses the UI family

#### Scenario: Count inside a sentence
- **WHEN** a view's header reads "5 worktrees · 1.2 GB · 2 to clean up"
- **THEN** the sentence uses the UI family with tabular figures, not the monospaced one

#### Scenario: Lone figure
- **WHEN** a row's Git cell shows "✎ 3"
- **THEN** it uses the monospaced family

### Requirement: Same type families on every platform
The app SHALL bundle its UI and monospaced type families, so text renders with the same fonts on macOS, Windows and Linux. The bundled fonts SHALL be distributed under a license that allows redistribution, shipped with the app.

#### Scenario: Linux without the fonts installed
- **WHEN** the app runs on a Linux machine that doesn't have the app's fonts installed
- **THEN** text renders in the bundled families, not in a system fallback

### Requirement: Primary is ink, not the brand
The primary color role SHALL be a neutral ink (dark in the light scheme, light in the dark scheme) with neutral containers, and it SHALL mark the single main action of a view. The brand red SHALL only be used for brand marks (logo, tray icon, About), never for a control, a link, a focus indicator or a state, and no primary or brand container SHALL share its color with the danger container.

#### Scenario: Main action of a view
- **WHEN** the "New worktree" button is shown in a worktree view's header
- **THEN** it is filled with the ink primary color, not with the brand red

#### Scenario: Brand and danger containers are distinct
- **WHEN** the light and dark schemes are compared
- **THEN** the primary container differs from the danger container in both

### Requirement: Accent role for selection, focus and links
The theme SHALL define an accent color with its on-color and its container, in light and dark, used for the keyboard focus indicator, links, the selected row and the active entry of an index. The accent SHALL NOT convey a status.

#### Scenario: Owner link in a Machine view
- **WHEN** a Gradle daemon's owner worktree is shown as a link
- **THEN** the link uses the accent color

#### Scenario: Active settings section
- **WHEN** a section of the project settings index is the one in view
- **THEN** its entry uses the accent color and the others the secondary text color

### Requirement: Semantic status aliases
The theme SHALL expose `attention` as the same colors as warning and `active` as the same colors as info, so code that shows "needs you" or "agent working" can name the use instead of the hue.

#### Scenario: Attention equals warning
- **WHEN** the attention colors are read in light or dark
- **THEN** they are the warning colors of that scheme

### Requirement: Elevation levels
The theme SHALL define three elevation levels: level 0 is the surface; level 1 is a low surface container with a 1 dp border; level 2, for menus, dialogs and popovers, is a high surface container with a 1 dp border and a small shadow. Text and secondary text SHALL reach 4.5:1 on every level, and each level's border SHALL be distinguishable from its container and from the background.

#### Scenario: Text on a level
- **WHEN** body text and secondary text are drawn on level 1 or level 2, in light or dark
- **THEN** both reach a contrast ratio of at least 4.5:1

#### Scenario: Border of a level
- **WHEN** a level 1 or level 2 container is drawn on the background
- **THEN** its border's contrast against the container and against the background is at least 1.3:1

### Requirement: Overlay scrim token
The dimming drawn behind an overlay panel SHALL come from a theme token with its alpha, not from a literal color in a screen.

#### Scenario: Detail drawn over the list
- **WHEN** the worktree detail is drawn over the list on a narrow window
- **THEN** the list behind it is dimmed with the theme's scrim token

### Requirement: Typography roles
The theme SHALL define a view title (UI family, weight 600, 16/22), a section or group title (600, 13/18) and a section label (monospaced, weight 500, 10.5/14, letter spacing 0.06 em, shown in uppercase), next to body text (13/18) and secondary text (12/16). Every view's header title SHALL use the view title role.

#### Scenario: View header
- **WHEN** the "Active", "All worktrees", a project's, a Machine view's or the project settings header is shown
- **THEN** its title uses the view title role, not a 22 sp headline

### Requirement: Radius and control size scales
Corner radii SHALL come from one scale: 4 dp for controls, 6 dp for rows and menu items, 8 dp for panels and menus, 12 dp for dialogs. No button or control SHALL be drawn as a pill. Buttons SHALL be 28 dp high in their compact size and 32 dp in their regular size, with 10 dp horizontal padding, and icon-only buttons SHALL be 28 × 28 dp.

#### Scenario: Compact button
- **WHEN** a compact button is shown
- **THEN** it is 28 dp high with 4 dp corners

#### Scenario: Regular button
- **WHEN** a regular button is shown in a form or a dialog
- **THEN** it is 32 dp high with 4 dp corners
