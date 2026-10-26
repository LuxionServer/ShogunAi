# docker-compose-provider Specification

## Purpose
Defines the Docker Compose provider: how each worktree gets its own Compose project and ports so parallel worktrees never share containers, networks, volumes or host ports, and how running projects are shown and cleaned up.

## Requirements

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
Without the `docker-compose` declaration, the system SHALL NOT run Docker, list Compose projects or their leftover resources, record project names, show anything Compose-related or add variables for Compose.

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare `"docker-compose"` in `providers`
- **THEN** Docker isn't run for it, no project name is recorded for it and its screens show nothing about Compose

### Requirement: Start the Compose project of a worktree
The system SHALL start a worktree's services on request by running `docker compose up --detach` in the worktree's folder with the worktree's resolved environment added to the process environment, so the project is named by its `COMPOSE_PROJECT_NAME` and publishes its reserved ports. Building first SHALL be optional. Extra Compose arguments given by the caller SHALL be passed after the system's own, as separate arguments.

#### Scenario: Start
- **WHEN** the user starts the services of worktree `TASK-123` of `main-repo`, whose `DB_PORT` is 20004
- **THEN** `docker compose up --detach` runs in that worktree with `COMPOSE_PROJECT_NAME=main-repo-task-123` and `DB_PORT=20004` in its environment

#### Scenario: Start with a build
- **WHEN** the user starts the services and asks to build them
- **THEN** `docker compose up --detach --build` runs with the worktree's environment

#### Scenario: Extra arguments
- **WHEN** the caller passes `api` and `--wait` as extra arguments
- **THEN** they reach Docker Compose as the arguments `api` and `--wait`, never parsed by a shell

#### Scenario: Failure
- **WHEN** Docker Compose exits with an error
- **THEN** the result says it failed with the last line Docker Compose printed on standard error

### Requirement: Build the Compose project of a worktree
The system SHALL build a worktree's services on request by running `docker compose build` in the worktree's folder with the worktree's resolved environment, passing the caller's extra arguments after its own, and SHALL report a failure with the last line Docker Compose printed on standard error.

#### Scenario: Build
- **WHEN** the user builds the services of worktree `TASK-123`
- **THEN** `docker compose build` runs in that worktree with `COMPOSE_PROJECT_NAME` and the port variables of that worktree

#### Scenario: Build failure
- **WHEN** the build of a service fails
- **THEN** the result says it failed and why

### Requirement: Remember the project names given to worktrees
The system SHALL record, per repository, the Compose project name of each worktree together with the worktree's path whenever it resolves that worktree's environment with reservation or starts or builds its services, in a file under the user's ShogunAi folder outside every repository. An entry SHALL be dropped only once no Docker resource of its project is left and its worktree no longer exists.

#### Scenario: Recorded on creation
- **WHEN** a worktree `TASK-123` of `main-repo` is created in a repository that declares the provider
- **THEN** the registry holds `main-repo-task-123` for that worktree's path

#### Scenario: Dropped after removal
- **WHEN** worktree `TASK-9` was removed and every resource of `main-repo-task-9` has been removed
- **THEN** the registry no longer holds `main-repo-task-9`

#### Scenario: Unreadable registry
- **WHEN** the registry file is missing or can't be parsed
- **THEN** it is treated as empty, the operation goes on, and the next write replaces it with valid content

### Requirement: Find the leftover Docker resources of removed worktrees
When the Docker CLI is available, the system SHALL list the volumes, networks and images whose `com.docker.compose.project` label is a project name recorded for the repository whose worktree no longer exists and isn't the project name of an existing worktree, each with its kind, name and project. Images SHALL be listed only when they carry that label, which only images built by Compose do.

#### Scenario: Volume of a removed worktree
- **WHEN** the volume `main-repo-task-9_db-data` has the label of project `main-repo-task-9`, recorded for a worktree that no longer exists
- **THEN** it is listed as a leftover volume of `main-repo-task-9`

#### Scenario: Built image
- **WHEN** the image `main-repo-task-9-api` has the label of that project
- **THEN** it is listed as a leftover image

#### Scenario: Pulled image
- **WHEN** the project used `postgres:17`, which has no Compose label
- **THEN** that image isn't listed

#### Scenario: Project never recorded
- **WHEN** a volume's project starts with the repository's name but isn't recorded for the repository
- **THEN** it isn't listed

#### Scenario: Worktree recreated with the same folder
- **WHEN** a project recorded for a removed worktree is the project name of an existing worktree
- **THEN** its resources aren't listed as leftovers

### Requirement: Remove the leftover Docker resources of removed worktrees
The system SHALL remove on request the networks and images listed as leftovers of removed worktrees, and their volumes only when the caller asks for volumes explicitly. It SHALL never force a removal, so an image or network still used by a container is kept and reported with the reason, and one failure SHALL NOT stop the rest.

#### Scenario: Default removal
- **WHEN** the user removes the leftovers of `main-repo-task-9`, which has a network, a built image and a volume
- **THEN** the network and the image are removed and the volume is kept

#### Scenario: Volumes too
- **WHEN** the user removes the leftovers and asks for volumes
- **THEN** the volume is removed too

#### Scenario: Image in use
- **WHEN** a leftover image is used by a container
- **THEN** it is kept, the result says why, and the other resources are still removed

### Requirement: Compose section in the worktree detail
When the repository declares the provider, the detail of a worktree whose folder exists SHALL show its Compose project's name and state, `not started` when Docker knows no such project, with Start, Build and Take down actions. While one of them runs, the section SHALL show progress and ignore further actions; when it ends, the state SHALL be read again and a failure shown with its reason. When Docker isn't available the section SHALL say so and offer no action.

#### Scenario: Not started
- **WHEN** the selected worktree has no Compose project known to Docker
- **THEN** the section shows its project name, `not started`, and the Start and Build actions enabled

#### Scenario: Start from the detail
- **WHEN** the user activates Start
- **THEN** the worktree's services start with its environment, the section shows progress until it ends, and then shows the new state

#### Scenario: Double click
- **WHEN** the user activates Start twice while the first start runs
- **THEN** only one start is sent

#### Scenario: Docker unavailable
- **WHEN** Docker isn't reachable
- **THEN** the section says Docker isn't available and offers no action

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the provider
- **THEN** the detail has no Compose section and Docker isn't run
