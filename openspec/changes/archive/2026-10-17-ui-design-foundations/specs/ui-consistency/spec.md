# Spec Delta

## ADDED Requirements

### Requirement: Interactive elements give pointer feedback
Every clickable element SHALL show a hover state and the hand pointer when the mouse is over it, including custom clickable rows and headers, not only Material buttons.

#### Scenario: Hovering a clickable row
- **WHEN** the mouse moves over a clickable list row
- **THEN** the row shows a hover background and the pointer becomes a hand

#### Scenario: Disabled element
- **WHEN** the mouse moves over a disabled action
- **THEN** no hover state is shown and the pointer stays an arrow

### Requirement: Icon-only actions show a tooltip
Every icon-only action SHALL show a tooltip with the same text as its accessible label when the mouse rests on it.

#### Scenario: Hovering an icon action
- **WHEN** the mouse rests on an icon-only action for a moment
- **THEN** a tooltip shows the action's name in the active UI language

### Requirement: Destructive actions are visually distinct
Actions that delete or remove something SHALL use the danger color, both where they are offered and in the confirmation that follows, so they can't be mistaken for neutral actions.

#### Scenario: Delete action next to a neutral action
- **WHEN** a delete action is shown next to a neutral action
- **THEN** the delete action uses the danger color and the neutral one doesn't

#### Scenario: Confirmation dialog
- **WHEN** a confirmation dialog for a deletion is shown
- **THEN** its confirm action uses the danger color and its cancel action is neutral

### Requirement: Keyboard focus is visible
The element that has keyboard focus SHALL show a visible focus indicator, and Tab SHALL move focus through the interactive elements in reading order.

#### Scenario: Tabbing through actions
- **WHEN** the user presses Tab repeatedly
- **THEN** each focused element shows a focus indicator, in reading order

### Requirement: Feedback notices share one presentation
Error, warning, success and info messages that report the result of an operation SHALL use one shared notice presentation: an icon and a color for the kind of message, the message, and a dismiss action.

#### Scenario: Error and success notices
- **WHEN** an error notice and later a success notice are shown on any screen
- **THEN** both have the same layout, each with its kind's icon and color and a dismiss action
