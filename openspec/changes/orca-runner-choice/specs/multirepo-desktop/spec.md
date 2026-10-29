## MODIFIED Requirements

### Requirement: New change from the app
The multirepo view SHALL offer "New change", a dialog like "New worktree" with the change id, feature or fix, fetching first, one checkbox per repository with the default repositories checked, and a selector to open the new worktrees in Herdr or in Orca, starting on the runner in use when the selector offers it and on not opening them otherwise. It SHALL create the group as `shogun new <id> --repos` does, report each repository's result and then open the group in the chosen runner as "Open all" does.

#### Scenario: Create a change
- **WHEN** the user enters `TASK-20`, keeps `app` and `api` checked, sets the selector to not opening and confirms
- **THEN** both worktrees are created on `feature/TASK-20` in their projects' worktrees folders, the group appears in the multirepo view and nothing is opened

#### Scenario: Selector starts on the runner in use
- **WHEN** Orca is the runner in use and installed, and the user opens "New change"
- **THEN** the selector starts on Orca

#### Scenario: Create and open in Orca
- **WHEN** the user picks Orca in the selector and confirms
- **THEN** both worktrees are created, then opened in Orca as "Open all in Orca" does, and the dialog reports each repository's creation and opening

#### Scenario: Create and open in Herdr
- **WHEN** the user picks Herdr in the selector and confirms
- **THEN** both worktrees are created, then opened in Herdr as "Open all in Herdr" does

#### Scenario: Runner not installed
- **WHEN** Orca isn't installed and Herdr is
- **THEN** the selector offers not opening and Herdr only, and with neither installed it isn't shown

#### Scenario: Opening fails after creating
- **WHEN** the worktrees are created but opening `api` in the chosen runner fails
- **THEN** the change stays created, `app` is opened, and the dialog shows `api`'s opening error

#### Scenario: One repository fails
- **WHEN** `api`'s post-creation command fails
- **THEN** the dialog reports `api`'s failure, and `app` and `api`'s kept worktree appear in the group, and only the worktrees that exist are opened when a runner was picked
