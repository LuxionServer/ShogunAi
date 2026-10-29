# Spec Delta

## Purpose

Defines one presentation and one vocabulary for the state of the coding agents running in a worktree or a multirepo folder, whichever tool reported it, so the same agent looks the same in the list, the detail and the multirepo view.

## ADDED Requirements

### Requirement: One vocabulary for the agent's state
An agent's state SHALL be shown with one of five words, whatever tool reported it: "working", "needs you" (the agent is blocked or waiting for the user), "done", "idle", and "running" when the agent's process is found but its state isn't known. A label specific to a source SHALL NOT be shown.

#### Scenario: Blocked in Herdr
- **WHEN** Herdr reports Claude as blocked
- **THEN** the agent is shown as "Claude · needs you"

#### Scenario: Waiting in Orca
- **WHEN** Orca reports Codex as waiting
- **THEN** the agent is shown as "Codex · needs you"

#### Scenario: State unknown
- **WHEN** Claude runs in a worktree without Herdr, Orca or Claude Code hooks reporting its state
- **THEN** the agent is shown as "Claude · running"

### Requirement: Dot, name and state
An agent SHALL be shown as a dot in its state's status color (warning for needs you, info for working and running, success for done, neutral for idle), followed by its name and its state in the interface font, not the monospaced font. In a compact place only the dot SHALL be shown, and the name and state SHALL stay in its tooltip and accessible name.

#### Scenario: Compact row
- **WHEN** the worktree list is narrow and Claude needs the user
- **THEN** the row shows only a dot in the warning color, and hovering it says Claude needs you

### Requirement: Several agents
When several agents run in the same place, the most urgent one SHALL be shown (needs you, then done, working, running and idle), followed by a "+N" count of the others.

#### Scenario: Two agents
- **WHEN** Codex is working and Claude needs the user in the same worktree
- **THEN** the indicator shows "Claude · needs you" with "+1"

### Requirement: Source and last update in the tooltip
Hovering an agent indicator SHALL list every agent with its state, say which tool reported the state (Herdr, Orca or Claude Code hooks), or that only its process was found, and say how long ago the agents were last read.

#### Scenario: State from Orca
- **WHEN** Orca reported Claude as waiting and the agents were read 40 seconds ago
- **THEN** the tooltip says Claude needs you, that the state comes from Orca, and that it was updated less than a minute ago

### Requirement: Same indicator everywhere
The worktree row, the worktree detail's Agent section, the multirepo view's header and the group headers of the multirepo view by change SHALL show agents with this indicator.

#### Scenario: Multirepo header
- **WHEN** Claude is idle in a multirepo folder
- **THEN** the multirepo view's header shows a neutral dot with "Claude · idle", as a worktree row would
