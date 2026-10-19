# Spec Delta

## MODIFIED Requirements

### Requirement: Run post-creation commands
After a worktree's files are in place and its environment prepared, the system SHALL run the post-creation commands in order, each as argv with the worktree as working directory, stopping at the first that fails, only when they are approved for the repository on this machine (see `repository-trust`); unapproved commands follow the asking rules of the entry point. A failure SHALL report the command, its exit code and its error output, and SHALL NOT remove the worktree.

#### Scenario: Commands succeed
- **WHEN** the repository declares `[["npm", "install"]]`, the commands are approved and a worktree is created
- **THEN** `npm install` runs inside the new worktree after the declared files were copied, and creation succeeds

#### Scenario: Commands not approved
- **WHEN** the repository declares `[["npm", "install"]]`, the commands aren't approved and the user doesn't approve them
- **THEN** the worktree is created with its files and environment, `npm install` doesn't run, and the result says the commands were skipped

#### Scenario: Command fails during creation
- **WHEN** the first of two approved post-creation commands exits with code 1 while creating a worktree
- **THEN** the second command doesn't run, creation fails with an error naming the command, its exit code and its error output, and the worktree and its branch are kept

#### Scenario: Command can't be started
- **WHEN** an approved post-creation command names a program that isn't installed
- **THEN** creation fails with an error naming the command, and the worktree is kept

#### Scenario: Provisioning an existing worktree
- **WHEN** a worktree created by another tool is provisioned in a repository that declares approved post-creation commands
- **THEN** the commands run inside that worktree after its files are copied, and a failure fails provisioning

## ADDED Requirements

### Requirement: Provisioned files stay inside the worktree
Before copying or linking a file into a worktree, the system SHALL resolve the real location of its destination's parent folder, following symbolic links, and SHALL refuse the file when that location isn't inside the worktree's real folder. A refused file SHALL fail the operation with an error naming the file, and SHALL NOT replace or delete anything at the outside location.

#### Scenario: Symlinked folder on the branch
- **WHEN** the base repository has the ignored file `config/.env`, matching a `copy` pattern, and the new worktree's branch tracks `config` as a symbolic link to a folder outside the worktree
- **THEN** `config/.env` isn't copied, nothing is written in the outside folder, and the operation fails with an error naming `config/.env`

#### Scenario: Regular folder
- **WHEN** the destination's parent is a regular folder inside the worktree
- **THEN** the file is copied or linked as before
