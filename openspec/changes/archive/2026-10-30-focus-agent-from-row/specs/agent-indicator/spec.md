# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
