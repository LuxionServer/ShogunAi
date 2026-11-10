# Spec Delta

## MODIFIED Requirements

### Requirement: Group tag in other views
In every view other than the multirepo view, the row of a worktree that belongs to a group SHALL show a tag with the group's title and the other repositories of the group (e.g. `TASK-12 · +api`), and activating the tag SHALL open the multirepo view with that group shown and selected. "Active" grouped by state SHALL NOT show the tag, since it lists the group as one change row whose child rows are its worktrees.

#### Scenario: Tag in a repository's view
- **WHEN** `app`'s view lists its worktree on `feature/TASK-12`, a group with `app` and `api`
- **THEN** the row shows `TASK-12 · +api`

#### Scenario: Follow the tag
- **WHEN** the user activates the tag
- **THEN** the multirepo view opens with that group expanded and scrolled into view

#### Scenario: No tag inside a change row
- **WHEN** "Active", grouped by state, shows the `TASK-12` change row expanded
- **THEN** its `app` and `api` child rows show no group tag

## ADDED Requirements

### Requirement: Group actions outside the multirepo view
A group's actions SHALL work from any view that offers them, acting on the group's own multirepo: "Add repository" SHALL list the repositories of that multirepo the group lacks, and "Clean up group" SHALL clean up the group's worktrees, whichever view is shown.

#### Scenario: Add a repository from Active
- **WHEN** `product` has `app`, `api` and `docs`, the group `feature/TASK-1` has `app` and `api`, and the user opens the `TASK-1` row's menu in "Active"
- **THEN** "Add repository" offers `docs`, and picking it creates `docs`'s worktree as `shogun new TASK-1 --repos docs` would
