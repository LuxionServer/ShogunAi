## MODIFIED Requirements

### Requirement: Group header actions
Each change group's header SHALL offer "Open all in Herdr", which opens every worktree of the group as `shogun open <id> --herdr` does, "Open all in Orca", which opens them as `shogun open <id> --orca` does, "Add repository", which creates a worktree on the group's branch in a repository of the multirepo not yet in it, as `shogun new <id> --repos <alias>` does, and "Clean up group", enabled only when the group is a cleanup candidate and confirmed before removing it as `shogun clean --yes` would.

#### Scenario: Open all
- **WHEN** the user activates "Open all in Herdr" on `feature/TASK-1`
- **THEN** `app` and `api` open in Herdr under their repositories' spaces with their own environments and agents

#### Scenario: Open all in Orca
- **WHEN** the user activates "Open all in Orca" on `feature/TASK-1`
- **THEN** `app` and `api` open in Orca under their repositories with their own environments and agents, and the notice says, per repository, whether its agent was started or focused

#### Scenario: Open all in Orca with a failure
- **WHEN** `api`'s agent doesn't start in Orca in time
- **THEN** `app` is still opened and the view shows `api`'s error with the terminal and the command as technical details

#### Scenario: Add a repository to a group
- **WHEN** the user activates "Add repository" on `feature/TASK-1` and picks `docs`
- **THEN** `docs`'s worktree is created on `feature/TASK-1` in `docs`'s project worktrees folder and appears in the group

#### Scenario: Group not ready to clean up
- **WHEN** `api`'s worktree of the group isn't merged
- **THEN** "Clean up group" is disabled and its tooltip says `api` isn't merged

#### Scenario: Clean up a group
- **WHEN** the user activates "Clean up group" on a candidate and confirms
- **THEN** its worktrees are removed and the dialog listed them and their Herdr workspaces before confirming, with the same option to delete merged branches as "Clean up"

#### Scenario: Clean up a group open in Orca
- **WHEN** a worktree of the candidate has Orca terminals
- **THEN** the dialog marks it as open in Orca and says its terminals and agents will be closed, and Orca not running leaves the dialog as before

### Requirement: New change from the app
The multirepo view SHALL offer "New change", a dialog like "New worktree" with the change id, feature or fix, fetching first, one checkbox per repository with the default repositories checked, and a selector to open the new worktrees in Herdr or in Orca, defaulting to not opening them. It SHALL create the group as `shogun new <id> --repos` does, report each repository's result and then open the group in the chosen runner as "Open all" does.

#### Scenario: Create a change
- **WHEN** the user enters `TASK-20`, keeps `app` and `api` checked, leaves the selector on not opening and confirms
- **THEN** both worktrees are created on `feature/TASK-20` in their projects' worktrees folders, the group appears in the multirepo view and nothing is opened

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

### Requirement: Multirepo agent in the multirepo view
The multirepo view SHALL show the multirepo agent running in the multirepo folder, with its state, next to "Open multirepo agent in Herdr" and "Open multirepo agent in Orca". Only an agent whose working directory is exactly the multirepo folder SHALL count, and the sidebar entry of the multirepo SHALL reflect its activity.

#### Scenario: Agent working in the folder
- **WHEN** Claude Code runs in `~/projects/product` and is working
- **THEN** the multirepo view shows the agent as working and the multirepo's sidebar row shows activity

#### Scenario: Agent in a member repository
- **WHEN** an agent runs in `~/projects/product/product-app`
- **THEN** it shows under `app`'s worktree and not as the multirepo agent

#### Scenario: Agent managed by Herdr
- **WHEN** Herdr reports an agent whose directory is the multirepo folder
- **THEN** its state comes from Herdr as for a worktree's agent

#### Scenario: Agent managed by Orca
- **WHEN** Orca reports an agent in its workspace whose path is the multirepo folder, a folder workspace included
- **THEN** its state comes from Orca as for a worktree's agent, with Orca's `waiting` shown as blocked

## ADDED Requirements

### Requirement: Open the multirepo agent in Orca from the app
The multirepo view SHALL offer "Open multirepo agent in Orca", which opens the multirepo agent as `shogun open --orca` does in the multirepo folder, and SHALL tell apart, in English and Spanish, that the agent was started or focused.

#### Scenario: Open the multirepo agent in Orca
- **WHEN** the user activates it in `product`'s view and Orca has a workspace in the multirepo folder without a live agent
- **THEN** the agent is started in a new terminal of that workspace and the notice says it was started

#### Scenario: Live agent
- **WHEN** that workspace already has a live agent
- **THEN** its terminal is focused and the notice says the running agent was focused

#### Scenario: No workspace for the folder
- **WHEN** the multirepo folder isn't a Git repository and Orca has no workspace for it
- **THEN** the view shows the error explaining how to add the folder in Orca's app, and nothing changes in Orca

#### Scenario: Agent doesn't start
- **WHEN** Orca doesn't report the multirepo agent in time
- **THEN** the view shows the error naming the terminal and the command
