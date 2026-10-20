## ADDED Requirements

### Requirement: Open a worktree in Herdr from the command line
`shogun open <target> --herdr` SHALL open the worktree in Herdr with the same workspace, environment and agent command as the desktop app's "Open in Herdr", SHALL focus the existing workspace without typing into it when Herdr already has the worktree open, and SHALL report on standard error, with exit code 1, that Herdr isn't installed or that its command failed.

#### Scenario: Worktree with an environment
- **WHEN** the user runs `shogun open TASK-123 --herdr` and the repository declares `env`
- **THEN** the workspace's shell has every variable `shogun env` prints for that worktree and the agent command runs with them

#### Scenario: Herdr not installed
- **WHEN** the `herdr` command can't be found
- **THEN** the error says Herdr isn't installed, names herdr.dev, and the command exits with code 1
