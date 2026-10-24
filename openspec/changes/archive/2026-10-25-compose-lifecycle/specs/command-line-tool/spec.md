## ADDED Requirements

### Requirement: Start and build Compose projects from the command line
`shogun compose up [<worktree>] [--build] [-- <args>…]` SHALL start the services of the given worktree, or of the worktree containing the current directory, and `shogun compose build [<worktree>] [-- <args>…]` SHALL build them, both with the worktree's environment, passing the arguments after `--` to Docker Compose. Docker Compose's output SHALL be shown as it runs, and the command SHALL exit with Docker Compose's failure as code 1.

#### Scenario: Up in the current worktree
- **WHEN** the user runs `shogun compose up` inside worktree `TASK-123` of `main-repo`
- **THEN** the services start under project `main-repo-task-123` with the worktree's ports, and the output names the project

#### Scenario: Up with a build and a service
- **WHEN** the user runs `shogun compose up TASK-123 --build -- api`
- **THEN** `docker compose up --detach --build api` runs in that worktree with its environment

#### Scenario: Build
- **WHEN** the user runs `shogun compose build`
- **THEN** the services of the current worktree are built with its environment

#### Scenario: Unknown worktree
- **WHEN** the user runs `shogun compose up TASK-404` and no such worktree exists
- **THEN** an error on standard error says so, Docker isn't run and the command exits with code 1

#### Scenario: Failure
- **WHEN** Docker Compose fails
- **THEN** the reason is printed on standard error and the command exits with code 1

#### Scenario: JSON
- **WHEN** the user runs `shogun compose up --json`
- **THEN** standard output has one object with the worktree, the project name and whether it succeeded, and Docker Compose's own output goes to standard error

### Requirement: Remove leftover Docker resources from the command line
`shogun compose prune [--volumes] [--yes]` SHALL remove the networks and built images left by the projects of removed worktrees, and their volumes with `--volumes`. Without `--yes` it SHALL only list what it would remove and remove nothing. It SHALL report each resource removed and each failure with the reason, and exit with code 1 when any removal fails.

#### Scenario: Dry run
- **WHEN** the user runs `shogun compose prune` and a removed worktree left a network, an image and a volume
- **THEN** the output lists the network and the image as to be removed, the volume as kept, and nothing is removed

#### Scenario: Remove
- **WHEN** the user runs `shogun compose prune --yes`
- **THEN** the network and the image are removed, the volume is kept and the output says so

#### Scenario: With volumes
- **WHEN** the user runs `shogun compose prune --yes --volumes`
- **THEN** the volume is removed too

#### Scenario: Nothing left
- **WHEN** no removed worktree left any resource
- **THEN** the output says there is nothing to remove and the command exits with code 0

#### Scenario: JSON
- **WHEN** the user runs `shogun compose prune --yes --json`
- **THEN** the object lists the removed resources with their kind, name and project, and the failures with their reason

## MODIFIED Requirements

### Requirement: List and take down Compose projects from the command line
`shogun compose` SHALL print one line per Compose project of the repository's worktrees with its status and worktree, followed by one line per leftover volume, network or image of removed worktrees with its kind and project. `shogun compose down` SHALL take down the projects of removed worktrees, and `shogun compose down <worktree>` the project of the given worktree, both keeping volumes unless `--volumes` is given. It SHALL report what was taken down and what failed with the reason, and exit with code 1 when the given worktree doesn't exist or the repository doesn't declare the provider.

#### Scenario: List
- **WHEN** the user runs `shogun compose` and two projects exist
- **THEN** each has a line with its status and worktree

#### Scenario: Leftover resources listed
- **WHEN** the user runs `shogun compose` and a removed worktree left the volume `main-repo-task-9_db-data`
- **THEN** a line names that volume, its kind and its project, and says `shogun compose prune --volumes` removes it

#### Scenario: Take down orphans
- **WHEN** the user runs `shogun compose down` and the project of a removed worktree exists
- **THEN** that project is taken down, volumes are kept and the output says so

#### Scenario: Remove volumes
- **WHEN** the user runs `shogun compose down --volumes`
- **THEN** the volumes of the projects taken down are removed too

#### Scenario: Provider not declared
- **WHEN** the repository doesn't declare the Compose provider
- **THEN** an error on standard error says so, Docker isn't run and the command exits with code 1

### Requirement: Take down Compose projects when cleaning up from the command line
With `--yes`, `shogun clean --down-compose-projects` SHALL also take down the Compose projects of the removed worktrees and remove the networks and built images those projects leave, keeping their volumes unless `--compose-volumes` is also given, and report how many projects were taken down, which resources were removed and what failed. Without the option no project SHALL be taken down and no resource removed.

#### Scenario: Projects of removed worktrees
- **WHEN** the user runs `shogun clean --yes --down-compose-projects` and a removed worktree had a project with a built image and a volume
- **THEN** that project is taken down, its image is removed, its volume is kept and the output says so

#### Scenario: Volumes too
- **WHEN** the user runs `shogun clean --yes --down-compose-projects --compose-volumes`
- **THEN** the volumes of the removed worktrees' projects are removed too

#### Scenario: Volumes option alone
- **WHEN** the user runs `shogun clean --yes --compose-volumes` without `--down-compose-projects`
- **THEN** a usage error says `--compose-volumes` needs `--down-compose-projects` and nothing is removed

#### Scenario: Without the option
- **WHEN** the user runs `shogun clean --yes`
- **THEN** no Compose project is taken down and no Docker resource is removed

#### Scenario: Option without confirmation
- **WHEN** the user runs `shogun clean --down-compose-projects` without `--yes`
- **THEN** nothing is removed or taken down, and the output also lists the projects that would be taken down
