## MODIFIED Requirements

### Requirement: Group header actions
Each change group's header SHALL offer "Open all in <default runner>" as its button and, in its ⋯ menu, "Open all with…", "Add repository" and "Clean up group". Opening all SHALL work as `shogun open <id> --herdr` or `--orca`, "Add repository" as `shogun new <id> --repos <alias>`, and "Clean up group", enabled only for a cleanup candidate, SHALL be confirmed before removing the group as `shogun clean --yes` would.

#### Scenario: Open all
- **WHEN** Herdr is the default runner and the user activates "Open all in Herdr" on `feature/TASK-1`
- **THEN** `app` and `api` open in Herdr under their repositories' spaces with their own environments and agents

#### Scenario: Open all in Orca
- **WHEN** the user activates "Orca" in "Open all with…" on `feature/TASK-1`
- **THEN** `app` and `api` open in Orca under their repositories with their own environments and agents, and the notice says, per repository, whether its agent was started or focused

#### Scenario: Open all in Orca with a failure
- **WHEN** `api`'s agent doesn't start in Orca in time
- **THEN** `app` is still opened and the view shows `api`'s error with the terminal and the command as technical details

#### Scenario: Add a repository to a group
- **WHEN** the user picks `docs` in "Add repository" of `feature/TASK-1`'s ⋯ menu
- **THEN** `docs`'s worktree is created on `feature/TASK-1` in `docs`'s project worktrees folder and appears in the group

#### Scenario: Group not ready to clean up
- **WHEN** `api`'s worktree of the group isn't merged
- **THEN** "Clean up group" is disabled and its reason says `api` isn't merged

#### Scenario: Clean up a group
- **WHEN** the user activates "Clean up group" on a candidate and confirms
- **THEN** its worktrees are removed and the dialog listed them and their Herdr workspaces before confirming, with the same option to delete merged branches as "Clean up"

#### Scenario: Clean up a group open in Orca
- **WHEN** a worktree of the candidate has Orca terminals
- **THEN** the dialog marks it as open in Orca and says its terminals and agents will be closed, and Orca not running leaves the dialog as before

#### Scenario: Long branch in the header
- **WHEN** a group's branch and repositories don't fit the header's second line
- **THEN** the line is truncated and its tooltip shows the full branch and repositories

### Requirement: Open the multirepo agent from the app
The multirepo view SHALL offer opening the multirepo agent in Herdr in its header's "More" (⋯) menu, as "Open multirepo agent in Herdr" when Herdr is the default runner or as "Herdr" under "Open multirepo agent with…" otherwise, which opens the multirepo agent as `shogun open --herdr` does in the multirepo folder, and SHALL tell apart, in English and Spanish, that the agent was opened, started in its already open workspace, or focused.

#### Scenario: Open the multirepo agent
- **WHEN** the user activates it in `product`'s view
- **THEN** the multirepo agent's Herdr workspace opens in the multirepo folder, or its live agent is focused if open

#### Scenario: Open workspace without an agent
- **WHEN** the multirepo's Herdr workspace is open but has no live agent
- **THEN** the agent is started in a new pane of it and the notice says the agent was started, not that it was focused

#### Scenario: Agent doesn't start
- **WHEN** Herdr doesn't detect the multirepo agent in time
- **THEN** the view shows the error naming the pane and the command

### Requirement: Open the multirepo agent in Orca from the app
The multirepo view SHALL offer opening the multirepo agent in Orca in its header's "More" (⋯) menu, only when Orca is installed, as "Open multirepo agent in Orca" when Orca is the default runner or as "Orca" under "Open multirepo agent with…" otherwise, which opens the multirepo agent as `shogun open --orca` does in the multirepo folder, and SHALL tell apart, in English and Spanish, that the agent was started or focused.

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

#### Scenario: Orca not installed
- **WHEN** Orca isn't installed
- **THEN** the view's ⋯ menu doesn't offer opening the multirepo agent in Orca

## ADDED Requirements

### Requirement: Repository alias column fits its aliases
The multirepo view's repository alias column SHALL be as wide as its longest alias, between a minimum and a maximum width, so the usual aliases aren't truncated. An alias longer than the maximum SHALL be truncated with its full text in a tooltip.

#### Scenario: Usual alias
- **WHEN** the multirepo's repositories are `workspace`, `app` and `api`
- **THEN** every row shows `workspace` whole in the alias column

#### Scenario: Very long alias
- **WHEN** an alias doesn't fit the maximum width
- **THEN** it is truncated and its tooltip shows it whole
