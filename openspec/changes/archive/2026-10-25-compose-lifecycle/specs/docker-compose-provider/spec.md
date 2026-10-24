## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Provider only for repositories that declare it
Without the `docker-compose` declaration, the system SHALL NOT run Docker, list Compose projects or their leftover resources, record project names, show anything Compose-related or add variables for Compose.

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare `"docker-compose"` in `providers`
- **THEN** Docker isn't run for it, no project name is recorded for it and its screens show nothing about Compose
