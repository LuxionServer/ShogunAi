## MODIFIED Requirements

### Requirement: Open a worktree in Orca from the command line
`shogun open <target> --orca`, or `shogun open <target>` without a runner flag when Orca is the runner in use, SHALL open the worktree in Orca exactly as the desktop app's "Open in Orca" does. It SHALL report on standard error, with exit code 1, that Orca isn't installed, that it didn't start, that its command failed, or that the agent didn't start. `--json` SHALL report whether the agent was started in a new terminal or a live one was focused.

#### Scenario: Worktree with an environment
- **WHEN** the user runs `shogun open TASK-123 --orca` and the repository declares `env`
- **THEN** the terminal's shell has every variable `shogun env` prints for that worktree, and the agent command runs with them

#### Scenario: Orca in use without a flag
- **WHEN** Orca is the runner in use and the user runs `shogun open TASK-123`
- **THEN** the worktree opens in Orca as with `shogun open TASK-123 --orca`

#### Scenario: Orca not installed
- **WHEN** the `orca` command can't be found
- **THEN** the error says Orca isn't installed, names onorca.dev, and the command exits with code 1

#### Scenario: Both targets given
- **WHEN** the user passes both `--herdr` and `--orca`
- **THEN** the command fails with a usage error and opens nothing
