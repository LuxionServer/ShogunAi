# Proposal

## Why

Agents running in Orca drive the desktop through Orca's computer use, and nothing makes them take ShogunAi's desktop turn first: the session-start rule speaks of "computer use" in general, Orca's own `computer-use` skill never mentions the turn, and an agent that skips the rule takes the screen, mouse and keyboard from another agent mid-check. Orca is becoming a first-class runner, so its agents must share the desktop like the others (roadmap item 1, "What only Orca offers").

What exploring Orca 1.4.243 showed:

- Orca's computer use is a command-line interface, `orca computer <command>` (`click`, `type-text`, `hotkey`, `get-app-state`…), that the agent runs from its shell, guided by Orca's bundled `computer-use` skill. There is no MCP server, no hook around it and no setting to run something before it; Orca's own "agent hooks" only report agent states.
- Some of its commands only read (`capabilities`, `permissions`, `list-apps`, `list-windows`, `get-app-state`), while the rest act on the app's window with clicks, keys, text, the clipboard, scrolling or dragging, and `--restore-window` brings a window to the front.
- Claude Code 2.1.312, which ShogunAi already integrates through user-level hooks that also run in Orca's terminals, accepts a `PreToolUse` hook with an `if` filter in permission-rule syntax. `"if": "Bash(*computer *)"` ran the hook for `orca computer capabilities` with an absolute path and inside `echo a && orca computer list-apps`, and not for `echo hi` or `ls`, so no process starts for other commands.

## What Changes

- A new Claude Code hook, `shogun hook desktop-turn`, installed for `PreToolUse` on Bash (filtered to commands mentioning `computer`):
  - Before a command that runs an acting `orca computer` command (or `get-app-state --restore-window`), it takes the desktop turn for the session's worktree, or renews it when the worktree already holds it, and lets the command run.
  - When another worktree holds the turn or others wait for it, it blocks the command and tells the agent who holds it, until when, how many wait, and to run `shogun desktop acquire --wait` in the background before trying again.
  - Read-only `orca computer` commands, other commands and sessions outside a repository are left alone, and the hook never blocks a command because of its own failure.
  - The existing `Stop` and `SessionEnd` release keeps giving the turn back when the response ends.
- `shogun hooks install`, `status` and the desktop app's hooks section include the new hook; an installation from an earlier version reads as outdated and is updated by installing again. ShogunAi's hooks can now carry a matcher and an `if` filter.
- The desktop turn rule in the workflow guide and in the session start names Orca's `orca computer` commands as computer use, for agents without the hook.
- Docs (agents' Claude Code and Orca sections, CLI guide) and both roadmaps: the item is removed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `desktop-turn`: Orca's acting computer-use commands take the turn automatically through the `desktop-turn` hook, and are blocked while another worktree holds it.
- `claude-code-hooks`: the hooks installed include `PreToolUse` for `shogun hook desktop-turn`, with its matcher and filter.
- `agent-workflow-guide`: the desktop turn rule names Orca's `orca computer` commands.

## Impact

- `core`: `ClaudeCodeHooks` (desired hooks with matcher and `if`), a parser that recognizes desktop-acting `orca computer` commands in a shell command, `WorkflowRules`.
- `cli`: `shogun hook desktop-turn` in `CliCommand`, `ShogunCli` and `CliHelp`, reusing `AcquireDesktopTurnUseCase` without waiting.
- `desktopApp`: no code change beyond what the hooks status already lists; the new event shows in the hooks preview.
- Docs: `docs/agents.md` and `docs/guide/cli.md`, both languages; `docs/roadmap.md` and `docs/roadmap.es.md`.
- No new dependency. Agents other than Claude Code in Orca (Codex…) only get the guide's rule.
