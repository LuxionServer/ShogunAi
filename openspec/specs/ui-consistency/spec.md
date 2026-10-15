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
The worktree management screen SHALL present the "create worktree" form and the worktree list as visually separate sections, instead of interleaving the form with the worktree rows.

#### Scenario: Creation form and worktree list are separate sections
- **WHEN** the worktree management screen is displayed
- **THEN** the "create worktree" form is rendered as its own card above the list of existing worktrees, separate from the worktree rows
