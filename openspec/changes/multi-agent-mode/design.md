# Design

## Context

See proposal.md for motivation. `ProjectConfig.agentLaunchConfig` (`agentCommand`, `useHeadroom`) is stored in `projects.json` and read by the three launch use cases and by agent detection (`AgentSearch(agentCommand, worktrees)`). The command is a free string that may already hold arguments (`claude --model opus`). Per-machine UI state lives in `settings.json` (`JsonSettingsFile.Settings`).

## Goals / Non-Goals

**Goals:**
- Stored projects load unchanged, and a project with one agent looks and behaves as before.
- An older app version reading a project saved by this one still launches the default agent.

**Non-Goals:**
- Agents declared in the repository (`.shogunai.json`); agents are a per-user, per-machine choice.
- Passing context from one agent to the next (handoff prompts); the user does that in the agent.
- Agent selection from the command line; `shogun` doesn't launch agents.

## Decisions

**Data model.** `AgentLaunchConfig(name: String? = null, agentCommand, useHeadroom)`, and `ProjectConfig.additionalAgents: List<AgentLaunchConfig> = emptyList()`. `ProjectConfig.agents` = `listOf(agentLaunchConfig) + additionalAgents`. Keeping the default in its existing field (instead of replacing it by a list) means older versions, which ignore unknown keys, still find and launch it. Alternative: a single `agents` list with a migration; rejected for breaking downgrade. An agent's display name is its `name`, or else the executable of its command capitalized (`codex` → "Codex").

**Identity of an agent.** Agents are referred to by display name within a project; names must be unique and non-blank, validated in settings. Renaming or removing an agent drops remembered choices that pointed to it, falling back to the default.

**Remembered choice per worktree.** `Settings.worktreeAgents: Map<String, String>` (worktree path → agent display name), written when the user sets the worktree's agent in the detail or launches with a non-default agent, and cleared when either chooses the default. Entries for worktrees that no longer exist are pruned when a project's list is loaded. Stored in `settings.json`, never in the repository.

**Picking in the UI.** In the detail, an "Agent" field under the launch actions shows the worktree's agent with a dropdown of the project's agents, so it can be set without launching. the launch buttons read "Copy launch command · Codex" when a project has several agents, with a chevron opening the list of the project's agents; choosing one launches with it and remembers it. The row context menu gets a "Launch with" submenu for the three actions, and the palette lists the worktree's launch actions with the current agent, plus one secondary entry per other agent. With a single agent none of this is shown.

**Settings UI.** The Agent section lists the agents in order (the first marked default), each with name, command and Headroom switch, plus add, remove and "Make default". Making another agent the default moves it into `agentLaunchConfig`.

**Detection.** `AgentSearch` holds every agent command's executable; `DetectRunningAgentsUseCase` adds them all to the known names, so `antigravity` is recognized when it is one of the project's agents.

## Risks / Trade-offs

- [An older version saving the project drops `additionalAgents`] → Only if the user saves settings in the older version; the default agent is kept. Acceptable for a downgrade.
- [Remembered agent hides that the default changed] → The button always names the agent it will use.
