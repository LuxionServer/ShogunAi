## Context

`OpenWorktreeInHerdrUseCase` in `core` already does everything the desktop app's "Open in Herdr" does: `herdr worktree open` under the repository's space, a pane with the worktree's environment and the agent's launch command, or only focusing when the worktree is already open. `ShogunCli` already has a `HerdrClient` (`CliHerdrClient`), an environment resolver and the target resolution `remove` uses.

## Goals / Non-Goals

**Goals:**
- `shogun open <target> --herdr` behaves like the app's action, through the same use case.

**Non-Goals:**
- Opening in a terminal or an IDE from the CLI.
- Changing what the desktop app does.

## Decisions

**`open` with a required `--herdr` flag instead of a `herdr open` subcommand.** The destination is a property of opening, so `--terminal` or `--ide` can join later. Without a destination the command fails with a usage error (exit 2) naming `--herdr`.

**Target resolution shared with `remove`, without its main worktree check.** `findWorktreeTarget` resolves path, branch or task id; `resolveWorktreeTarget` keeps calling it and then refuses the main worktree. Opening the main worktree in Herdr is a normal use.

**Agent by display name.** `--agent <name>` matches `ProjectConfig.agents` by `displayName`, ignoring case. An unknown name fails with a usage error that lists the project's agents, instead of silently falling back to the default as `agentNamed` does for the app's saved choice. In a repository without a ShogunAi project the default `AgentLaunchConfig` runs.

**Output.** Text: `opened <path> in Herdr with <agent>` or `focused <path> in Herdr (already open)`. JSON: `{"path", "branch", "herdr": "opened" | "focused", "agent": <name> | null}`, `agent` being `null` when nothing was launched. Herdr failures (`HerdrNotInstalled`, `HerdrCommandFailed`) are `WorktreeError`s, so they reach the CLI's existing one-line error with exit 1.

## Risks / Trade-offs

- [The agent starts inside Herdr, not in the caller's terminal] → that's the purpose; the text output says which agent was started where.
