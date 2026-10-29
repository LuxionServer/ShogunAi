## MODIFIED Requirements

### Requirement: Open a worktree in Herdr from the command line
`shogun open <target> --herdr`, or `shogun open <target>` without a runner flag when Herdr is the runner in use, SHALL open the worktree in Herdr with the same workspace, environment and agent command as the desktop app's "Open in Herdr", SHALL reuse an existing workspace as that action does (focusing a live agent, or starting one in a new pane when there's none), and SHALL report on standard error, with exit code 1, that Herdr isn't installed, that its command failed, or that the agent didn't start.

#### Scenario: Worktree with an environment
- **WHEN** the user runs `shogun open TASK-123 --herdr` and the repository declares `env`
- **THEN** the workspace's shell has every variable `shogun env` prints for that worktree and the agent command runs with them

#### Scenario: Herdr in use without a flag
- **WHEN** Herdr is the runner in use and the user runs `shogun open TASK-123`
- **THEN** the worktree opens in Herdr as with `shogun open TASK-123 --herdr`

#### Scenario: Herdr not installed
- **WHEN** the `herdr` command can't be found
- **THEN** the error says Herdr isn't installed, names herdr.dev, and the command exits with code 1

#### Scenario: Workspace left without an agent
- **WHEN** the user runs `shogun new TASK-123`, then `shogun open TASK-123 --herdr`, quits the agent inside its pane, and runs `shogun open TASK-123 --herdr` again
- **THEN** the second run starts the agent in a new pane of the same workspace instead of only focusing it
