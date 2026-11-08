# agent-indicator Specification

## Purpose
Defines one presentation and one vocabulary for the state of the coding agents running in a worktree or a multirepo folder, whichever tool reported it, so the same agent looks the same in the list, the detail and the multirepo view.

## Requirements

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
An agent SHALL be shown as the agent icon in its state's status color (warning for needs you, info for working and running, success for done, neutral for idle), followed by its name and its state in the interface font, not the monospaced font. The same icon SHALL be used for every agent and every state. In a compact place only the icon SHALL be shown, at the same size and in the same color, and the name and state SHALL stay in its tooltip and accessible name.

#### Scenario: Compact row
- **WHEN** the worktree list is narrow and Claude needs the user
- **THEN** the row shows only the agent icon in the warning color, and hovering it says Claude needs you

#### Scenario: Same icon for every agent
- **WHEN** Claude is working in one worktree and Codex is done in another
- **THEN** both rows show the same agent icon, Claude's in the info color and Codex's in the success color

#### Scenario: Icon legible on its surfaces
- **WHEN** the agent icon is shown in any state color on a worktree row, a selected worktree row, the sidebar, a selected sidebar entry or the inspector, in the light or the dark theme
- **THEN** the icon's color has a contrast of at least 3:1 against that surface

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

#### Scenario: Runner of each agent
- **WHEN** Claude needs the user in a Herdr pane and Codex works in a terminal Orca reports
- **THEN** the tooltip lists "Claude · needs you · in Herdr" and "Codex · working · in Orca"

#### Scenario: Runner unknown
- **WHEN** only Claude's process was found in a worktree
- **THEN** the tooltip lists "Claude · running" without saying where it runs

### Requirement: Same indicator everywhere
The worktree row, the worktree detail's Agent section, the multirepo view's header and the group headers of the multirepo view by change SHALL show agents with this indicator. The icon SHALL be small in the sidebar, medium in the worktree row, the group headers by change and the multirepo view's header, where it matches the size of the device icon on the row's second line, and large in the worktree detail's Agent section.

#### Scenario: Multirepo header
- **WHEN** Claude is idle in a multirepo folder
- **THEN** the multirepo view's header shows a neutral agent icon with "Claude · idle", as a worktree row would

#### Scenario: Aligned with the device line
- **WHEN** a worktree row shows Claude working on its first line and a running emulator on its second
- **THEN** the agent icon and the emulator icon have the same size and are aligned in the execution cell

### Requirement: Runner an agent runs in
An agent SHALL be taken as running in Herdr when Herdr reports its pane, and in Orca when Orca reports its state. Otherwise its runner SHALL be unknown, including when only Claude Code's hooks report it.

#### Scenario: Reported by Herdr
- **WHEN** Herdr reports Claude in one of its panes
- **THEN** Claude runs in Herdr

#### Scenario: Reported only by Claude Code's hooks
- **WHEN** Claude Code's hooks report Claude as working and neither Herdr nor Orca report it
- **THEN** Claude's runner is unknown

### Requirement: Clickable indicator to focus the agent
Where an agent indicator offers to focus its agent, and one of its agents runs in a known runner, clicking it SHALL focus the most urgent of those agents in its runner. The indicator SHALL then show the hand pointer, be a button for assistive technologies, and its tooltip SHALL add "click to focus" to that agent's line. Otherwise the indicator SHALL not react to clicks and SHALL not add the hint.

#### Scenario: Focusable agent
- **WHEN** Claude needs the user in Herdr and the indicator offers to focus it
- **THEN** its tooltip reads "Claude · needs you · in Herdr — click to focus", and clicking it focuses Claude's pane in Herdr

#### Scenario: Most urgent agent in a known runner
- **WHEN** Claude needs the user without a known runner and Codex works in Orca
- **THEN** clicking the indicator focuses Codex in Orca, and only Codex's line says "click to focus"

#### Scenario: No known runner
- **WHEN** only Claude's process was found
- **THEN** the indicator shows no hand pointer, has no "click to focus" hint and clicking it does nothing
