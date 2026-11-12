## MODIFIED Requirements

### Requirement: Reuse the worktree's Herdr workspace
When Herdr already has the worktree open, the "Open in Herdr" action SHALL NOT open another workspace and SHALL NOT close any existing pane. When a pane of that workspace has a live agent (Herdr reports an agent status other than `unknown`), it SHALL focus that agent's pane and SHALL NOT type anything into any pane.

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

### Requirement: Close the Herdr workspace of a removed worktree
Every ShogunAi removal path (the desktop app's remove action and "Clean up", `shogun remove`, `shogun clean --yes` and `shogun hook worktree-remove`) SHALL close the Herdr workspace of each worktree it actually removes, looking the workspace up before removing and closing it only after the removal succeeds.

#### Scenario: Worktree open in Herdr
- **WHEN** the user removes a worktree that Herdr has open
- **THEN** the worktree is removed and its Herdr workspace is closed

#### Scenario: Cleanup with several worktrees open in Herdr
- **WHEN** `shogun clean --yes` or the desktop "Clean up" removes two worktrees open in Herdr and prunes a missing one that Herdr also has open
- **THEN** the three Herdr workspaces are closed

#### Scenario: Removal fails
- **WHEN** removing a worktree open in Herdr fails, e.g. because it has uncommitted changes and isn't forced
- **THEN** its Herdr workspace stays open

#### Scenario: Claude Code removes the worktree
- **WHEN** `shogun hook worktree-remove` removes a worktree that Herdr has open
- **THEN** its Herdr workspace is closed

#### Scenario: Folder locked by Herdr's shells
- **WHEN** removing a worktree that Herdr has open fails because the folder can't be deleted (permission denied, busy or used by another process)
- **THEN** its Herdr workspace is closed, the removal is retried once and succeeds, and the workspace isn't closed a second time

#### Scenario: Panes in another workspace
- **WHEN** the removal is blocked by the folder being locked and a Herdr pane outside the worktree's workspace, such as one in the repository's workspace, has its directory inside the worktree
- **THEN** that pane is closed individually before the retry and the other panes of its workspace stay open

#### Scenario: Locked without Herdr
- **WHEN** the folder is locked but Herdr isn't installed, isn't running or has nothing open in the worktree
- **THEN** nothing is closed and the removal fails with the folder-in-use error

#### Scenario: Another failure
- **WHEN** the removal fails for a reason other than a locked folder
- **THEN** no Herdr workspace or pane is closed

### Requirement: Open a worktree in Herdr from the command line
`shogun open <target> --herdr`, or `shogun open <target>` without a runner flag when Herdr is the runner in use, SHALL open the worktree in Herdr with the same workspace, environment and agent command as the desktop app's "Open in Herdr", SHALL reuse an existing workspace as that action does (focusing a live agent, or starting one in the workspace's idle pane or in a new pane when there's none).

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

## ADDED Requirements

### Requirement: Start the agent in the open workspace's idle pane
When the worktree's open Herdr workspace has no live agent and a single pane, in the worktree, whose shell Herdr reports at its prompt with no other process in the foreground, the "Open in Herdr" action SHALL run the agent launch command in that pane.

#### Scenario: Idle pane reused
- **WHEN** the user opens in Herdr a worktree Herdr already has open with a single pane in the worktree, after quitting the agent that ran in it, and that pane's shell is back at its prompt
- **THEN** no workspace or pane is created and the agent launch command runs in that pane

### Requirement: Add a pane to the open workspace
When the worktree's open Herdr workspace has no live agent and no idle pane to reuse, the "Open in Herdr" action SHALL add a pane to that workspace in the worktree, with the same environment and agent launch command as a fresh open.

#### Scenario: Program in the foreground
- **WHEN** the workspace's only pane has no live agent but runs a program in the foreground, e.g. a dev server
- **THEN** a new pane opens in that workspace in the worktree with every variable `shogun env` prints for it, the agent launch command runs in the new pane, and the existing pane is kept

#### Scenario: Two or more panes
- **WHEN** the workspace has two or more panes and none has a live agent
- **THEN** a new pane opens in that workspace in the worktree with every variable `shogun env` prints for it, the agent launch command runs in the new pane, and the existing panes are kept

#### Scenario: Only pane in another folder
- **WHEN** the workspace's only pane has no live agent and its shell is in a folder other than the worktree
- **THEN** a new pane opens in that workspace in the worktree with the worktree's environment and runs the agent launch command, and the existing pane is kept

#### Scenario: Pane processes unknown
- **WHEN** Herdr can't report what runs in the workspace's only pane, e.g. because its version lacks that information
- **THEN** a new pane opens in that workspace as when the pane runs a program, and the open doesn't fail because of it

### Requirement: Close Herdr's shells that lock a worktree's folder
When Git can't complete a removal because the worktree's folder is locked by a process (for example, on Windows, a shell Herdr has open in it), ShogunAi SHALL close the worktree's Herdr workspace and every Herdr pane whose directory is inside the worktree, and retry the removal once. It SHALL NOT close anything for any other failure.

#### Scenario: Locked by Herdr's shells
- **WHEN** removing a worktree that Herdr has open fails because the folder can't be deleted (permission denied, busy or used by another process)
- **THEN** its Herdr workspace is closed, the removal is retried once and succeeds, and the workspace isn't closed a second time

#### Scenario: Pane of another workspace inside the worktree
- **WHEN** the removal is blocked by the folder being locked and a Herdr pane outside the worktree's workspace, such as one in the repository's workspace, has its directory inside the worktree
- **THEN** that pane is closed individually before the retry and the other panes of its workspace stay open

#### Scenario: Failure other than a locked folder
- **WHEN** the removal fails for a reason other than a locked folder
- **THEN** no Herdr workspace or pane is closed

### Requirement: Report why opening in Herdr failed
`shogun open --herdr`, and `shogun open` when Herdr is the runner in use, SHALL report on standard error, with exit code 1, that Herdr isn't installed, that its command failed, or that the agent didn't start.

#### Scenario: Not installed
- **WHEN** the `herdr` command can't be found
- **THEN** the error says Herdr isn't installed, names herdr.dev, and the command exits with code 1

#### Scenario: Herdr's command fails
- **WHEN** the `herdr` command that opens the workspace exits with an error
- **THEN** the error says Herdr's command failed and the command exits with code 1

#### Scenario: Agent didn't start
- **WHEN** the workspace opens but the agent doesn't start in it
- **THEN** the error says the agent didn't start and the command exits with code 1
