## MODIFIED Requirements

### Requirement: Rules for shared resources and cleanup
The `resources` and `cleanup` topics SHALL tell agents that each worktree uses its own emulator and simulator and stops them when done, that a worktree's Compose services are started and built with `shogun compose up` and `shogun compose build` and taken down with `shogun compose down <worktree>`, that `shogun gradle stop` and `shogun xcode clean` are run showing what they free and asking first, and that `shogun clean --yes` is run only after asking.

#### Scenario: Cleaning
- **WHEN** the user runs `shogun guide cleanup`
- **THEN** the output says to ask before running `shogun clean --yes` and names the options that also stop emulators and Gradle daemons and delete simulators, DerivedData and branches

#### Scenario: Compose services
- **WHEN** the repository declares the docker-compose provider and the user runs `shogun guide resources`
- **THEN** the output says to start and build the worktree's services with `shogun compose up` and `shogun compose build`, not with a hand-written project name, and to take them down with `shogun compose down <worktree>`

#### Scenario: Compose rule in the session context
- **WHEN** a Claude Code session starts in a worktree of a repository that declares the docker-compose provider
- **THEN** the context says to start the worktree's services with `shogun compose up`
