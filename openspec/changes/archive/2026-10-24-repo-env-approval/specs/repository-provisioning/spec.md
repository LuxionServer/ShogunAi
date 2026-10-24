## MODIFIED Requirements

### Requirement: Prepare the environment when provisioning
Creating a worktree (on a new or an existing branch) and provisioning an existing worktree SHALL, once the repository's approval has been settled under the asking rules of the entry point, resolve the worktree's environment, reserving its ports, and write the environment file when one is declared and the repository is approved. While the repository isn't approved, the declared variables and the environment file SHALL be left out (see `worktree-environment`). During creation, a failure to reserve ports or to write the file SHALL remove the new worktree, as a failed copy does; when provisioning, the worktree SHALL be left in place.

#### Scenario: Creation reserves ports
- **WHEN** the repository declares `{ "env": { "PORT": "${port}" } }`, is approved, and a worktree is created
- **THEN** the new worktree has a reserved port before creation finishes

#### Scenario: Environment file on creation
- **WHEN** the repository declares `envFile`, is approved, and a worktree is created
- **THEN** the new worktree contains the environment file

#### Scenario: Writing fails during creation
- **WHEN** writing the environment file fails after the worktree was added
- **THEN** the worktree is removed, best-effort, and creation fails with the error

#### Scenario: Provisioning an existing worktree
- **WHEN** a worktree created by another tool is provisioned in an approved repository that declares `env` and `envFile`
- **THEN** its ports are reserved and the environment file is written

#### Scenario: Approved during creation
- **WHEN** the repository declares `PORT` and `envFile`, isn't approved, and the user approves it when asked during creation
- **THEN** the new worktree has a reserved port for `PORT` and the environment file

#### Scenario: Skipped during creation
- **WHEN** the repository declares `PORT` and `envFile`, isn't approved, and the user skips it
- **THEN** the worktree is created without a port for `PORT` nor an environment file, and the result names `PORT` as skipped

### Requirement: Run post-creation commands
After a worktree's files are in place and its environment prepared, the system SHALL run the post-creation commands in order, each as argv with the worktree as working directory, stopping at the first that fails, only when the repository is approved on this machine (see `repository-trust`); an unapproved repository follows the asking rules of the entry point, asked once for its commands and its declared variables before the environment is prepared. A failure SHALL report the command, its exit code and its error output, and SHALL NOT remove the worktree.

#### Scenario: Commands succeed
- **WHEN** the repository declares `[["npm", "install"]]`, the commands are approved and a worktree is created
- **THEN** `npm install` runs inside the new worktree after the declared files were copied, and creation succeeds

#### Scenario: Commands not approved
- **WHEN** the repository declares `[["npm", "install"]]`, the commands aren't approved and the user doesn't approve them
- **THEN** the worktree is created with its files and the environment of its providers, `npm install` doesn't run, and the result says the commands were skipped

#### Scenario: Command fails during creation
- **WHEN** the first of two approved post-creation commands exits with code 1 while creating a worktree
- **THEN** the second command doesn't run, creation fails with an error naming the command, its exit code and its error output, and the worktree and its branch are kept

#### Scenario: Command can't be started
- **WHEN** an approved post-creation command names a program that isn't installed
- **THEN** creation fails with an error naming the command, and the worktree is kept

#### Scenario: Provisioning an existing worktree
- **WHEN** a worktree created by another tool is provisioned in a repository that declares approved post-creation commands
- **THEN** the commands run inside that worktree after its files are copied, and a failure fails provisioning

#### Scenario: One question for commands and variables
- **WHEN** the repository declares commands and `env`, isn't approved, and a worktree is created on a terminal
- **THEN** the user is asked once, and the answer applies to both the commands and the variables
