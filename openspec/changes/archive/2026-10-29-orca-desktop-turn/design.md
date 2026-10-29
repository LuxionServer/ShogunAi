# Design

## Context

See proposal.md for the motivation and what exploring Orca 1.4.243 showed. The pieces this change builds on:

- The desktop turn (`desktop-turn` spec, `DesktopTurnUseCases.kt`): `AcquireDesktopTurnUseCase` without a wait takes a free turn, renews the holder's, and throws `WorktreeError.DesktopTurnTaken(holder, waiting)` when another worktree holds it or others are ahead in line, without touching the turn or the queue.
- `shogun hook agent-state` already releases the turn on `Stop` and `SessionEnd` for the session's worktree.
- `ClaudeCodeHooks` installs one ShogunAi command per event, in a group without a `matcher`, recognizing its own hooks by the `shogun hook ` prefix and comparing only commands.
- Claude Code runs ShogunAi's user-level hooks in every terminal, Orca's included, next to Orca's own agent hooks. A `PreToolUse` hook can block a tool call with `{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"…"}}`; the reason is shown to the model. A hook object's `if` (permission-rule syntax) keeps the process from starting for calls that don't match.

## Goals / Non-Goals

**Goals:**

- Claude agents in Orca take the turn before driving the desktop with `orca computer`, without having to remember the rule.
- No cost for the shell commands that aren't computer use.
- A hook failure never blocks the agent.

**Non-Goals:**

- Agents other than Claude Code in Orca (Codex, OpenCode…): they have no ShogunAi hooks; they get the guide's rule.
- Other computer-use tools (Claude's computer-use MCP, Chrome automation): the same hook could cover them by tool name, but they aren't Orca's and their sharing rules differ; left as a roadmap follow-up.
- Orca's embedded browser (`orca tab`, `orca click`…) and `orca emulator`: they act inside Orca's own panes, not on the machine's desktop.
- Enforcing the turn for an agent that bypasses the hook (running `orca computer` through a script): the turn stays cooperative.

## Decisions

### Hook into Claude Code, not Orca

Orca offers nothing to hook into: computer use is its CLI, its `orca.yaml` hooks are worktree lifecycle scripts, and its agent hooks only report states. ShogunAi's own `PreToolUse` hook sees every command the agent runs, wherever it runs.

Alternatives considered:
- **Only the workflow guide.** It already says to take the turn before computer use, and that is the gap: Orca's `computer-use` skill tells the agent exactly what to run and never mentions the turn. The guide's rule is still updated to name `orca computer`, for agents without the hook.
- **A wrapper (an `orca` shim on the worktree's `PATH`).** Orca's skill resolves the executable itself and agents often call it by absolute path; a shim would be bypassed, and shadowing another tool's binary is fragile across Orca updates.

### Take the turn automatically; block only when it's taken

When the turn is free or already the worktree's, the hook acquires it (renewing it on every acting command) and stays silent, so the command runs through Claude Code's normal permission flow. When it's taken, the hook denies the call with a reason naming the holder, its expiry, the waiters and `shogun desktop acquire --wait` run in the background. Renewal on each action also solves the long-check renewal that the guide asks agents to do by hand; the `Stop` release still gives the turn back at the end of the response.

The hook never answers `allow`: that would skip the person's permission prompt for the command.

Alternative considered: waiting inside the hook. Rejected: a hook blocks the session with no progress shown, runs under Claude Code's hook timeout, and can't be backgrounded; `shogun desktop acquire --wait` already does all that properly.

### Filter with `matcher: "Bash"` and `if: "Bash(*computer *)"`, then parse

The filter is coarse on purpose: it covers `orca computer`, `orca-ide computer` and absolute paths, inside compound commands, as checked with Claude Code 2.1.312 (see proposal). The hook then decides exactly. A Claude Code version that ignores `if` still runs the hook only for `Bash`, at about 0.15 s per command, with the same result.

The parser (`OrcaComputerUse` in `core/domain/model`) splits the command into segments at `;`, `&`, `|`, newlines, `(` and `)`, splits each segment into words with quotes removed, and looks for a word whose file name is `orca` or `orca-ide` (with `.exe` or `.cmd` on Windows) followed by `computer` and a subcommand (the first word after `computer` not starting with `-`). The subcommand drives the desktop unless it is one of `capabilities`, `permissions`, `list-apps`, `list-windows`, or `get-app-state` without `--restore-window` in that segment. It is a heuristic over a shell string, not a shell parser: `echo "orca computer click"` reads as acting, which only means the turn is taken a moment early.

### Read-only commands don't need the turn

`get-app-state` reads the accessibility tree and captures the window without bringing it forward, and the list commands only read, so another agent's turn doesn't block an agent that is looking. `permissions` can open System Settings, which the person handles, not an agent turn. Everything else synthesizes input, changes values or the clipboard, or moves windows.

### The hook's worktree and failures

The worktree is the one containing the input's `cwd` (as for `agent-state`), resolved with `git rev-parse --show-toplevel`; outside a repository the hook does nothing. Every error other than `DesktopTurnTaken` (unreadable input, lock failure) lets the command run silently with exit code 0, as the other hooks do.

### Hooks with a matcher and a filter

`ClaudeCodeHooks.DESIRED` becomes a list of `DesiredHook(event, command, matcher, condition)`. A ShogunAi hook matches the desired one when command, its group's `matcher` and its own `if` are equal; otherwise it's reported as replaced, so an existing installation reads as outdated (`PreToolUse` missing) and the app's "Update" and `shogun hooks install` add it. Installing writes the group with `matcher` and the hook with `if`. The test that used `PreToolUse` as "an event no longer used" moves to another event.

### Guide wording

The `resources.desktop-turn` rule's text names `orca computer`; it's already in the session start, so the line budget doesn't change.

## Risks / Trade-offs

- [The `if` field or its wildcard matching changes in a later Claude Code] → The matcher still limits the hook to `Bash`; the parser decides on its own. Checked by the real-run validation of this change.
- [A heuristic parser misses an unusual invocation (a variable holding the path, `xargs`)] → The turn stays cooperative; the guide's rule covers it.
- [Two agents in the same worktree share the turn] → Same known edge as the existing turn.
- [Orca launches agents with `--dangerously-skip-permissions`] → `PreToolUse` hooks still run and a `deny` still blocks in that mode.

## Migration Plan

Installed hooks from an earlier version read as outdated until the person runs `shogun hooks install` or "Update" in Settings; until then Orca's computer use works as before. Rolling back leaves a `PreToolUse` hook calling an unknown `shogun hook desktop-turn`, which exits with a usage error (code 2) that Claude Code treats as blocking the `orca computer` command; running `shogun hooks install` from the older version removes the hook, since it's on an event that version doesn't use.
