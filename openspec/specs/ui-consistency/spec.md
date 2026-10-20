# ui-consistency Specification

## Purpose
Cross-screen consistency rules for the desktop UI: accessible labels on icon-only actions, two shapes for choice controls, and a creation form kept apart from the list it adds to.

## Requirements

### Requirement: Icon-only actions expose an accessible label
Every icon-only action control (an `IconButton` with no visible text) SHALL provide a `contentDescription` describing the action it performs, in the active UI language.

#### Scenario: Edit/delete project icons have descriptions
- **WHEN** the project list screen renders the edit and delete icon actions for a project
- **THEN** each `IconButton` exposes a non-empty `contentDescription` describing that action (e.g. "Edit project", "Delete project")

#### Scenario: Remove secret file icon has a description
- **WHEN** the project configuration screen renders the remove action for a secret file entry
- **THEN** the `IconButton` exposes a non-empty `contentDescription` describing the action (e.g. "Remove secret file")

### Requirement: Choice controls follow one of two consistent shapes
Every "pick one of N" control in the desktop app SHALL be rendered as either a segmented selector (for a small, fixed set of options) or a dropdown (for a longer or dynamically loaded list of options), instead of an ad hoc `RadioButton` group.

#### Scenario: Small fixed option sets use a segmented selector
- **WHEN** the user picks the worktree creation mode, the branch type, or the project list sort order
- **THEN** the control is rendered as a segmented selector showing all options at once, not a `RadioButton` list

#### Scenario: Longer or dynamic option lists use a dropdown
- **WHEN** the user picks an existing branch to create a worktree from
- **THEN** the control is rendered as a dropdown selector, not a `RadioButton` list

### Requirement: Worktree creation form is visually distinct from the worktree list
The worktree management view SHALL present the "create worktree" form in a dialog, separate from the worktree list, instead of interleaving the form with the worktree rows or keeping it on the view.

#### Scenario: Creation form and worktree list are separate sections
- **WHEN** the worktree management view is displayed
- **THEN** the list of worktrees takes the content area, and the "create worktree" form is only shown in the "New worktree" dialog

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

### Requirement: Button labels stay on one line
Every button label in the desktop UI SHALL be shown on a single line, in English and in Spanish, without wrapping in the middle of a word and without being cut. A button SHALL be as wide as its label needs, and when the available space is too small the layout SHALL give the button room or shorten the surrounding content instead of wrapping the label.

#### Scenario: Longest Spanish label
- **WHEN** the UI language is Spanish and a button shows its longest label
- **THEN** the label is on one line and fully visible

#### Scenario: Narrow dialog actions
- **WHEN** a dialog with several actions is shown in either language
- **THEN** each action's label is on one line and fully visible

#### Scenario: Narrow window
- **WHEN** the window is resized to its minimum width
- **THEN** no button label wraps or is cut
