# Spec Delta

## MODIFIED Requirements

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

### Requirement: Same indicator everywhere
The worktree row, the worktree detail's Agent section, the multirepo view's header and the group headers of the multirepo view by change SHALL show agents with this indicator. The icon SHALL be small in the sidebar, medium in the worktree row, the group headers by change and the multirepo view's header, where it matches the size of the device icon on the row's second line, and large in the worktree detail's Agent section.

#### Scenario: Multirepo header
- **WHEN** Claude is idle in a multirepo folder
- **THEN** the multirepo view's header shows a neutral agent icon with "Claude · idle", as a worktree row would

#### Scenario: Aligned with the device line
- **WHEN** a worktree row shows Claude working on its first line and a running emulator on its second
- **THEN** the agent icon and the emulator icon have the same size and are aligned in the execution cell
