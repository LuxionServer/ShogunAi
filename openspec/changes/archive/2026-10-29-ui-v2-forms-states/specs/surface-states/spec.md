# Spec Delta

## Purpose

Defines how every asynchronous surface of the desktop app (a list, a view, a section) presents its loading, empty, error and not-applicable states, so the user can always tell "not read yet" from "nothing here" from "couldn't be read".

## ADDED Requirements

### Requirement: First load shows placeholder rows
A list that is read for the first time SHALL show placeholder rows in the place of its content until the first result arrives, instead of a lone spinner or an empty area.

#### Scenario: Project read for the first time
- **WHEN** a project's worktree list is being read for the first time in "All worktrees"
- **THEN** that project's group shows placeholder rows and no worktree rows

#### Scenario: Placeholders are not announced as content
- **WHEN** placeholder rows are shown
- **THEN** they expose a single "Loading" description to assistive technologies and no clickable element

### Requirement: Reloading keeps the previous data
A surface that already has data SHALL keep showing it while it's read again, with at most a small progress indicator, and SHALL replace it only when the new result arrives.

#### Scenario: Reload of a loaded project
- **WHEN** a project whose worktrees are shown is reloaded
- **THEN** its rows stay visible during the reload and no placeholder rows replace them

### Requirement: Empty surfaces show one sentence and one action
A surface whose read succeeded with nothing to show SHALL show an empty state with an icon, one sentence saying what's missing, at most one main action and an optional secondary link.

#### Scenario: Nothing active
- **WHEN** the Active view has no worktree with activity
- **THEN** it shows an empty state with a sentence, "New worktree" as its action and "See all worktrees" as its link

#### Scenario: Filter without matches
- **WHEN** the text filter matches no worktree
- **THEN** the list shows an empty state saying nothing matches, with "Clear filter" as its action

#### Scenario: First launch
- **WHEN** the app has no saved project
- **THEN** the content area shows an empty state with "Add project" as its action and "Take the tour" as its link

### Requirement: Errors stay in the scope that failed
A surface that couldn't be read SHALL show its error inside its own scope (the section, the group or the view) and SHALL NOT show it in an enclosing scope.

#### Scenario: One group fails
- **WHEN** one project's worktree list can't be read
- **THEN** the error is shown inside that project's group and the view's header shows no error

### Requirement: Not applicable surfaces are not drawn
A section that doesn't apply to its subject SHALL NOT be drawn, while a section that applies but hasn't been read yet SHALL be drawn with its placeholder.

#### Scenario: Provider not declared
- **WHEN** a repository doesn't declare a provider
- **THEN** no section or placeholder for that provider is shown

### Requirement: Error state for a surface that can't be read
A whole view or section that can't be read because something outside the app is missing SHALL show an error state: one sentence about what's missing, one action to recover, and the raw message folded under "Technical details".

#### Scenario: Error state with details
- **WHEN** an error state is shown with a raw message
- **THEN** the sentence and the action are visible, and the raw message appears only after expanding "Technical details"
