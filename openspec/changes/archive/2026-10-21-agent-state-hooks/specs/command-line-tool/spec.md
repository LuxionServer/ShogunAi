# Spec Delta

## ADDED Requirements

### Requirement: Report each agent's state in status
`shogun status` SHALL show each agent's state whatever reports it, Herdr or Claude Code's hooks, and `shogun status --json` SHALL add `state` (`working`, `blocked`, `done`, `idle` or `null`) and `stateSource` (`herdr`, `claude-code` or `null`) to each agent, keeping `herdrState` as before.

#### Scenario: Agent state from Claude Code's hooks
- **WHEN** Claude is blocked in a worktree, as recorded by its `Notification` hook, and Herdr doesn't report it
- **THEN** the line shows `claude(blocked)` and the JSON agent has `state` `blocked`, `stateSource` `claude-code` and `herdrState` `null`

#### Scenario: Agent state from Herdr
- **WHEN** Herdr reports Claude as working
- **THEN** the JSON agent has `state` `working`, `stateSource` `herdr` and `herdrState` `working`

### Requirement: Record agent states through Claude Code's hooks
`shogun hook agent-state` SHALL read the hook's JSON from standard input and record the session's state for the worktree containing its `cwd`: working on `UserPromptSubmit`, blocked on `Notification`, done on `Stop`, and remove the record on `SessionEnd`. It SHALL print nothing, never block the session, and exit with code 0 whenever the input is valid, also outside a repository.

#### Scenario: Permission prompt
- **WHEN** Claude Code calls the hook with `hook_event_name` `Notification` from a worktree
- **THEN** the session is recorded as blocked for that worktree and nothing is printed

#### Scenario: Session ends
- **WHEN** Claude Code calls the hook with `SessionEnd`
- **THEN** the session's record is removed

#### Scenario: Outside a repository
- **WHEN** the hook's `cwd` isn't inside a Git repository
- **THEN** nothing is recorded and the command exits with code 0

#### Scenario: Invalid input
- **WHEN** standard input isn't the hook's JSON
- **THEN** the error is printed on standard error and the command exits with code 2

### Requirement: Session records stay out of the working tree
Session records SHALL be stored in the worktree's Git administrative folder, so they never appear as changes, aren't copied to other worktrees and disappear when the worktree is removed.

#### Scenario: Record written
- **WHEN** a session's state is recorded in a worktree
- **THEN** `git status` in that worktree shows no new file

### Requirement: Manage the hooks from the command line
`shogun hooks` SHALL offer `install`, `uninstall` and `status`, as specified in `claude-code-hooks`, with `--project` to target the repository's settings, `--yes` to skip confirmation and `--json` for machine-readable output.

#### Scenario: Help
- **WHEN** the user runs `shogun hooks --help`
- **THEN** the three subcommands and their options are described
