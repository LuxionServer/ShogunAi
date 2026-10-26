# Spec Delta

## ADDED Requirements

### Requirement: Desktop entry in the Machine section
While a worktree holds the desktop turn or any worktree waits for it, the Machine section SHALL show a "Desktop" entry, also when no saved project declares a provider, with a summary naming the holder and the number of waiters. Its view SHALL show the holder with project, worktree, since when and until when, and "Release" with a confirmation naming it, then the waiters in order with since when each waits.

#### Scenario: Held with waiters
- **WHEN** `api` · `TASK-1` holds the turn and two worktrees wait
- **THEN** the Machine section shows "Desktop" with `api` · `TASK-1` and 2 waiting, and its view lists the holder and the two waiters in order

#### Scenario: Desktop free
- **WHEN** nobody holds the turn and nobody waits
- **THEN** the Machine section shows no "Desktop" entry

#### Scenario: No provider declared
- **WHEN** no saved project declares any provider and a worktree holds the turn
- **THEN** the sidebar shows the Machine section with only the "Desktop" entry

#### Scenario: Holder outside the saved projects
- **WHEN** the holder's worktree isn't in any saved project
- **THEN** the view shows its folder as outside the saved projects

#### Scenario: Go to the holder
- **WHEN** the user activates the holder `api` · `TASK-1` in the Desktop view
- **THEN** "All worktrees" opens with `TASK-1` of `api` selected

#### Scenario: Shown while open
- **WHEN** the Desktop view is shown and the holder releases the turn from the command line
- **THEN** the view shows the change without the user refreshing it
