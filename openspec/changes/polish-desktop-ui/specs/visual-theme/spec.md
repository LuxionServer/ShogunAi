## ADDED Requirements

### Requirement: Use theme color tokens for status and accent colors
UI elements that convey status (errors, success feedback) or accent emphasis SHALL use the active `colorScheme`'s tokens (e.g. `error`, `primary`) instead of hardcoded color values, so their appearance stays consistent with the app's color scheme.

#### Scenario: Error text reflects the active color scheme
- **WHEN** an error message is shown on any screen
- **THEN** its color comes from `MaterialTheme.colorScheme.error`, instead of a fixed literal color

#### Scenario: Success feedback reflects the active color scheme
- **WHEN** a success message is shown on any screen
- **THEN** its color comes from a theme color token, instead of a fixed literal color

### Requirement: Disabled controls remain visually distinguishable
Disabled buttons and inputs SHALL use a disabled-state color with sufficient contrast against both their enabled state and the surrounding surface, consistent across screens.

#### Scenario: Disabled "Guardar"/"Crear worktree" button
- **WHEN** a primary action button (e.g. "Guardar", "Crear worktree") is disabled because required input is missing
- **THEN** the button is visually distinguishable as disabled while remaining legible against its background
