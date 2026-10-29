## MODIFIED Requirements

### Requirement: Reuse the worktree's Herdr workspace
When Herdr already has the worktree open, the "Open in Herdr" action SHALL NOT open another workspace. When a pane of that workspace has a live agent (Herdr reports an agent status other than `unknown`), it SHALL focus that agent's pane and SHALL NOT type anything into any pane. When no pane has a live agent and the workspace has a single pane, in the worktree, whose shell Herdr reports at its prompt with no other process in the foreground, it SHALL run the agent launch command in that pane. In any other case without a live agent (several panes, a pane in another folder, a pane running a program, or Herdr unable to report the pane's processes) it SHALL add a pane to that workspace in the worktree, with the same environment and agent launch command as a fresh open. It SHALL NOT close any existing pane.

#### Scenario: Already open
- **WHEN** the user opens in Herdr a worktree Herdr already has open and one of its panes runs `claude`
- **THEN** that agent's pane is focused, no workspace or pane is created and nothing is typed into its panes

#### Scenario: Already open without an agent
- **WHEN** the user opens in Herdr a worktree Herdr already has open with a single pane in the worktree, after quitting the agent that ran in it, and that pane's shell is back at its prompt
- **THEN** no workspace or pane is created and the agent launch command runs in that pane

#### Scenario: Pane running a program
- **WHEN** the workspace's only pane has no live agent but runs a program in the foreground, e.g. a dev server
- **THEN** a new pane opens in that workspace in the worktree with every variable `shogun env` prints for it, the agent launch command runs in the new pane, and the existing pane is kept

#### Scenario: Several panes
- **WHEN** the workspace has two or more panes and none has a live agent
- **THEN** a new pane opens in that workspace in the worktree with every variable `shogun env` prints for it, the agent launch command runs in the new pane, and the existing panes are kept

#### Scenario: Pane in another folder
- **WHEN** the workspace's only pane has no live agent and its shell is in a folder other than the worktree
- **THEN** a new pane opens in that workspace in the worktree with the worktree's environment and runs the agent launch command, and the existing pane is kept

#### Scenario: Processes unknown
- **WHEN** Herdr can't report what runs in the workspace's only pane, e.g. because its version lacks that information
- **THEN** a new pane opens in that workspace as when the pane runs a program, and the open doesn't fail because of it

#### Scenario: Agent finished its turn
- **WHEN** the only agent of the open workspace reports `done` or `idle`
- **THEN** it counts as a live agent and its pane is focused

### Requirement: Open a worktree in Herdr from the command line
`shogun open <target> --herdr`, or `shogun open <target>` without a runner flag when Herdr is the runner in use, SHALL open the worktree in Herdr with the same workspace, environment and agent command as the desktop app's "Open in Herdr", SHALL reuse an existing workspace as that action does (focusing a live agent, or starting one in the workspace's idle pane or in a new pane when there's none), and SHALL report on standard error, with exit code 1, that Herdr isn't installed, that its command failed, or that the agent didn't start.

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
- **THEN** the second run starts the agent again in that same pane, the workspace still has a single pane, and the output names that pane
