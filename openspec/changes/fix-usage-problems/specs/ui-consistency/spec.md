# Spec Delta

## ADDED Requirements

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
