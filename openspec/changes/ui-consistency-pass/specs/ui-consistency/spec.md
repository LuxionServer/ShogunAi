## ADDED Requirements

### Requirement: Icon-only actions expose an accessible label
Every icon-only action control (an `IconButton` with no visible text) SHALL provide a `contentDescription` describing the action it performs, in Spanish, matching the app's existing UI copy.

#### Scenario: Edit/delete project icons have descriptions
- **WHEN** the project list screen renders the edit and delete icon actions for a project
- **THEN** each `IconButton` exposes a non-empty `contentDescription` describing that action (e.g. "Editar proyecto", "Eliminar proyecto")

#### Scenario: Remove secret file icon has a description
- **WHEN** the project configuration screen renders the remove action for a secret file entry
- **THEN** the `IconButton` exposes a non-empty `contentDescription` describing the action (e.g. "Quitar archivo de secretos")

### Requirement: Choice controls follow one of two consistent shapes
Every "pick one of N" control in the desktop app SHALL be rendered as either a segmented selector (for a small, fixed set of options) or a dropdown selector (for a longer or dynamically loaded list of options), instead of an ad hoc `RadioButton` group.

#### Scenario: Small fixed option sets use a segmented selector
- **WHEN** the user picks the terminal selection mode, the worktree creation mode, or the branch type
- **THEN** the control is rendered as a segmented selector showing all options at once, not a `RadioButton` list

#### Scenario: Longer or dynamic option lists use a dropdown selector
- **WHEN** the user picks a fixed terminal emulator or an existing branch to create a worktree from
- **THEN** the control is rendered as a dropdown selector, not a `RadioButton` list

### Requirement: Worktree creation form is visually distinct from the worktree list
The worktree management screen SHALL present the "create worktree" form and the worktree list as visually separate sections, instead of interleaving both inside a single scrolling list.

#### Scenario: Creation form and worktree list are separate sections
- **WHEN** the worktree management screen is displayed
- **THEN** the "create worktree" form is rendered as a distinct, non-scrolling section separate from the scrollable list of existing worktrees
