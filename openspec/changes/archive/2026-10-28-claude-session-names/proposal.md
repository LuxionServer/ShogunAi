# Proposal

## Why

In a multirepo group, the Claude sessions that `shogun open <id> --herdr` starts can't be told apart for messaging. Claude Code derives a session's name from its folder, and every worktree of a group shares the folder name (`<worktrees>/repo-a/TASK-123`, `<worktrees>/repo-b/TASK-123`), so the sessions get names like `task-123-1a` and `task-123-41`. The coordinating session's agent list doesn't show each session's folder and `shogun status` doesn't show session names, so the coordinator has to guess which session is which repository before sending it a task.

## What Changes

- When the agent ShogunAi types into a Herdr pane is Claude Code, the launch command gets `-n <name>` with a name ShogunAi chooses: `<alias>-<id>` for a group worktree, `<project>-<id>` for a worktree of a single repository, `<project>` for its main worktree, and the multirepo's name for the multirepo agent. Names are reduced to lowercase letters, digits and hyphens, and made unique against the live Claude sessions with a numeric suffix. A command that already names its session, or that isn't a single Claude Code invocation, is typed unchanged. This applies to the command line and to the desktop app's "Open in Herdr", which share the same use cases.
- After launching, `shogun open --herdr` waits a short time for the session to appear under that name in Claude Code's session registry, and reports the name: in the text line and as `session_name` in the `--json` result of a worktree, of each worktree of a group and of the multirepo agent. When the open only focuses an agent that was already running, the name is read from the registry. When no name can be confirmed the field is `null` and the open still succeeds.
- `shogun status` shows each Claude agent's session names next to its state, e.g. `agents=claude(working, app-task-123)`, listing every name when one worktree has several sessions; `shogun status --json` adds a `sessions` list with `name` and `pid` to each agent. The same applies to the group status of a multirepo folder.
- Claude Code's session registry (`~/.claude/sessions/<pid>.json`, an undocumented internal format) is read tolerantly: unknown or missing fields, unreadable files and a missing directory never fail `open` or `status`, and a file whose process is no longer running is ignored.

## Capabilities

### New Capabilities

- `claude-session-names`: how ShogunAi names the Claude Code sessions it launches, how it reads Claude Code's session registry, and how `shogun open --herdr` and `shogun status` report session names.

### Modified Capabilities

None. The existing `open` and `status` requirements keep their behavior; the new fields and the naming are specified in `claude-session-names`.

## Impact

- `core`: a new `ClaudeSessionRegistry` port and its file implementation (directory passed as a parameter), session name building and sanitizing, Claude detection in a free-text agent command, `HerdrAgentLauncher` appending `-n` and waiting for the name, `HerdrOpening` and `RunningAgent` gaining session data, `DetectRunningAgentsUseCase` joining sessions to worktrees by `cwd`, and the three open use cases passing the name.
- `cli`: `herdrOpeningLine`, `openJson`, `groupOpenJson`, `multirepoAgentOpenJson`, `agentLabel` and `agentJson` (additive JSON fields only), and the registry wiring.
- `desktopApp`: the registry wiring in `AppContainer`; no UI change.
- Machine state: read-only access to Claude Code's session registry; nothing is written there.
- Docs: the CLI and multirepo guides (English and Spanish), and the roadmap on archive.
