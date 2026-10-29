## MODIFIED Requirements

### Requirement: Every action is declared once
The desktop app SHALL declare each action once with an identifier, a scope (app, project, multirepo, worktree or machine), a group (open, copy, provision, resources, manage or destructive), a label, an icon, an optional shortcut, an availability, whether it is agent-aware, and the confirmation it requires, if any.

#### Scenario: Actions covered
- **WHEN** the catalog is built for a worktree, a project view, a multirepo view and a change group
- **THEN** it declares opening the worktree in Herdr, Orca, the terminal, the IDE, Claude Desktop and its folder, copying its path and its launch command, deleting it, "Clean up", "New worktree", "New change", "Project settings", "Reload", "Open all in Herdr", "Open all in Orca", "Open multirepo agent in Herdr" and "Open multirepo agent in Orca"

#### Scenario: Same label everywhere
- **WHEN** the detail, the row's context menu, the command palette, the tray and the menu bar show "Open in Orca" for the same worktree
- **THEN** all of them take its label, availability and agent choice from the same declaration

#### Scenario: Destructive actions ask first
- **WHEN** the user activates "Delete worktree" from any surface
- **THEN** the delete confirmation opens before anything is removed

### Requirement: View header actions
A worktree view's header SHALL keep a single primary action ("New worktree", or "New change" in a multirepo view) and SHALL move its other actions into a "More" (⋯) menu: "Open multirepo agent in Herdr" and, when Orca is installed, "Open multirepo agent in Orca" in a multirepo view and, last after a separator in danger text, "Clean up", disabled with its reason when nothing can be cleaned up.

#### Scenario: Clean up from the header
- **WHEN** "All worktrees" has cleanup candidates and the user opens the header's ⋯ menu
- **THEN** "Clean up" is the last item, in danger text, and opens the cleanup confirmation

#### Scenario: Multirepo agent in both runners
- **WHEN** Orca is installed and the user opens the ⋯ menu of an approved multirepo's view
- **THEN** it offers "Open multirepo agent in Herdr", then "Open multirepo agent in Orca", then "Clean up" after a separator

#### Scenario: Orca not installed
- **WHEN** Orca isn't installed and the user opens the ⋯ menu of a multirepo's view
- **THEN** it offers "Open multirepo agent in Herdr" and "Clean up", and no Orca item

## ADDED Requirements

### Requirement: Change group header actions come from the catalog
A change group's header SHALL show its open actions as declared by the catalog for that group, in its order: "Open all in Herdr", then "Open all in Orca" when Orca is installed. "Open all in Orca" SHALL be disabled with its reason while the group is being opened in Orca.

#### Scenario: Both runners
- **WHEN** Orca is installed and the multirepo view groups worktrees by change
- **THEN** each group's header shows "Open all in Herdr" followed by "Open all in Orca"

#### Scenario: Opening in progress
- **WHEN** the user activates "Open all in Orca" on `feature/TASK-1` and it hasn't finished
- **THEN** that header's "Open all in Orca" is disabled and explains that it is opening, and other groups' headers aren't affected
