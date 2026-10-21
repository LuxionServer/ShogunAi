# Design

## Context

`DetectRunningAgentsUseCase` merges two sources: the process list (`lsof` working directories plus `ps` executables), which gives agents without a state, and Herdr's API, which gives `HerdrAgentState`. `worktree-states-redesign` makes "Needs you" depend on an agent being blocked or done, so agents outside Herdr can never reach it. The CLI already handles three Claude Code hooks (`hook worktree-create`, `worktree-remove`, `session-start`) that read the hook's JSON from standard input; users configure them by copying JSON from `docs/agents.md`.

Checked on this machine: `shogun` is a native executable that starts in about 0.13 s, and Claude Code is 2.1.306.

## Goals / Non-Goals

**Goals:**
- Working, blocked and done for Claude sessions outside Herdr, accurate enough for the state groups.
- Zero manual configuration beyond one confirmation: one command or one button installs every ShogunAi hook, and upgrades keep them current.
- Nothing written into the working tree, nothing left behind by a crashed session.

**Non-Goals:**
- States for other agents (Codex, Gemini…): only Claude Code has these hooks; they keep showing without a state unless Herdr reports them.
- Notifications when an agent becomes blocked (roadmap follow-up).
- Hooks on every tool call (`PreToolUse`/`PostToolUse`).

## Decisions

### Events and states

| Hook event | Recorded state |
|---|---|
| `UserPromptSubmit` | working |
| `Notification` | blocked; done when the notification says the prompt has been idle (`notification_type` `idle_prompt`) |
| `Stop` | done |
| `SessionEnd` | record removed |

`PreToolUse` and `PostToolUse` would track a granted permission exactly, but they run on every tool call and each one would start `shogun`. Instead, the record keeps the session's `transcript_path`, and a blocked or done record reads as working when the transcript's modification time is later than the record's: after a permission is granted Claude writes the tool's result to the transcript. A done session isn't written to until the next prompt, which fires `UserPromptSubmit` anyway.

The `notification_type` field is read from the `Notification` input: `idle_prompt` means done, `auth_success` is ignored (nothing is waiting), and any other value or a missing field means blocked. The inputs of `UserPromptSubmit`, `Stop` and `SessionEnd` were recorded from a real session of Claude Code 2.1.306 (all carry `session_id`, `transcript_path`, `cwd` and `hook_event_name`); `Notification` couldn't be provoked in a non-interactive session, so its shape (`message`, `notification_type`) follows Claude Code's documentation and is covered by tests only.

### Where records live

One JSON file per session, `<git-dir>/shogunai/agent-sessions/<session_id>.json`, where `<git-dir>` is the worktree's own Git directory (`.git/worktrees/<name>` for a linked worktree, `.git` for the main one), resolved by reading the worktree's `.git` file. Contents: `sessionId`, `agent` (`claude`), `state`, `updatedAt`, `transcriptPath`, `pid`. Git ignores unknown files there, the folder goes away with `git worktree remove`, and nothing is copied to new worktrees. The file is written to a temporary name and renamed, so a reader never sees half a record.

Alternative considered: `~/.shogunai/agent-sessions/`. Rejected: records would outlive their worktrees and need their own cleanup.

### Liveness

The hook walks its ancestors (`ProcessHandle.parent()`) to the first process whose executable is `claude` and records its pid. A record is live while that pid is alive and is still a `claude` process, so a reused pid doesn't revive it. A record without a pid (not found) is live while any `claude` process runs in that worktree and the record is less than 12 hours old. Detection deletes records it finds dead, best effort.

### Merging sources

`HerdrAgentState` becomes `AgentState` and `RunningAgent` gains `stateSource: AgentStateSource?` (`HERDR`, `CLAUDE_CODE`); `herdrState` is replaced by `state`, and the CLI keeps writing `herdrState` when the source is Herdr. `DetectRunningAgentsUseCase` reads records through a new `AgentSessionStore` port (file implementation in `infrastructure`) for the worktrees with a `claude` process, picks the most urgent live session per worktree (blocked > done > working, the urgency order already used for Herdr), and applies it to the `claude` agent. Herdr's state is applied after, so it wins.

### Installing the hooks

A `core` use case, `ClaudeCodeHooks`, works on the parsed settings as a JSON tree (`kotlinx.serialization.json`), never on text:

- The desired hooks are one table in code: event → command (`shogun hook worktree-create`, …, `shogun hook agent-state` for the four state events).
- A ShogunAi hook is a `command` hook whose command starts with `shogun hook `. Install removes ShogunAi hooks that aren't in the table or differ, then appends the missing ones as their own matcher group; everything else is untouched, including key order where the JSON library keeps it.
- Status compares the table with what is there: installed, outdated (some missing or different) or missing (none).
- Uninstall removes ShogunAi hooks and empty groups and events.
- The settings file is `$CLAUDE_CONFIG_DIR/settings.json` or `~/.claude/settings.json`; `--project` uses `<repository>/.claude/settings.json`. Invalid JSON aborts without writing. Writes go to a temporary file and are renamed.

The preview is the list of events and commands added, replaced and removed, shown by the CLI before asking and by the app in its confirmation dialog.

`WorktreeCreate` and `WorktreeRemove` are installed too: one action sets up everything, as the user asked. They already handle repositories without a ShogunAi project (worktrees go to `.claude/worktrees/`, as Claude Code does by itself).

### Desktop app

Settings gets a "Claude Code" section showing the status for the user settings, with Install/Update/Remove and the preview dialog. The one-time notice is shown in the worktree view when a `claude` agent has no state source and the status isn't "installed"; dismissing it stores `claudeHooksNoticeDismissed` in the app's settings. The app runs the core use case directly; it doesn't need `shogun` on the `PATH` to write the settings, but the hooks do, so the section warns when `shogun` isn't found on the `PATH`.

## Risks / Trade-offs

- [Claude Code changes its hook input or event names] → the hook ignores unknown events and fields, exits 0, and the agent falls back to "no state"; the checked version is written in `docs/agents.md`.
- [Transcript writes that aren't activity (e.g. compaction) read as working] → acceptable: the next `Stop` or `Notification` corrects it.
- [User settings managed by another tool or synced] → the preview and confirmation show exactly what changes; uninstall reverts it.
- [`shogun` not on the `PATH` of Claude Code's shell] → hooks fail visibly in Claude Code; Settings warns, and `shogun hooks status` runs from the same shell users run Claude in.
- [Depends on `worktree-states-redesign`] → this branch rebases on it once merged; until then, the states are only visible in `shogun status` and the row's agent label.
