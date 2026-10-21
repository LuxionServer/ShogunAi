# Spec Delta

## ADDED Requirements

### Requirement: Show the detected stacks from the command line
`shogun stacks` SHALL print one line per stack detected in the repository with its evidence and, per suggested provider, whether the repository already declares it. Providers not supported by the platform SHALL NOT be suggested. It SHALL change nothing and exit with code 0 when no stack is detected.

#### Scenario: Stacks detected
- **WHEN** the user runs `shogun stacks` in a repository with `pom.xml` that declares no provider
- **THEN** the output lists Maven with `pom.xml` and the suggested provider `maven`

#### Scenario: Already declared
- **WHEN** the repository already declares the `maven` provider
- **THEN** the output shows it as declared and not as a suggestion

#### Scenario: Nothing detected
- **WHEN** no stack is detected
- **THEN** the command says so and exits with code 0

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun stacks` outside any Git repository
- **THEN** an error on standard error says so and the command exits with code 1

### Requirement: List Maven daemons from the command line
`shogun maven` SHALL print one line per Maven daemon with its process id, memory and, inside a Git repository, its worktree, followed by the total memory. It SHALL work outside a Git repository.

#### Scenario: Daemons running
- **WHEN** the user runs `shogun maven` while two Maven daemons run
- **THEN** each has a line with its memory and worktree, and the total memory is printed

#### Scenario: No daemons
- **WHEN** no Maven daemon is running
- **THEN** the command says so and exits with code 0

### Requirement: List and take down Compose projects from the command line
`shogun compose` SHALL print one line per Compose project of the repository's worktrees with its status and worktree. `shogun compose down` SHALL take down the projects of removed worktrees, and `shogun compose down <worktree>` the project of the given worktree, both keeping volumes unless `--volumes` is given. It SHALL report what was taken down and what failed with the reason, and exit with code 1 when the given worktree doesn't exist or the repository doesn't declare the provider.

#### Scenario: List
- **WHEN** the user runs `shogun compose` and two projects exist
- **THEN** each has a line with its status and worktree

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
With `--yes`, `shogun clean --down-compose-projects` SHALL also take down the Compose projects of the removed worktrees, keeping their volumes, and report how many were taken down and how many failed. Without the option no project SHALL be taken down.

#### Scenario: Projects of removed worktrees
- **WHEN** the user runs `shogun clean --yes --down-compose-projects` and a removed worktree had a project
- **THEN** that project is taken down and the output says so

#### Scenario: Without the option
- **WHEN** the user runs `shogun clean --yes`
- **THEN** no Compose project is taken down

#### Scenario: Option without confirmation
- **WHEN** the user runs `shogun clean --down-compose-projects` without `--yes`
- **THEN** nothing is removed or taken down, and the output also lists the projects that would be taken down
