# Proposal

## Why

Before the v2 redesign the worktree row showed a robot icon tinted by the agent's state; since then the shared `AgentIndicator` draws an 8 dp dot. A dot doesn't say "agent": next to the device icon on the second line, and among the other dots and badges of the row and the sidebar, it reads as a generic status mark. The designer decided to bring the agent icon back in place of the dot, everywhere the indicator appears.

## What Changes

- The agent indicator draws the agent icon (the same robot glyph as the "open agent" action) in its state's status color instead of a dot, with the same state-to-color mapping, label and spacing.
- The indicator gets a named size with three values: small (sidebar), medium (execution cell, group header by change, multirepo header; the same size as the device icon of the execution cell) and large (the inspector's Agent section).
- Compact mode shows the icon alone, same size and color; the label, "+N" and source stay in the tooltip and the accessible name.
- The sidebar's activity mark for projects and multirepo folders becomes the small agent icon in the info color, drawn by the agent indicator in compact mode instead of its own dot.
- The icon is decorative for assistive technologies: the indicator's accessible name keeps carrying the agent and its state.
- Every state color reaches 3:1 against each surface where the icon appears, in both themes, checked by a test.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-indicator`: the agent is shown as an icon colored by its state, not a dot; it has three sizes by place.
- `worktree-row`: the execution cell and its narrow form show the agent's icon instead of its dot.
- `worktree-detail`: the Agent section's scenario shows the agent's icon.
- `multirepo-desktop`: the multirepo view's agent and the group headers by change show the agent's icon.
- `app-shell`: the sidebar's activity indicator is the agent icon in the info color.

## Impact

- `desktopApp/.../ui/components/AgentIndicator.kt`: icon instead of dot, size parameter.
- `desktopApp/.../ui/shell/Sidebar.kt`: `ActivityMarks` uses the agent indicator; `AgentDotSize` is removed.
- Call sites: `WorktreeRow.kt`, `WorktreeListPane.kt` (group header by change), `WorktreeScreen.kt` (multirepo header), `inspector/AgentSection.kt`.
- Tests: `AgentIndicatorUiTest`, `ThemeContrastTest`, sidebar UI tests if they look for the dot.
- No change to the domain, the CLI or the agent detection.
