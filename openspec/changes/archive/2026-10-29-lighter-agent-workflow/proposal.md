# Proposal

## Why

ShogunAi should step in only when agents really conflict over something scarce, and otherwise give them information instead of rules. An audit of what it imposes on agents found that hardly anything blocks them, but the context injected at every session start carries rules that don't apply there. Every Claude session in every repository gets the desktop-turn paragraph, even a backend session that never touches the desktop. And when a Gradle daemon is busy, nobody can tell who holds it. In a multirepo incident, one session ran a long-lived `benchRun` in another session's worktree. The blocked session couldn't see why its builds waited, so it worked around it by copying the worktree outside ShogunAi and building there, escaping the pool.

## What Changes

- The `PreToolUse` hook also takes the desktop turn for Claude's own computer use (the computer-use MCP tools that act on the desktop), just as it already does for Orca's `orca computer`. A tool call goes ahead when the desktop is free or the session's worktree already holds the turn, and is denied with the holder and how to wait only when another worktree holds it or others are waiting. Read-only tools, such as screenshots, are left alone.
- The desktop-turn rule leaves the session-start context. It stays in `shogun guide resources`, and the denial message tells the agent what to do at the moment it matters.
- The rule about `shogun new --from` and checking `git log -1` leaves the session-start context and stays in `shogun guide worktrees`. `shogun new` prints, on standard error, the commit the new branch starts at, so the agent sees the base without having to remember to check it.
- In the session-start context, the desktop-app rule shrinks to one line that points to `shogun guide resources`. The guide keeps the full text.
- For each busy Gradle daemon, `shogun gradle` and its JSON output show the build that holds it: the client's process id, the tasks it ran, the directory, how long it has been running and, when it can be traced, the agent session that started it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-workflow-guide`: the desktop-turn and `--from` rules are no longer in the session-start context, and the desktop-app rule has a short session-start form.
- `desktop-turn`: Claude's computer-use tools take the desktop turn through the hook, like Orca's computer use.
- `claude-code-hooks`: the `PreToolUse` hook also covers the computer-use tools that act on the desktop.
- `gradle-provider`: the busy daemon's holding build is identified.
- `command-line-tool`: `shogun gradle` shows the holding build, and `shogun new` reports the commit the branch starts at.

## Impact

- `core`: `WorkflowRules` (session-start selection and short texts), `ClaudeCodeHooks` (more than one ShogunAi hook per event), the Gradle daemon source and model (holding build), and the desktop-turn hook's command classification.
- `cli`: `hook desktop-turn` input handling for MCP tools, `shogun gradle` text and JSON output, and `shogun new` output on standard error.
- Installed hooks read as outdated after upgrading until `shogun hooks install` runs again, as with any change to the hooks.
- Docs: the Gradle provider and agent workflow pages in `docs/architecture/` (both languages), and the roadmap on archive.
