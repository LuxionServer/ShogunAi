# Spec Delta

## ADDED Requirements

### Requirement: Orca's computer use takes the desktop turn
Before Claude Code runs a shell command that drives the desktop through Orca's computer use, `shogun hook desktop-turn` SHALL give the desktop turn to the session's worktree, or renew it when that worktree already holds it, and let the command run. A command drives the desktop when it runs `orca computer` (or `orca-ide computer`) with any command other than `capabilities`, `permissions`, `list-apps`, `list-windows` and `get-app-state`, or `get-app-state` with `--restore-window`.

#### Scenario: Desktop free
- **WHEN** nobody holds the desktop turn and Claude in `TASK-1` is about to run `orca computer click --app com.example.app --element-index 4 --json`
- **THEN** `TASK-1` holds the turn and the command runs

#### Scenario: Holder renews
- **WHEN** `TASK-1` holds the turn until 14:10, the duration is 10 minutes and its agent runs `orca computer type-text` at 14:05
- **THEN** its turn lasts until 14:15 and the command runs

#### Scenario: Absolute path in a compound command
- **WHEN** the agent runs `cd /tmp && /opt/homebrew/bin/orca computer hotkey --app com.example.app --key CmdOrCtrl+A`
- **THEN** the hook takes the turn before the command runs

#### Scenario: Window brought to the front
- **WHEN** the agent runs `orca computer get-app-state --app com.example.app --restore-window --json`
- **THEN** the hook takes the turn before the command runs

### Requirement: Orca's computer use waits for another holder
When the desktop turn is held by another worktree, or other worktrees wait for it, `shogun hook desktop-turn` SHALL block the command and tell the agent who holds the turn and until when, how many worktrees wait, and to take its turn with `shogun desktop acquire --wait` run in the background before running the command again. Nothing SHALL change in the turn or the queue.

#### Scenario: Taken
- **WHEN** `api` · `TASK-1` holds the turn until 14:32 and Claude in `TASK-2` is about to run `orca computer click`
- **THEN** the command doesn't run, and the agent is told that `api` · `TASK-1` holds the desktop until 14:32 and to run `shogun desktop acquire --wait` in the background first

#### Scenario: Others waiting
- **WHEN** nobody holds the turn but `TASK-2` waits for it, and Claude in `TASK-3` is about to run `orca computer press-key`
- **THEN** the command doesn't run, the agent is told one worktree is waiting, and `TASK-2` stays first in line

### Requirement: The desktop-turn hook leaves other commands alone
`shogun hook desktop-turn` SHALL print nothing and change nothing for tools other than the shell, for commands that don't drive the desktop through Orca's computer use, and for sessions outside a Git repository. When it can't read the turn or its input, it SHALL let the command run, print nothing and exit with code 0.

#### Scenario: Read-only command
- **WHEN** another worktree holds the turn and the agent runs `orca computer get-app-state --app com.example.app --json`
- **THEN** the command runs and the turn is unchanged

#### Scenario: Unrelated command
- **WHEN** the agent runs `echo computer science`
- **THEN** the hook prints nothing and the turn is unchanged

#### Scenario: Outside a repository
- **WHEN** a session outside any Git repository runs `orca computer click`
- **THEN** the command runs and nobody holds the turn

#### Scenario: Unreadable turn
- **WHEN** the turn's file can't be read and the agent runs `orca computer click`
- **THEN** the command runs and the hook exits with code 0
