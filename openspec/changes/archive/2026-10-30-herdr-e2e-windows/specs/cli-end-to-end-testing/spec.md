# Spec Delta

## MODIFIED Requirements

### Requirement: End-to-end tests cover opening worktrees in the runners
The end-to-end tests SHALL cover opening a worktree in Herdr and in Orca with fake executables placed where the command looks for them, and SHALL check the commands those executables receive. On every operating system, Windows included, each fake SHALL have the name and kind of file the command runs there, so opening in Herdr and in Orca is not skipped on any of them.

#### Scenario: Opening in Herdr sends the worktree and its agent
- **WHEN** a test runs `shogun open TASK-1 --herdr` with a fake `herdr`
- **THEN** the fake receives a command to open the worktree's path and a command to run the project's agent in a pane, and `shogun` exits with 0

#### Scenario: Opening in Herdr runs on Windows
- **WHEN** the end-to-end tests run on Windows, where Herdr's client runs only `herdr.exe`
- **THEN** a fake `herdr.exe` comes first on the command's `PATH`, receives the same commands as the fake `herdr` on Linux and macOS, and the Herdr scenarios run instead of being skipped

#### Scenario: Opening in Orca creates the agent's terminal
- **WHEN** a test runs `shogun open TASK-1 --orca` with a fake `orca`
- **THEN** the fake receives a command to create a terminal in the worktree running the project's agent, and `shogun` exits with 0
