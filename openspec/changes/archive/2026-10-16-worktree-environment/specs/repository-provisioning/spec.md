# Spec Delta

## ADDED Requirements

### Requirement: Prepare the environment when provisioning
Creating a worktree (on a new or an existing branch) and provisioning an existing worktree SHALL resolve the worktree's environment, reserving its ports, and write the environment file when one is declared. During creation, a failure to reserve ports or to write the file SHALL remove the new worktree, as a failed copy does; when provisioning, the worktree SHALL be left in place.

#### Scenario: Creation reserves ports
- **WHEN** the repository declares `{ "env": { "PORT": "${port}" } }` and a worktree is created
- **THEN** the new worktree has a reserved port before creation finishes

#### Scenario: Environment file on creation
- **WHEN** the repository declares `envFile` and a worktree is created
- **THEN** the new worktree contains the environment file

#### Scenario: Writing fails during creation
- **WHEN** writing the environment file fails after the worktree was added
- **THEN** the worktree is removed, best-effort, and creation fails with the error

#### Scenario: Provisioning an existing worktree
- **WHEN** a worktree created by another tool is provisioned in a repository that declares `env` and `envFile`
- **THEN** its ports are reserved and the environment file is written
