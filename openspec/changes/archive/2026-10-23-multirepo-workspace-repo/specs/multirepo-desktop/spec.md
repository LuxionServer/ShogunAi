## ADDED Requirements

### Requirement: Multirepo agent in the multirepo view
The multirepo view SHALL show the multirepo agent running in the multirepo folder, with its state, next to "Open multirepo agent in Herdr". Only an agent whose working directory is exactly the multirepo folder SHALL count, and the sidebar entry of the multirepo SHALL reflect its activity.

#### Scenario: Agent working in the folder
- **WHEN** Claude Code runs in `~/projects/product` and is working
- **THEN** the multirepo view shows the agent as working and the multirepo's sidebar row shows activity

#### Scenario: Agent in a member repository
- **WHEN** an agent runs in `~/projects/product/product-app`
- **THEN** it shows under `app`'s worktree and not as the multirepo agent

#### Scenario: Agent managed by Herdr
- **WHEN** Herdr reports an agent whose directory is the multirepo folder
- **THEN** its state comes from Herdr as for a worktree's agent

### Requirement: Folder of a multirepo isn't listed as a project
The sidebar SHALL NOT list as a loose project one whose base repository is the multirepo folder; its worktrees SHALL appear under the multirepo as the workspace repository's, and the project SHALL keep its settings. With the declaration's `"workspace": false` it SHALL be listed as an ordinary project.

#### Scenario: Registered project for the folder
- **WHEN** a project is registered for `~/projects/product`, which is a multirepo folder and a Git repository
- **THEN** the sidebar shows no project outside the multirepo for it and its worktrees appear under the multirepo as `workspace`

#### Scenario: Add project from the folder
- **WHEN** the user adds `~/projects/product` as a project
- **THEN** the app explains that it's a multirepo and adds nothing

### Requirement: Workspace repository in the multirepo settings
The multirepo settings SHALL show the workspace repository, marked as detected when the declaration doesn't state it, with a checkbox to declare it, a field for its alias, a checkbox to include it in the default repositories and an option to turn detection off, saving as `shogun multirepo workspace` does.

#### Scenario: Declare a detected workspace
- **WHEN** the settings of `product` show `workspace` as detected and the user checks "Declare" and saves
- **THEN** the declaration holds `"workspace": { "alias": "workspace" }` and the row is no longer marked as detected

#### Scenario: No workspace repository
- **WHEN** the multirepo folder isn't a Git repository
- **THEN** the settings show no workspace option

#### Scenario: New change with the workspace repository
- **WHEN** the workspace repository is declared as a default repository
- **THEN** "New change" lists it with its checkbox checked
