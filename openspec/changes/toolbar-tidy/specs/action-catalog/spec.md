# Spec Delta

## MODIFIED Requirements

### Requirement: View header actions
A worktree view's header SHALL keep a single primary action ("New worktree", or "New change" in a multirepo view) and, before it, "Clean up N" as a neutral ghost button, where N is the number of worktrees selected for cleanup in the view, shown only when there is something to clean up and disabled with its reason while it can't run. Its other actions SHALL go into a "More" (⋯) menu, shown only when it has entries: in a multirepo view, "Open multirepo agent in <default runner>" and, when another runner is installed, an "Open multirepo agent with…" submenu with each other runner.

#### Scenario: Clean up from the header
- **WHEN** "All worktrees" has three cleanup candidates
- **THEN** the header shows the ghost button "Clean up 3" before "New worktree", and activating it opens the cleanup confirmation

#### Scenario: Nothing to clean up
- **WHEN** no worktree in the view is selected for cleanup
- **THEN** the header shows no "Clean up" button

#### Scenario: Cleanup running
- **WHEN** a cleanup is running and the view still has candidates
- **THEN** "Clean up N" is disabled and its tooltip says a cleanup is already running

#### Scenario: No ⋯ outside a multirepo
- **WHEN** the user opens "All worktrees" or a project's view
- **THEN** the header has no "⋯" button

#### Scenario: Multirepo agent in both runners
- **WHEN** Herdr is the default runner, Orca is installed and the user opens the ⋯ menu of an approved multirepo's view
- **THEN** it offers "Open multirepo agent in Herdr", then "Open multirepo agent with…" holding "Orca", and no second "Open multirepo agent in …" entry

#### Scenario: Orca is the default runner
- **WHEN** Orca is the default runner and the user opens the ⋯ menu of an approved multirepo's view
- **THEN** it offers "Open multirepo agent in Orca", and Herdr is in the "Open multirepo agent with…" submenu

#### Scenario: Orca not installed
- **WHEN** Orca isn't installed and the user opens the ⋯ menu of a multirepo's view
- **THEN** it offers "Open multirepo agent in Herdr" and no "Open multirepo agent with…" submenu
