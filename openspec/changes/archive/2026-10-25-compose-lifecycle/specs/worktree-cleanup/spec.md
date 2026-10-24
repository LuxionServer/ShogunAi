## MODIFIED Requirements

### Requirement: Take down the Compose projects of removed worktrees
When the repository declares the Docker Compose provider, the cleanup dialog SHALL offer an option, checked by default, to also take down the Compose projects of the worktrees it removes and remove the networks and built images they leave, and a second option, unchecked by default and available only with the first, to remove their volumes too. They run after the removals, and a project or resource that can't be removed SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the Docker Compose provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also take down their Compose projects" option and an unchecked "also remove their volumes" option

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the provider
- **THEN** the dialog doesn't show the options and Docker isn't run

#### Scenario: Projects taken down
- **WHEN** the first option is checked and a removed worktree had a Compose project with a built image and a volume
- **THEN** that project is taken down, its image is removed, its volume is kept and the report says how many projects were taken down and resources removed

#### Scenario: Volumes removed
- **WHEN** both options are checked
- **THEN** the volumes of the removed worktrees' projects are removed too

#### Scenario: Volumes option depends on the first
- **WHEN** the user unchecks "also take down their Compose projects"
- **THEN** "also remove their volumes" is unchecked and disabled

#### Scenario: Docker unavailable
- **WHEN** Docker isn't reachable when the cleanup runs
- **THEN** the worktrees are still reported as removed and the report says the projects were kept
