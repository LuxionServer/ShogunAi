# Spec Delta

## ADDED Requirements

### Requirement: Claude's computer use takes the desktop turn
Before Claude Code runs a computer-use tool that acts on the desktop, under the local (`mcp__computer-use__`) or linked-machine (`mcp__remote-devices__computer_`) prefix, `shogun hook desktop-turn` SHALL give the desktop turn to the session's worktree, or renew it when that worktree already holds it, and let the tool run. Acting tools use the mouse, the keyboard, scrolling, opening an application, writing the clipboard or a batch of actions.

#### Scenario: Desktop free
- **WHEN** nobody holds the desktop turn and Claude in `TASK-1` is about to run `mcp__computer-use__left_click`
- **THEN** `TASK-1` holds the turn and the click runs

#### Scenario: Holder renews
- **WHEN** `TASK-1` holds the turn until 14:10, the duration is 10 minutes and its agent runs `mcp__computer-use__type` at 14:05
- **THEN** its turn lasts until 14:15 and the tool runs

#### Scenario: Linked machine
- **WHEN** nobody holds the turn and Claude in `TASK-1` is about to run `mcp__remote-devices__computer_key`
- **THEN** `TASK-1` holds the turn and the tool runs

### Requirement: Claude's computer use waits for another holder
When the desktop turn is held by another worktree, or other worktrees wait for it, `shogun hook desktop-turn` SHALL deny a computer-use tool that acts on the desktop, and tell the agent who holds the turn and until when, how many worktrees wait, and to take its turn with `shogun desktop acquire --wait` run in the background before trying again. Nothing SHALL change in the turn or the queue.

#### Scenario: Taken
- **WHEN** `api` · `TASK-1` holds the turn until 14:32 and Claude in `TASK-2` is about to run `mcp__computer-use__left_click`
- **THEN** the click doesn't run, and the agent is told that `api` · `TASK-1` holds the desktop until 14:32 and to run `shogun desktop acquire --wait` in the background first

#### Scenario: Others waiting
- **WHEN** nobody holds the turn but `TASK-2` waits for it, and Claude in `TASK-3` is about to run `mcp__computer-use__scroll`
- **THEN** the tool doesn't run, the agent is told one worktree is waiting, and `TASK-2` stays first in line

## MODIFIED Requirements

### Requirement: The desktop-turn hook leaves other commands alone
`shogun hook desktop-turn` SHALL print nothing and change nothing for tools other than the shell and the computer-use tools that act on the desktop, for shell commands that don't drive the desktop through Orca's computer use, and for sessions outside a Git repository. Computer-use tools that only look, such as screenshots, zoom, the cursor position, waiting, switching the display, reading the clipboard, listing applications and requesting access, SHALL run without the turn. When it can't read the turn or its input, it SHALL let the tool run, print nothing and exit with code 0.

#### Scenario: Read-only command
- **WHEN** another worktree holds the turn and the agent runs `orca computer get-app-state --app com.example.app --json`
- **THEN** the command runs and the turn is unchanged

#### Scenario: Screenshot while the desktop is taken
- **WHEN** another worktree holds the turn and Claude runs `mcp__computer-use__screenshot`
- **THEN** the screenshot runs, the hook prints nothing and the turn is unchanged

#### Scenario: Unrelated command
- **WHEN** the agent runs `echo computer science`
- **THEN** the hook prints nothing and the turn is unchanged

#### Scenario: Outside a repository
- **WHEN** a session outside any Git repository runs `orca computer click`
- **THEN** the command runs and nobody holds the turn

#### Scenario: Unreadable turn
- **WHEN** the turn's file can't be read and the agent runs `orca computer click`
- **THEN** the command runs and the hook exits with code 0
