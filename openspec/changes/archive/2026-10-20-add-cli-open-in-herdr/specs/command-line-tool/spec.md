## ADDED Requirements

### Requirement: Open a worktree in Herdr from the command line
`shogun open <target> --herdr` SHALL open the worktree identified by a task id, a path or a branch name in Herdr, as the desktop app's "Open in Herdr" does, including the main worktree, running the project's default agent or the one named by `--agent <name>` (ignoring case). With `--json` it SHALL print `path`, `branch`, `herdr` (`opened` or `focused`) and `agent` (`null` when only focused).

#### Scenario: Open by task id
- **WHEN** the user runs `shogun open TASK-123 --herdr` and Herdr doesn't have the worktree open
- **THEN** the worktree opens in Herdr with the project's default agent, the output names the path and the agent, and the command exits with code 0

#### Scenario: Already open
- **WHEN** the user runs `shogun open TASK-123 --herdr` and Herdr already has the worktree open
- **THEN** its workspace is focused, no agent is started, the output says it was already open, and the command exits with code 0

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
- **THEN** standard output is one line with an object whose `herdr` is `opened` and `agent` is the agent's name

## MODIFIED Requirements

### Requirement: Machine-readable output for every result command
`shogun new`, `provision`, `trust`, `clean`, `gradle`, `gradle stop`, `xcode`, `xcode clean`, `emulator`, `simulator`, `remove`, `repair` and `open` SHALL accept `--json`. With it, a command SHALL print one compact JSON object on a single line on standard output instead of its text, with the same exit code. Every documented field SHALL be present, `null` when it doesn't apply, and later versions SHALL only add fields.

#### Scenario: Emulator status as JSON
- **WHEN** the user runs `shogun emulator status --json` in a worktree whose emulator runs as `emulator-5556`
- **THEN** standard output is one line with an object whose `serial` is `emulator-5556` and `state` is `running`, and the command exits with code 0

#### Scenario: Unknown option on a command without JSON
- **WHEN** the user runs `shogun hook session-start --json`
- **THEN** it prints that the option is unknown and exits with code 2
