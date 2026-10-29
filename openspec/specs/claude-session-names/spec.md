# claude-session-names Specification

## Purpose
Lets a person or a coordinating agent tell apart the Claude Code sessions ShogunAi launches, by naming each session after the worktree, group repository or multirepo it runs in, and reporting those names in `shogun open --herdr` and `shogun status`.

## Requirements

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

### Requirement: Only a plain Claude command gets a name
ShogunAi SHALL add `-n` only when the launch command runs Claude Code directly or through `headroom wrap`, optionally after environment assignments, and is a single command. A command that runs another agent, already passes `-n` or `--name`, or chains several commands SHALL be typed unchanged, and the open SHALL report no session name for it.

#### Scenario: Another agent
- **WHEN** the agent command is `codex`
- **THEN** the typed command is `codex`, with no `-n`

#### Scenario: Wrapped Claude
- **WHEN** the project uses Headroom and the agent command is `claude --model opus`
- **THEN** the typed command is `headroom wrap claude --model opus -n <name>`

#### Scenario: Claude by path
- **WHEN** the agent command is `/opt/tools/claude`
- **THEN** the typed command is `/opt/tools/claude -n <name>`

#### Scenario: Name already given
- **WHEN** the agent command is `claude -n mine`
- **THEN** the typed command is `claude -n mine`

#### Scenario: Chained command
- **WHEN** the agent command is `source env.sh && claude`
- **THEN** the typed command is unchanged

#### Scenario: Word containing claude
- **WHEN** the agent command is `claude-wrapper`
- **THEN** the typed command is unchanged

### Requirement: Session names are safe and unique
A session name SHALL contain only lowercase letters, digits and single hyphens, without leading or trailing hyphens: uppercase letters SHALL be lowercased and every run of other characters SHALL become one hyphen. When a live Claude session already has the name, ShogunAi SHALL append `-2`, `-3` and so on, using the first free one.

#### Scenario: Characters outside the set
- **WHEN** a group's alias is `App_Web` and its branch is `feature/TASK-123.v2`
- **THEN** the name is `app-web-task-123-v2`

#### Scenario: Name taken by a live session
- **WHEN** a live Claude session is already named `app-task-123`
- **THEN** the new session is named `app-task-123-2`

#### Scenario: Name of a session that is gone
- **WHEN** Claude Code's registry has a file named `app-task-123` whose process is no longer running
- **THEN** the new session is named `app-task-123`

### Requirement: Read Claude Code's session registry tolerantly
ShogunAi SHALL read Claude Code's session registry, one JSON file per session in the `sessions` folder of Claude Code's configuration directory, only to read each session's process id, folder and name. It SHALL ignore unknown fields, files without a process id or folder, files that can't be read or parsed, and sessions whose process isn't running, and a missing folder SHALL count as no sessions. It SHALL never write there.

#### Scenario: Unreadable file
- **WHEN** one session file is not valid JSON and another describes a live session
- **THEN** the live session is used and nothing fails

#### Scenario: No registry
- **WHEN** the registry folder doesn't exist
- **THEN** `shogun status` and `shogun open --herdr` behave as with no sessions

#### Scenario: Stale file
- **WHEN** a session file's process id belongs to no running Claude process
- **THEN** that session is ignored

### Requirement: Report the session name when opening in Herdr
After typing a named Claude command, `shogun open --herdr` SHALL wait up to 10 seconds for a live session with that name in the pane's folder, and SHALL report it in its text line and as `session_name` in the `--json` result of a worktree, of each worktree of a group, and of the multirepo agent. Without a confirmed name, `session_name` SHALL be `null` and the open SHALL still succeed.

#### Scenario: Opened
- **WHEN** the user runs `shogun open TASK-123 --herdr --json` in project `main-repo` and the session appears as `main-repo-task-123`
- **THEN** the result has `session_name` `main-repo-task-123`

#### Scenario: Group as JSON
- **WHEN** the user runs `shogun open TASK-123 --herdr --json` in a multirepo folder with group members `app` and `api`
- **THEN** `app`'s entry has `session_name` `app-task-123` and `api`'s has `api-task-123`

#### Scenario: Name never confirmed
- **WHEN** the agent starts but no session named as typed appears within 10 seconds
- **THEN** the open succeeds with `session_name` `null`

#### Scenario: Text output
- **WHEN** the user runs `shogun open TASK-123 --herdr` and the session is named `main-repo-task-123`
- **THEN** the printed line names the session `main-repo-task-123`

### Requirement: Report the session name of a focused agent
When `shogun open --herdr` only focuses a live agent, it SHALL report as `session_name` the name of a live Claude session whose folder is the opened folder, preferring the name ShogunAi would give it and otherwise the most recently started one, or `null` when there's none.

#### Scenario: Already open
- **WHEN** the worktree's live agent is focused and the registry has a live session named `main-repo-task-123` in its folder
- **THEN** the result has `herdr` `focused` and `session_name` `main-repo-task-123`

#### Scenario: Already open with a name of its own
- **WHEN** the focused session was started by hand and Claude Code named it `task-123-1a`
- **THEN** the result has `session_name` `task-123-1a`

#### Scenario: Focused session not found
- **WHEN** the focused agent isn't Claude, or no live session in the registry is in its folder
- **THEN** `session_name` is `null`

### Requirement: Show session names in status
`shogun status`, in a repository and in a multirepo folder, SHALL show next to each Claude agent the names of its live sessions whose folder belongs to that worktree, after its state, e.g. `claude(working, app-task-123)`, and `shogun status --json` SHALL add to each agent a `sessions` list with each session's `name` and `pid`, empty when there are none.

#### Scenario: Two worktrees of a group
- **WHEN** `app`'s and `api`'s worktrees of `feature/TASK-123` each run a Claude session named `app-task-123` and `api-task-123`
- **THEN** `app`'s line shows `claude(working, app-task-123)` and `api`'s shows `claude(working, api-task-123)`

#### Scenario: Several sessions in one worktree
- **WHEN** one worktree has two live sessions named `main-repo-task-123` and `main-repo-task-123-2`
- **THEN** its agent shows both names and its JSON `sessions` lists both

#### Scenario: JSON
- **WHEN** the user runs `shogun status --json` and a worktree's Claude session is `app-task-123` with process id 4242
- **THEN** that agent has `sessions` `[{"name": "app-task-123", "pid": 4242}]`, and every other field is as before

### Requirement: A session belongs to the worktree that contains its folder
`shogun status` SHALL attribute a live session to the deepest worktree that contains its folder, as it attributes agent processes, SHALL NOT attribute it to a worktree when its folder is inside a repository nested in that worktree, and in a multirepo folder SHALL attribute to the multirepo agent only a session whose folder is the multirepo folder itself.

#### Scenario: Session in a subfolder
- **WHEN** a session's folder is a subfolder of a worktree
- **THEN** it belongs to that worktree

#### Scenario: Session in a nested repository
- **WHEN** a session's folder is inside a repository nested in a worktree
- **THEN** it isn't shown for that worktree

#### Scenario: Session outside every worktree
- **WHEN** a live session's folder is outside every worktree
- **THEN** it isn't shown

#### Scenario: Dead session
- **WHEN** a session file's process isn't a running Claude process
- **THEN** it isn't shown

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
