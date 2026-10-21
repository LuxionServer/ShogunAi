# Spec Delta

## Purpose

Defines the Docker Compose provider: how each worktree gets its own Compose project and ports so parallel worktrees never share containers, networks, volumes or host ports, and how running projects are shown and cleaned up.

## ADDED Requirements

### Requirement: Isolate the Compose project of each worktree
When the repository declares the `docker-compose` provider, the system SHALL add `COMPOSE_PROJECT_NAME` to every worktree's environment, made of the repository folder's name and the worktree folder's name, lowercased, with every character other than letters, digits, `-` and `_` replaced by `-`, and not starting with `-` or `_`. Two worktrees SHALL NEVER get the same name. A variable with the same name declared in `env` SHALL take precedence.

#### Scenario: Project name
- **WHEN** the repository folder is `main-repo` and the worktree folder is `TASK-123`
- **THEN** the worktree's environment has `COMPOSE_PROJECT_NAME=main-repo-task-123`

#### Scenario: Unsupported characters
- **WHEN** the worktree folder is `fix login (2)`
- **THEN** the project name contains only letters, digits, `-` and `_`

#### Scenario: Names that would collide
- **WHEN** two worktrees' folders differ only in case or in characters replaced by `-`
- **THEN** their project names still differ

#### Scenario: Declared variable wins
- **WHEN** `env` declares `COMPOSE_PROJECT_NAME`
- **THEN** the worktree's environment has the declared value

### Requirement: Reserve ports for Compose port variables
The `docker-compose` provider SHALL accept a `portVariables` option, a list of environment variable names. Each SHALL be added to every worktree's environment with a port reserved for that variable under the rules of variable ports, so a Compose file can publish `${NAME}` without colliding with another worktree. Names SHALL follow the variable name rule and SHALL NOT be reserved names.

#### Scenario: Ports for the database and the API
- **WHEN** the repository declares `"docker-compose": { "portVariables": ["DB_PORT", "API_PORT"] }`
- **THEN** every worktree's environment has `DB_PORT` and `API_PORT` with two different ports in 20000-29999, unique among worktrees

#### Scenario: Stable ports
- **WHEN** a worktree's environment is resolved again
- **THEN** it gets the same ports as before

#### Scenario: Invalid name
- **WHEN** `portVariables` contains `1DB` or `PATH`
- **THEN** the file is reported as invalid with an error that names the variable

#### Scenario: Name also declared in env
- **WHEN** `DB_PORT` is both in `portVariables` and in `env`
- **THEN** the declared `env` value is delivered and no port is reserved for it by the provider

### Requirement: List the Compose projects of the worktrees
The system SHALL list the Compose projects known to Docker whose name is the project name of one of the repository's worktrees, each with its status and the worktree it belongs to, and the projects whose compose files live inside the repository or its worktrees folder but that match no existing worktree as belonging to a removed worktree. It SHALL work only when the Docker CLI is available.

#### Scenario: Project of a worktree
- **WHEN** the project `main-repo-task-123` is running and `task-123` is a worktree of `main-repo`
- **THEN** the project is listed as belonging to that worktree

#### Scenario: Project of a removed worktree
- **WHEN** the project `main-repo-task-9` exists, its compose file was in the repository's worktrees folder and no worktree of the repository is named `task-9`
- **THEN** it's listed as belonging to a removed worktree

#### Scenario: Unrelated project
- **WHEN** a Compose project's files are outside the repository and its worktrees folder and its name is no worktree's
- **THEN** it isn't listed

#### Scenario: Docker unavailable
- **WHEN** the Docker CLI isn't installed or its daemon isn't reachable
- **THEN** the list is reported as unavailable with the reason and nothing else is affected

### Requirement: Take down the Compose project of a worktree
The system SHALL take a project down on request with `docker compose -p <name> down`, which removes its containers and networks and keeps its volumes. Volumes SHALL be removed only when the user asks for it explicitly. A project that can't be taken down SHALL be reported with the reason without affecting the rest.

#### Scenario: Take down
- **WHEN** the user takes down `main-repo-task-9`
- **THEN** its containers and networks are removed and its volumes are kept

#### Scenario: Remove volumes
- **WHEN** the user takes down a project and chooses to remove its volumes
- **THEN** its volumes are removed too

#### Scenario: Failure
- **WHEN** taking a project down fails
- **THEN** the result says why and other projects are still processed

### Requirement: Provider only for repositories that declare it
Without the `docker-compose` declaration, the system SHALL NOT run Docker, list Compose projects, show anything Compose-related or add variables for Compose.

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare `"docker-compose"` in `providers`
- **THEN** Docker isn't run for it and its screens show nothing about Compose
