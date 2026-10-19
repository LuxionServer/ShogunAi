# Proposal

## Why

Some users switch between AI agents on the same task, for example when Claude runs out of quota and they continue with Codex or Antigravity. Today a project holds a single agent command, so switching means editing the project's settings, launching, and editing them back. This is the roadmap's "Multi-agent mode".

## What Changes

- A project can hold several agents, each with a name, its command with default arguments, and its Headroom option. The first one is the project's default; a project with one agent works exactly as today.
- Every launch action (copy launch command, open in terminal, open in Herdr) uses the worktree's agent by default and lets the user pick another one of the project's agents.
- Each worktree has its own agent, set from its detail or by launching it with another agent, and remembered on this machine, so the next launch continues with the agent the task moved to.
- Running-agent detection recognizes every agent command of the project, not only the first.

## Capabilities

### New Capabilities

### Modified Capabilities
- `project-configuration`: a list of agents instead of a single command.
- `worktree-terminal-launch`: choosing the agent for each launch, and setting each worktree's agent.
- `worktree-overview`: detection of every configured agent command.

## Impact

- `core`: `AgentLaunchConfig` gains an optional `name`; `ProjectConfig` gains `additionalAgents`, keeping `agentLaunchConfig` as the default agent so stored projects keep loading; the launch use cases (`CopyWorktreeLaunchCommandUseCase`, `OpenWorktreeInTerminalUseCase`, `OpenWorktreeInHerdrUseCase`) take the agent to use; `AgentSearch` takes every command; settings gain the per-worktree agent choice.
- `desktopApp`: the Agent section of project settings, an agent picker on the detail's launch buttons, the row context menu and the palette, strings.
- Docs: `docs/guide/desktop-app.md`, `docs/guide/configuration.md` and Spanish versions.
