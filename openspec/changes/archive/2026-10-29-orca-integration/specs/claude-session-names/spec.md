## MODIFIED Requirements

### Requirement: Name the Claude sessions ShogunAi launches
Whenever ShogunAi types an agent launch command into a Herdr pane, or starts one in an Orca terminal, and that command runs Claude Code, it SHALL add `-n <name>` to it: `<alias>-<id>` for a worktree of a group, where `<id>` is the group's branch without its prefix; `<project>-<id>` for another worktree, where `<id>` is its folder name; `<project>` for a project's main worktree; and the multirepo's name for the multirepo agent.

#### Scenario: Group worktrees
- **WHEN** the user runs `shogun open TASK-123 --herdr` in a multirepo folder and `feature/TASK-123` is a group with `app` and `api`
- **THEN** `app`'s pane runs Claude with `-n app-task-123` and `api`'s pane with `-n api-task-123`

#### Scenario: Worktree of a single repository
- **WHEN** the user runs `shogun open TASK-123 --herdr` in project `main-repo` whose worktree folder is `TASK-123`
- **THEN** the pane runs Claude with `-n main-repo-task-123`

#### Scenario: Main worktree
- **WHEN** the target is the main worktree of project `main-repo`
- **THEN** the pane runs Claude with `-n main-repo`

#### Scenario: Multirepo agent
- **WHEN** the user runs `shogun open --herdr` in multirepo `product` whose agent is `claude`
- **THEN** the pane runs Claude with `-n product`

#### Scenario: Started agent in an open workspace
- **WHEN** the worktree's workspace is open without a live agent and ShogunAi starts Claude in a new pane
- **THEN** that command also gets `-n` with the worktree's name

#### Scenario: Desktop app
- **WHEN** the person uses "Open in Herdr" on a worktree whose agent is Claude
- **THEN** the session is named as from the command line

#### Scenario: Opened in Orca
- **WHEN** the user runs `shogun open TASK-123 --orca` in project `main-repo` whose worktree folder is `TASK-123`, or uses "Open in Orca" on it
- **THEN** the Orca terminal runs Claude with `-n main-repo-task-123`

## ADDED Requirements

### Requirement: Report the session name when opening in Orca
After starting a named Claude command in Orca, `shogun open --orca` SHALL wait up to 10 seconds for a live session with that name in the worktree's folder, and SHALL report it in its text line and as `session_name` in its `--json` result. When it only focuses a live agent, it SHALL report the session name as `shogun open --herdr` does for a focused agent. Without a confirmed name, `session_name` SHALL be `null` and the open SHALL still succeed.

#### Scenario: Opened
- **WHEN** the user runs `shogun open TASK-123 --orca --json` in project `main-repo` and the session appears as `main-repo-task-123`
- **THEN** the result has `session_name` `main-repo-task-123`

#### Scenario: Focused agent
- **WHEN** the worktree's live agent in Orca is focused and the registry has a live session named `main-repo-task-123` in its folder
- **THEN** the result has `orca` `focused` and `session_name` `main-repo-task-123`

#### Scenario: Name never confirmed
- **WHEN** the agent starts but no session with that name appears within 10 seconds
- **THEN** the open succeeds with `session_name` `null`
