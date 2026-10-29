# worktree-overview Specification

## Purpose
Gives the user a full picture of a project's worktrees, whoever created them: where each one comes from, which AI agents are working in it, how much disk it uses and whether it has been abandoned.

## Requirements

### Requirement: Show where each worktree comes from
The system SHALL classify every non-main worktree by origin and show it next to the folder name in its row and in its detail: created by Claude Code when its path is inside a `.claude/worktrees/` folder, created by Herdr when it is inside Herdr's worktrees folder, created by Orca when Orca created it, created by ShogunAi when it is inside the project's worktrees folder, and "other location" otherwise. The main worktree has no origin label.

#### Scenario: Claude Code worktree
- **WHEN** a worktree's path is inside `<repository>/.claude/worktrees/`
- **THEN** its row and detail show it was created by Claude Code, even if that folder is also inside the project's worktrees folder

#### Scenario: Herdr worktree
- **WHEN** a worktree's path is inside Herdr's worktrees folder, the `directory` of the `[worktrees]` table in Herdr's `config.toml` or `~/.herdr/worktrees` when Herdr's configuration doesn't set it
- **THEN** its row and detail show it was created by Herdr

#### Scenario: Herdr configuration unreadable
- **WHEN** Herdr's configuration file is missing or can't be parsed
- **THEN** `~/.herdr/worktrees` is used as Herdr's worktrees folder and the classification of other worktrees is unaffected

#### Scenario: Orca worktree
- **WHEN** Orca lists a worktree among the ones it created, not among the external worktrees it shows
- **THEN** its row and detail show it was created by Orca

#### Scenario: Orca not running
- **WHEN** Orca can't be asked and a worktree's path is inside the folder where Orca last reported creating worktrees, or `~/orca/workspaces` when it never reported one
- **THEN** its row and detail show it was created by Orca

#### Scenario: ShogunAi worktree opened in Orca
- **WHEN** a worktree inside the project's worktrees folder, created by `shogun new`, is shown in Orca because ShogunAi opened it there
- **THEN** its row and detail still show it was created by ShogunAi

#### Scenario: ShogunAi worktree
- **WHEN** a worktree's path is inside the project's worktrees folder and not inside `.claude/worktrees/` or Herdr's worktrees folder, and Orca didn't create it
- **THEN** its row and detail show it was created by ShogunAi

#### Scenario: Other location
- **WHEN** a worktree's path is neither inside `.claude/worktrees/`, Herdr's worktrees folder nor the project's worktrees folder, and Orca didn't create it
- **THEN** its row and detail show it is in another location

### Requirement: Show the AI agents running in each worktree
The system SHALL detect the AI agent processes whose working directory is inside each worktree. A worktree's row SHALL show an "agent running" indicator, and its detail SHALL name each running agent. A process belongs to the worktree with the longest path that contains its working directory, so a process in a nested worktree isn't attributed to the main one, and a process whose working directory is inside another Git repository nested in the worktree's folder isn't attributed to the worktree. Recognized agents are Claude, Codex, Gemini, Cursor agent, OpenCode, Aider, Copilot and every agent command of the project. When processes can't be inspected on the platform, no agent information is shown, except the agents Herdr reports.

#### Scenario: Agent running in a worktree
- **WHEN** a `claude` process has its working directory inside a worktree
- **THEN** that worktree's row shows the agent running indicator and its detail shows Claude as running

#### Scenario: Agent in a nested worktree
- **WHEN** a `claude` process has its working directory inside `<repository>/.claude/worktrees/task`, which is a worktree nested in the main worktree's folder
- **THEN** Claude is shown on the nested worktree and not on the main one

#### Scenario: Agent in a nested repository
- **WHEN** a `claude` process has its working directory inside `~/projects/product/product-docs`, a separate Git repository inside the folder of the repository `~/projects/product`
- **THEN** no agent is shown on `~/projects/product`'s main worktree

#### Scenario: Non-agent processes
- **WHEN** only shells, editors or build tools have their working directory inside a worktree
- **THEN** its row and detail show no running agent

#### Scenario: Process inspection unavailable
- **WHEN** listing processes fails, for example because `lsof` isn't installed
- **THEN** no row or detail shows agent information and the rest of the list works normally

#### Scenario: Second agent of the project
- **WHEN** a project's second agent runs the command `antigravity` and an `antigravity` process works inside one of its worktrees
- **THEN** that worktree shows the agent as running

### Requirement: Show each worktree's disk usage
The system SHALL compute each worktree's disk usage in the background, without blocking the list or its statuses, and show it in human-readable units in its detail. Folders of other worktrees nested inside a worktree SHALL NOT be counted in it. The project summary SHALL show the total of the sizes computed so far.

#### Scenario: Size shown
- **WHEN** a worktree's files add up to 1,288,490,189 bytes
- **THEN** its detail shows "1.2 GB"

#### Scenario: Nested worktrees excluded
- **WHEN** the main worktree's folder contains another worktree under `.claude/worktrees/`
- **THEN** the main worktree's size doesn't include the nested worktree's files

#### Scenario: Missing folder
- **WHEN** a worktree's folder no longer exists
- **THEN** no size is shown for it

### Requirement: Flag inactive worktrees
The system SHALL flag a non-main worktree as inactive, in its row and its detail, when it has commits of its own, no uncommitted changes, no AI agent running in it, and its activity date is older than the inactivity threshold. The threshold SHALL default to 3 days. A worktree without commits of its own SHALL NOT be flagged as inactive.

#### Scenario: Abandoned worktree
- **WHEN** a clean worktree with no running agent had its last commit of its own 5 days ago, with the default threshold
- **THEN** its row and detail show it as inactive

#### Scenario: Recent work
- **WHEN** a clean worktree with no running agent had its last commit of its own 2 days ago, with the default threshold
- **THEN** it isn't flagged as inactive

#### Scenario: Agent running or pending changes
- **WHEN** a worktree whose last commit of its own is 20 days old has uncommitted changes or an agent running in it
- **THEN** it isn't flagged as inactive

#### Scenario: Old base commit in a new worktree
- **WHEN** a worktree was created today from a branch whose last commit is 30 days old, and has no commits of its own
- **THEN** it isn't flagged as inactive and is shown as new

### Requirement: Reload the overview with the list
The system SHALL detect running agents every time the worktree list is reloaded. A manual reload SHALL compute every listed worktree's disk usage again; any other reload SHALL compute it only for worktrees whose size isn't known yet. Sizes already computed SHALL stay shown until they are computed again.

#### Scenario: Reload
- **WHEN** the user reloads the worktree list
- **THEN** agents and sizes are read again for the listed worktrees, and each size stays shown until its new value is computed

#### Scenario: Automatic reload
- **WHEN** a project's list is reloaded because its Git metadata changed and one new worktree appeared
- **THEN** agents are read again and only the new worktree's size is computed

### Requirement: Show each worktree's reserved ports
The system SHALL show in each worktree's detail the ports already reserved for it, each with the variable or key it belongs to. Showing them SHALL NOT reserve ports; a worktree without reservations SHALL show nothing.

#### Scenario: Ports reserved
- **WHEN** a worktree has `PORT` reserved as 20001 and the key `api` as 20002
- **THEN** its detail shows `PORT 20001` and `api 20002`

#### Scenario: Nothing reserved yet
- **WHEN** a worktree created by another tool has never had its environment resolved
- **THEN** its detail shows no ports and none are reserved by opening the view

### Requirement: Show and control each worktree's emulator
For a repository that declares the emulator provider, each worktree's detail SHALL show the state of its emulator (stopped, booting or running), with a Start action when stopped and a Stop action otherwise, and refresh the state with the view. Its row SHALL show a device indicator only while the emulator is booting or running. Showing the detail SHALL NOT reserve a port or start anything.

#### Scenario: Running emulator
- **WHEN** a worktree's `emulator-5556` is online and booted
- **THEN** its row shows the device indicator and its detail shows the emulator as running with a Stop action

#### Scenario: Start from the row
- **WHEN** the user activates Start in a worktree's detail for a stopped emulator
- **THEN** the emulator starts, and the detail and the row show it booting, then running

#### Scenario: Port not reserved yet
- **WHEN** a worktree has never had its environment resolved
- **THEN** its detail shows a Start action, and starting reserves the port first

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the emulator provider
- **THEN** neither rows nor details show emulator information

### Requirement: Show and control each worktree's iOS simulator
For a repository that declares the iOS simulator provider, on macOS, each worktree's detail SHALL show its simulator's state (shut down, booting or booted), with Start when shut down and Stop otherwise, refreshed with the view. Its row SHALL show a device indicator only while the simulator is booting or booted. Showing the detail SHALL NOT create a simulator.

#### Scenario: Booted simulator
- **WHEN** a worktree's simulator is booted
- **THEN** its row shows the device indicator and its detail shows it as booted with a Stop action

#### Scenario: No simulator yet
- **WHEN** a worktree created by another tool never had its environment resolved
- **THEN** its detail shows a Start action, and starting creates and boots its simulator

### Requirement: Show the state Herdr reports for each agent
When Herdr is running, the system SHALL read the agents Herdr tracks and attribute each to the worktree with the longest path containing the agent's current directory. Each agent shown in a row or detail SHALL carry the state Herdr reports: working, blocked, done or idle. An agent Herdr reports SHALL count as running even when the process list doesn't show it. When Herdr isn't installed or running, agents SHALL be shown as before, without a state.

#### Scenario: Blocked agent
- **WHEN** Herdr reports a `claude` agent as blocked with its current directory inside a worktree
- **THEN** the worktree's row and detail show Claude as blocked

#### Scenario: Several panes of the same agent
- **WHEN** Herdr reports two `claude` agents in the same worktree, one working and one blocked
- **THEN** the worktree shows Claude as blocked

#### Scenario: Herdr not running
- **WHEN** Herdr is installed but its server isn't running
- **THEN** agents found in the process list are shown without a state and nothing reports an error

#### Scenario: Agent only Herdr sees
- **WHEN** the process list can't be read and Herdr reports an agent in a worktree
- **THEN** that worktree shows the agent with its state, and cleanup keeps the worktree

### Requirement: Configure the inactivity threshold
The app's settings SHALL let the user set the inactivity threshold as a whole number of days from 1 to 90, and SHALL remember it across restarts. A change SHALL apply to every view without reloading the worktrees.

#### Scenario: Longer threshold
- **WHEN** the user sets the threshold to 7 days and a clean worktree's last commit of its own was 5 days ago
- **THEN** the worktree is no longer flagged as inactive, and it moves out of "Inactive" without a reload

#### Scenario: Out of range
- **WHEN** the user enters 0 or 120 days
- **THEN** the value isn't accepted and the setting keeps its previous value

### Requirement: Show the state Claude Code's hooks report
When a Claude process runs in a worktree and a live session of that worktree has a recorded state, the agent SHALL carry that state. A record SHALL be live while its session's Claude process runs. A blocked or done record whose session's transcript was written after it SHALL read as working. When Herdr or Orca reports the same agent, the state they report SHALL be used.

#### Scenario: Blocked outside Herdr
- **WHEN** Claude runs in a worktree outside Herdr and Orca and its session was recorded as blocked
- **THEN** the worktree's row and detail show Claude as blocked, and the worktree is under "Needs you"

#### Scenario: Permission granted
- **WHEN** a session recorded as blocked writes to its transcript afterwards
- **THEN** Claude is shown as working

#### Scenario: Stale record
- **WHEN** a worktree has a record of a session whose Claude process no longer runs
- **THEN** the record is ignored, and no agent is shown unless a process, Herdr or Orca reports one

#### Scenario: Herdr also reports it
- **WHEN** Herdr reports Claude as working and the hook record says done
- **THEN** Claude is shown as working

#### Scenario: Orca also reports it
- **WHEN** Orca reports Claude as waiting and the hook record says working
- **THEN** Claude is shown as blocked

### Requirement: Show the state Orca reports for each agent
When Orca is running, the system SHALL read the agents Orca reports for each worktree, matched to ShogunAi's worktrees by path. Each agent SHALL carry Orca's state: working as working, waiting as blocked, done as done. An agent Orca reports SHALL count as running even when the process list doesn't show it. When Orca isn't installed or running, agents SHALL be shown without an Orca state.

#### Scenario: Waiting agent
- **WHEN** Orca reports a `claude` agent as waiting in a worktree
- **THEN** the worktree's row and detail show Claude as blocked, and the worktree is under "Needs you"

#### Scenario: Several agents in the worktree
- **WHEN** Orca reports two `claude` agents in the same worktree, one working and one waiting
- **THEN** the worktree shows Claude as blocked

#### Scenario: Agent only Orca sees
- **WHEN** the process list can't be read and Orca reports an agent in a worktree
- **THEN** that worktree shows the agent with its state, and cleanup keeps the worktree

#### Scenario: Orca not running
- **WHEN** Orca is installed but its app isn't running
- **THEN** agents found in the process list are shown without an Orca state, and nothing reports an error

### Requirement: Carry the details Orca reports for each agent
Each agent whose state Orca reports SHALL also carry, when Orca reports them: the tool it waits on and that tool's input, only while Orca reports it as waiting; the prompt it was last given; and its last message. Blank values SHALL count as missing. When several agents of the same name in a worktree are reported by Orca, the details SHALL be those of the one whose state is shown. Agents Orca doesn't report SHALL carry no details.

#### Scenario: Waiting on a tool
- **WHEN** Orca reports Claude as waiting, with tool `Bash`, input `curl -s https://example.com -o page.html` and prompt `Download the page`
- **THEN** Claude is blocked, waiting on `Bash` with that input, with that prompt and no last message

#### Scenario: Done after using a tool
- **WHEN** Orca reports Claude as done, with tool `Bash`, prompt `Save the date` and last message `Saved it in now.txt`
- **THEN** Claude is done with that prompt and last message, and isn't waiting on any tool

#### Scenario: Several agents of the same name
- **WHEN** Orca reports two `claude` agents in a worktree, one working with prompt `A` and one waiting on `Bash` with prompt `B`
- **THEN** the worktree's Claude is blocked, waiting on `Bash` with prompt `B`

#### Scenario: Agent Orca doesn't report
- **WHEN** Claude runs in a worktree that Orca doesn't list
- **THEN** Claude carries no tool, prompt or last message
