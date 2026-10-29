# Spec Delta

## ADDED Requirements

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
