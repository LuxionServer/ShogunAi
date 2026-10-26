## MODIFIED Requirements

### Requirement: Open a worktree in Herdr from the command line
`shogun open <target> --herdr` SHALL open the worktree identified by a task id, a path or a branch name in Herdr, as the desktop app's "Open in Herdr" does, including the main worktree, running the project's default agent or the one named by `--agent <name>` (ignoring case). With `--json` it SHALL print `path`, `branch`, `herdr` (`opened`, `started-agent` or `focused`), `pane_id` (the pane of the agent) and `agent` (the agent started, or the one Herdr reports in the focused pane).

#### Scenario: Open by task id
- **WHEN** the user runs `shogun open TASK-123 --herdr` and Herdr doesn't have the worktree open
- **THEN** the worktree opens in Herdr with the project's default agent, the output names the path, the agent and its pane, and the command exits with code 0

#### Scenario: Already open
- **WHEN** the user runs `shogun open TASK-123 --herdr` and Herdr already has the worktree open with a live agent
- **THEN** that agent's pane is focused, no agent is started, the output says it was already open and names the pane, and the command exits with code 0

#### Scenario: Open without an agent
- **WHEN** the user runs `shogun open TASK-123 --herdr` and Herdr has the worktree open but no pane of it has a live agent
- **THEN** the agent is started in a new pane of that workspace, the output says the agent was started in the existing workspace and names the agent and the pane, and the command exits with code 0

#### Scenario: Agent doesn't start
- **WHEN** Herdr doesn't detect the agent in the pane within the time limit
- **THEN** the error says the agent didn't start, names the pane and the command, and the command exits with code 1

#### Scenario: Another agent
- **WHEN** the project has an additional agent named `Codex` and the user runs `shogun open TASK-123 --herdr --agent codex`
- **THEN** that agent's launch command runs in the new workspace

#### Scenario: Unknown agent
- **WHEN** the user runs `shogun open TASK-123 --herdr --agent missing`
- **THEN** nothing is opened, the error lists the project's agents, and the command exits with code 2

#### Scenario: Main worktree
- **WHEN** the target is the main worktree
- **THEN** it opens in Herdr like any other worktree

#### Scenario: No destination
- **WHEN** the user runs `shogun open TASK-123`
- **THEN** nothing is opened, the error says `--herdr` is needed, and the command exits with code 2

#### Scenario: Unknown target
- **WHEN** the target matches no worktree
- **THEN** nothing is opened and the command exits with code 1

#### Scenario: Result as JSON
- **WHEN** the user runs `shogun open TASK-123 --herdr --json` and the worktree opens
- **THEN** standard output is one line with an object whose `herdr` is `opened`, `pane_id` is the agent's pane and `agent` is the agent's name

#### Scenario: Started agent as JSON
- **WHEN** the user runs `shogun open TASK-123 --herdr --json` and the agent is started in the existing workspace
- **THEN** the object's `herdr` is `started-agent`, with the new pane's `pane_id` and the agent's name

#### Scenario: Focused as JSON
- **WHEN** the user runs `shogun open TASK-123 --herdr --json` and a live `claude` agent is focused
- **THEN** the object's `herdr` is `focused`, `pane_id` is that agent's pane and `agent` is `claude`
