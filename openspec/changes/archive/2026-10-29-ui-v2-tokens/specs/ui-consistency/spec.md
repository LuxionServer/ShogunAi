# Spec Delta

## MODIFIED Requirements

### Requirement: Destructive actions are visually distinct
Actions that delete or remove something SHALL use the danger color, both where they are offered and in the confirmation that follows, so they can't be mistaken for neutral actions. A filled danger button SHALL only be used for the confirm action of a confirmation dialog. An action in a view's header that only opens a confirmation dialog (such as "Clean up") SHALL use the neutral ghost style, so it doesn't compete with the view's main action; the danger color appears in its confirmation.

#### Scenario: Delete action next to a neutral action
- **WHEN** a delete action is shown next to a neutral action
- **THEN** the delete action uses the danger color and the neutral one doesn't

#### Scenario: Confirmation dialog
- **WHEN** a confirmation dialog for a deletion is shown
- **THEN** its confirm action uses the danger color and its cancel action is neutral

#### Scenario: Clean up in a view's header
- **WHEN** a worktree view's header shows "Clean up" next to "New worktree"
- **THEN** "Clean up" is a neutral ghost button, "New worktree" is the only filled action, and the cleanup dialog's confirm action uses the danger color

#### Scenario: Delete outside a dialog
- **WHEN** a delete action that asks for confirmation is offered in a settings view
- **THEN** it is a danger text button, not a filled danger button

### Requirement: Keyboard focus is visible
The element that has keyboard focus SHALL show a visible focus indicator in the accent color, never in the brand red, and Tab SHALL move focus through the interactive elements in reading order.

#### Scenario: Tabbing through actions
- **WHEN** the user presses Tab repeatedly
- **THEN** each focused element shows a focus indicator in the accent color, in reading order

## ADDED Requirements

### Requirement: Shared buttons have two sizes and one shape
The shared buttons (primary, secondary, ghost, danger, danger text and neutral text) SHALL offer a compact and a regular size and SHALL set their own label style, so no screen restyles a button's label to make it smaller. Only one action of a view SHALL use the primary style.

#### Scenario: Label style comes from the button
- **WHEN** a compact secondary button shows "Try again"
- **THEN** its label uses the button's label style, the same as every other compact button

#### Scenario: Ghost button
- **WHEN** a ghost button is shown on a surface
- **THEN** it has no fill and no border at rest, uses the secondary text color, and shows a hover state
