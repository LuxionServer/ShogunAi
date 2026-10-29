## MODIFIED Requirements

### Requirement: View header actions
A worktree view's header SHALL keep a single primary action ("New worktree", or "New change" in a multirepo view) and SHALL move its other actions into a "More" (⋯) menu: in a multirepo view, "Open multirepo agent in <default runner>" and, when another runner is installed, an "Open multirepo agent with…" submenu with each other runner; and, last after a separator in danger text, "Clean up", disabled with its reason when nothing can be cleaned up.

#### Scenario: Clean up from the header
- **WHEN** "All worktrees" has cleanup candidates and the user opens the header's ⋯ menu
- **THEN** "Clean up" is the last item, in danger text, and opens the cleanup confirmation

#### Scenario: Multirepo agent in both runners
- **WHEN** Herdr is the default runner, Orca is installed and the user opens the ⋯ menu of an approved multirepo's view
- **THEN** it offers "Open multirepo agent in Herdr", then "Open multirepo agent with…" holding "Orca", then "Clean up" after a separator, and no second "Open multirepo agent in …" entry

#### Scenario: Orca is the default runner
- **WHEN** Orca is the default runner and the user opens the ⋯ menu of an approved multirepo's view
- **THEN** it offers "Open multirepo agent in Orca", and Herdr is in the "Open multirepo agent with…" submenu

#### Scenario: Orca not installed
- **WHEN** Orca isn't installed and the user opens the ⋯ menu of a multirepo's view
- **THEN** it offers "Open multirepo agent in Herdr" and "Clean up", and no "Open multirepo agent with…" submenu

### Requirement: Change group header actions come from the catalog
A change group's header SHALL show, as a button, only "Open all in <default runner>", declared by the catalog for that group. Its ⋯ menu SHALL hold, from the same catalog, "Open all with…" with each other installed runner, "Add repository" with each repository not yet in the group, and, last after a separator in danger text, "Clean up group", disabled with its reason when the group can't be cleaned up. An opening in progress SHALL disable its entry with its reason.

#### Scenario: Both runners
- **WHEN** Herdr is the default runner, Orca is installed and the multirepo view groups worktrees by change
- **THEN** each group's header shows the button "Open all in Herdr", and its ⋯ menu offers "Open all with…" holding "Orca"

#### Scenario: Opening in progress
- **WHEN** the user activates "Orca" in "Open all with…" on `feature/TASK-1` and it hasn't finished
- **THEN** that group's Orca entry is disabled and explains that it is opening, and other groups aren't affected

#### Scenario: Clean up group in the menu
- **WHEN** the user opens a group's ⋯ menu
- **THEN** "Clean up group" is its last item, after a separator, in danger text, and the header has no "Clean up group" button

#### Scenario: Group not ready to clean up
- **WHEN** `api`'s worktree of the group isn't merged and the user opens the group's ⋯ menu
- **THEN** "Clean up group" is disabled and says `api` isn't merged
