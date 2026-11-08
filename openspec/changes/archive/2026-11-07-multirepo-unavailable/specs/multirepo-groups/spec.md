# Spec Delta

## MODIFIED Requirements

### Requirement: Register an existing multirepo
`shogun multirepo init [--yes]` in a folder that already has a declaration SHALL validate it, review and approve it as `shogun multirepo trust` does, and then add it to the known multirepos and register a project for each declared repository that has none, as `add-repo` does; it SHALL leave the declaration unchanged. `--worktrees-root` and `--name` SHALL be rejected there. A repository another known multirepo declares SHALL make it fail without changes.

#### Scenario: Folder moved
- **WHEN** `~/projects/product` moved to `~/work/product` with its repositories side by side, and the user runs `shogun multirepo init` in `~/work/product`
- **THEN** the command shows the review and, when the user answers yes, the multirepo `~/work/product` is known and approved, the entry for `~/projects/product` stays known, unchanged and ignored, each declared repository without a project at its new path gets one, and the command says it approved and registered an existing multirepo

#### Scenario: No terminal
- **WHEN** the declaration isn't approved and the command runs without a terminal, or with `--json`, and without `--yes`
- **THEN** it says to pass `--yes`, registers nothing and exits with code 1

#### Scenario: Review declined
- **WHEN** the user answers no to the review
- **THEN** nothing is registered or approved and the command exits with code 0

#### Scenario: Already known
- **WHEN** the folder's declaration is already known and approved and every repository has a project
- **THEN** nothing changes and the command says the multirepo is already registered, exiting with code 0

#### Scenario: Invalid declaration
- **WHEN** the existing declaration is invalid, for example a repository path that doesn't exist
- **THEN** the command names the problem and the file, registers nothing and exits with code 1

#### Scenario: Options for a new declaration
- **WHEN** the user runs `shogun multirepo init --name Product` in a folder that already has a declaration
- **THEN** the command says to use `shogun multirepo name` or `worktrees-root` instead, changes nothing and exits with code 2

#### Scenario: Repository claimed by another multirepo
- **WHEN** a declared repository is also declared by another known multirepo whose declaration can be read
- **THEN** the command names the other multirepo, registers nothing and exits with code 1

## ADDED Requirements

### Requirement: Commands never forget a multirepo on their own
No `shogun` command SHALL remove an entry, its approval or its local agent from `~/.shogunai/multirepos.json` because its folder or declaration can't be found; only `shogun multirepo delete`, `shogun multirepo trust --revoke` and the app's explicit removal forget a multirepo.

#### Scenario: Volume unmounted
- **WHEN** `~/.shogunai/multirepos.json` lists `/Volumes/External/main-multirepo`, `/Volumes/External` isn't mounted, and the user runs `shogun multirepo init` anywhere
- **THEN** `~/.shogunai/multirepos.json` still lists it with its approval and local agent

#### Scenario: Volume mounted again
- **WHEN** the volume is mounted again and the user runs a command inside one of its repositories
- **THEN** the command finds the multirepo, still approved, without registering it again

## REMOVED Requirements

### Requirement: Forget multirepos whose folder is gone
**Reason**: A folder that doesn't exist at that moment may be on an unmounted volume or a network drive that is down; forgetting it dropped its approval and local agent.
**Migration**: Entries stay and are ignored while their declaration can't be read; the app lists them as unavailable and removes them on request.
