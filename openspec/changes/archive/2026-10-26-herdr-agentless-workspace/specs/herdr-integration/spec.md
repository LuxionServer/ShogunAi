## MODIFIED Requirements

### Requirement: Reuse the worktree's Herdr workspace
When Herdr already has the worktree open, the "Open in Herdr" action SHALL NOT open another workspace. When a pane of that workspace has a live agent (Herdr reports an agent status other than `unknown`), it SHALL focus that agent's pane and SHALL NOT type anything into any pane. When no pane has a live agent, it SHALL add a pane to that workspace in the worktree, with the same environment and agent launch command as a fresh open, and SHALL leave the other panes as they are.

#### Scenario: Already open
- **WHEN** the user opens in Herdr a worktree Herdr already has open and one of its panes runs `claude`
- **THEN** that agent's pane is focused, no workspace or pane is created and nothing is typed into its panes

#### Scenario: Already open without an agent
- **WHEN** the user opens in Herdr a worktree Herdr already has open, after quitting the agent that ran in it
- **THEN** no new workspace is created, a new pane opens in that workspace in the worktree with every variable `shogun env` prints for it, the agent launch command runs in that pane, and the existing panes are kept

#### Scenario: Agent finished its turn
- **WHEN** the only agent of the open workspace reports `done` or `idle`
- **THEN** it counts as a live agent and its pane is focused

### Requirement: Open a worktree in Herdr from the command line
`shogun open <target> --herdr` SHALL open the worktree in Herdr with the same workspace, environment and agent command as the desktop app's "Open in Herdr", SHALL reuse an existing workspace as that action does (focusing a live agent, or starting one in a new pane when there's none), and SHALL report on standard error, with exit code 1, that Herdr isn't installed, that its command failed, or that the agent didn't start.

#### Scenario: Worktree with an environment
- **WHEN** the user runs `shogun open TASK-123 --herdr` and the repository declares `env`
- **THEN** the workspace's shell has every variable `shogun env` prints for that worktree and the agent command runs with them

#### Scenario: Herdr not installed
- **WHEN** the `herdr` command can't be found
- **THEN** the error says Herdr isn't installed, names herdr.dev, and the command exits with code 1

#### Scenario: Workspace left without an agent
- **WHEN** the user runs `shogun new TASK-123`, then `shogun open TASK-123 --herdr`, quits the agent inside its pane, and runs `shogun open TASK-123 --herdr` again
- **THEN** the second run starts the agent in a new pane of the same workspace instead of only focusing it

## ADDED Requirements

### Requirement: Verify the agent started in Herdr
Every time ShogunAi types an agent launch command into a Herdr pane, it SHALL wait up to 10 seconds for Herdr to report an agent status other than `unknown` in that pane. When the time runs out, the open SHALL fail with an error naming the pane and the command, and the pane SHALL be left open.

#### Scenario: Agent detected
- **WHEN** a fresh open types `claude` into pane `w1:p1` and Herdr reports `idle` for it two seconds later
- **THEN** the open succeeds

#### Scenario: Agent never detected
- **WHEN** Herdr still reports `unknown` for the pane 10 seconds after the command was typed
- **THEN** the open fails with an error saying the agent didn't start in that pane, naming the command, and the pane stays open

#### Scenario: Already open with an agent
- **WHEN** the open only focuses a live agent
- **THEN** nothing is typed and nothing is waited for

### Requirement: Report the Herdr outcome in the desktop app
The desktop app SHALL tell apart, after "Open in Herdr", that the worktree was opened with its agent, that its agent was started in its already open workspace, and that its live agent was focused, in English and Spanish, and SHALL show an agent that didn't start as an error naming the pane and the command.

#### Scenario: Agent started in an open workspace
- **WHEN** "Open in Herdr" starts the agent in the worktree's already open workspace
- **THEN** the confirmation says the agent was started in the existing workspace, not that it was already open

#### Scenario: Agent didn't start
- **WHEN** Herdr doesn't detect the agent in time
- **THEN** the worktree shows the error with the pane and the command as technical details
