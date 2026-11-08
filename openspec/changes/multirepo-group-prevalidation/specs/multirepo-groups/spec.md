## ADDED Requirements

### Requirement: Check every repository before creating a group
Before creating any worktree of a group, ShogunAi SHALL check the local files of every repository that will get a new worktree, as a single `shogun new` checks them. When any of them has unavailable local files, ShogunAi SHALL create, fetch or provision nothing in any repository and exit with code 1. Repositories already on the branch SHALL NOT be checked, and a repository whose check can't run SHALL be left to its own creation.

#### Scenario: Second repository has an unavailable file
- **WHEN** the user runs `shogun new TASK-123 --repos app,api` and `api`'s `local.properties` is missing from its base folder
- **THEN** no worktree or branch is created in `app` or `api`, standard error names `api` with the missing file and how to fix it and says `app` wasn't created because of `api`, nothing is printed on standard output, and the command exits with code 1

#### Scenario: Several repositories have unavailable files
- **WHEN** `app`'s `keys.properties` is a broken link and `api`'s `local.properties` is missing
- **THEN** both repositories are reported as failed, each with its own files, and nothing is created

#### Scenario: Repository already in the group
- **WHEN** `app` already has a worktree on `feature/TASK-123`, `api` has an unavailable file and the user runs `shogun new TASK-123 --repos app,api,docs`
- **THEN** `app` is reported as already in the group, `api` as failed, `docs` as skipped, and nothing is created

#### Scenario: Unavailable file in a repository already in the group
- **WHEN** `app` already has a worktree on `feature/TASK-123` and an unavailable local file, and the user runs `shogun new TASK-123 --repos app,api`
- **THEN** `app` is reported as already in the group and `api`'s worktree is created

#### Scenario: Fetch requested
- **WHEN** the user runs `shogun new TASK-123 --fetch` and a repository has an unavailable local file
- **THEN** no repository is fetched

#### Scenario: Every file is available
- **WHEN** every repository's local files are available
- **THEN** the group is created as before

### Requirement: Report a group stopped by unavailable local files
When the check stops a group, ShogunAi SHALL report each repository with unavailable local files as failed with the error a single `shogun new` gives for them, every other repository that would have been created as skipped because of those repositories, and the repositories already on the branch as already in the group.

#### Scenario: Command-line output
- **WHEN** `shogun new TASK-123 --repos app,api` is stopped by `api`'s missing `local.properties`
- **THEN** standard error has `api`'s local files error and a line saying `app` wasn't created because the local files of `api` are unavailable

## MODIFIED Requirements

### Requirement: JSON result of group creation
`shogun new <id> --json` creating or extending a group SHALL print one JSON object with the `branch` and a `repos` list with each repository's `alias`, `path`, `result` (`created`, `existing`, `failed` or `skipped`), the post-creation commands it skipped and, for a failure or a skipped repository, its error.

#### Scenario: Mixed result
- **WHEN** `shogun new TASK-123 --json` creates `app`, finds `api` already on the branch and fails on `docs`
- **THEN** the object has `branch` `feature/TASK-123` and lists `app` as `created`, `api` as `existing` with its path and `docs` as `failed` with its error, and the command exits with code 1

#### Scenario: Group stopped by unavailable local files
- **WHEN** `shogun new TASK-123 --repos app,api --json` finds a missing local file in `api`
- **THEN** the object lists `api` as `failed` with its local files error and `app` as `skipped` with an error naming `api`, neither with a path, and the command exits with code 1
