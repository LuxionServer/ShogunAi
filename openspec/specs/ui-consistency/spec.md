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
- **WHEN** the project configuration screen renders the remove action for a local files entry, whether it is a file or a folder
- **THEN** the `IconButton` exposes a non-empty `contentDescription` describing the action (e.g. "Remove local file or folder")

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

### Requirement: Shared buttons have two sizes and one shape
The shared buttons (primary, secondary, ghost, danger, danger text and neutral text) SHALL offer a compact and a regular size and SHALL set their own label style, so no screen restyles a button's label to make it smaller. Only one action of a view SHALL use the primary style.

#### Scenario: Label style comes from the button
- **WHEN** a compact secondary button shows "Try again"
- **THEN** its label uses the button's label style, the same as every other compact button

#### Scenario: Ghost button
- **WHEN** a ghost button is shown on a surface
- **THEN** it has no fill and no border at rest, uses the secondary text color, and shows a hover state

### Requirement: Form fields keep their label above the input
Every text, path and choice field of a form SHALL show its label above a compact input, never inside it as a floating label, with its help text and its error below the input. The error SHALL replace the help text while it applies.

#### Scenario: Field with help and error
- **WHEN** a project's name field is empty after the user typed in it
- **THEN** the label stays above the input and the error is shown below it in place of the help text

#### Scenario: Path field
- **WHEN** a form asks for a folder
- **THEN** the field shows its label above the input and a "Browse…" action next to it that opens the folder picker

### Requirement: Dialogs share one layout
Every dialog SHALL use one of three widths (small, medium, large), a title, a body that scrolls when it doesn't fit, and a fixed footer with the dismiss action followed by the confirm action, aligned to the end. The confirm action SHALL be the only main action in the dialog.

#### Scenario: Long dialog body
- **WHEN** a dialog's content is taller than the window allows
- **THEN** its body scrolls and its title and footer stay visible

#### Scenario: Footer order
- **WHEN** a confirmation dialog is shown in either language
- **THEN** the dismiss action is on the left of the confirm action and both are aligned to the end of the footer

### Requirement: Disabled controls explain why
A control that is shown disabled SHALL show, when the pointer rests on it, a tooltip that says what is missing for it to be usable, in the selected language, and SHALL expose that reason to assistive technologies as its state. A control that can never be used in its context SHALL NOT be shown.

#### Scenario: Nothing to clean up
- **WHEN** "Clean up" is disabled because no worktree is merged or missing
- **THEN** resting the pointer on it shows a tooltip saying there's nothing to clean up

#### Scenario: Cleanup in progress
- **WHEN** "Clean up" is disabled because a cleanup is running
- **THEN** its tooltip says a cleanup is already running

#### Scenario: Busy Gradle daemon
- **WHEN** a Gradle daemon's "Stop" is disabled because the daemon is busy
- **THEN** its tooltip says only idle daemons can be stopped

#### Scenario: No idle daemon
- **WHEN** "Stop idle daemons" is disabled because no daemon is idle
- **THEN** its tooltip says no daemon is idle

### Requirement: Notices of the same kind are grouped
Notices of the same kind from the same source SHALL be shown as one banner that summarizes them, with their count and the individual messages in expandable details. A scope SHALL show at most one such banner, and dismissing it SHALL dismiss every notice it groups.

#### Scenario: Three trims
- **WHEN** idle Gradle daemons were stopped three times to stay within the limit
- **THEN** the Gradle daemons view shows a single banner with the total stopped and a count of 3, and its details list the three reports

#### Scenario: Dismiss the group
- **WHEN** the user dismisses a grouped banner
- **THEN** none of the notices it grouped is shown again

### Requirement: Trivial actions confirm in place
An action whose result the user sees at once, such as copying a value, SHALL confirm in the control that started it, by showing a check and "Copied" for 1.5 seconds, instead of a global message.

#### Scenario: Copy a snippet
- **WHEN** the user copies the simulator settings snippet in a project's settings
- **THEN** the button shows a check and "Copied" for 1.5 seconds and then returns to its label, and no global message is shown

### Requirement: Titles are declared as headings
The title of a view, the title of a form section, the header of a list group and the title of a dialog SHALL be exposed to assistive technologies as headings.

#### Scenario: View and dialog titles
- **WHEN** a screen reader lists the headings of the "All worktrees" view with the "New worktree" dialog open
- **THEN** the view title, each project group header and the dialog title are listed as headings

#### Scenario: Form sections
- **WHEN** a screen reader lists the headings of a project's settings
- **THEN** each section title is listed as a heading
