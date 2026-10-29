# Proposal

## Why

The v2 design audit found that every list draws its own row (the worktree list has a well-built fixed grid, Machine has `MachineRowLine` and `MachineColumnHeader`, the multirepo settings have rows with inline "Rename" and "Remove"), that the worktree detail groups its content with loose eyebrow titles in an order nobody chose and hides sections that have nothing yet (so "not applicable" can't be told from "not read yet"), that the agent is shown three ways (colored "Claude · working" in the row, lowercase uncolored "claude · working" in the detail, plain "claude · idle" in the multirepo header) with labels that depend on the source, and that monospace is used for states and relative dates, flattening the hierarchy. This change is the "inspector and rows" part of the epic "UI redesign, second part" (direction A, ink as primary), running in parallel with `ui-v2-action-catalog`.

## What Changes

- New shared components in `ui/components`:
  - `DataRow` and `ColumnSpec`: the worktree row's grid made generic, with fixed or weighted columns, a compact width per column (or hidden when compact), hover, selection on the accent container, keyboard focus and activation, a trailing hover-action slot and the 48 dp (two lines) and 32 dp (one line) heights; plus `DataHeader` for column labels aligned with the rows.
  - `InspectorSection`: an eyebrow label declared as a heading by the component, a summary at the end, collapsible, and the states content, loading (skeleton), empty (one muted sentence) and not applicable (not drawn).
  - `PropertyList`: a key/value grid, key in the sans, value in mono, selectable, with a per-row copy action confirmed in place with `InlineConfirm`.
  - `AgentIndicator`: a status dot, the agent's name and its state in the sans with one vocabulary (working, needs you, done, idle, running), compact as the dot alone, and a tooltip with every agent, the state's source (Herdr, Orca or Claude Code hooks) and when it was last read.
- Adoption:
  - The worktree row becomes a `DataRow` (Active, All, project, multirepo By change and By state); the agent line becomes an `AgentIndicator`; states, relative dates and project names move to the sans. The context menu wrapper stays a single call, owned by `ui-v2-action-catalog`.
  - The Machine lists (Gradle, Maven, emulators, simulators, DerivedData, Compose, desktop turn) use `DataRow` and `DataHeader` instead of `MachineRowLine` and `MachineColumnHeader`.
  - The multirepo settings' repository rows use `DataRow`, with the remove action in the row's action slot and "Rename" only while the alias is edited.
  - The worktree detail body follows the fixed order: header (branch, origin and path), [action block, untouched, replaced by `ui-v2-action-catalog`'s `ActionBar`], the worktree's notices, Status, Agent, Environment, Resources (devices, Compose, headless app, desktop turn and disk) and Provisioning, each an `InspectorSection` with `PropertyList`, extracted to `worktree/inspector/`.
  - The multirepo view header and the By change group headers show an `AgentIndicator`.
- Pending from `ui-v2-tokens` (its task 7.1): remove the `bodySmall` labels inside buttons left in the detail and move the agent's state and dates to the sans.
- Documentation of the components and of the inspector and row rules in `docs/architecture/ui.md`, and of the detail's sections in `docs/guide/desktop-app.md`, with their Spanish translations.

## Capabilities

### New Capabilities
- `data-rows`: the shared row and column contract of every list (columns, compact behavior, hover, selection, keyboard, action slot, heights, header labels).
- `inspector-sections`: the inspector's section and property list components and the fixed section order of a detail pane.
- `agent-indicator`: one presentation and one vocabulary for an agent's state wherever it appears, with its source and last update.

### Modified Capabilities
- `worktree-row`: the execution cell's agent uses the agent indicator and its vocabulary; states, relative dates and the project's name are in the sans.
- `worktree-detail`: the detail's content is organized in fixed sections with properties that can be copied, and sections that aren't read yet show a skeleton instead of disappearing.
- `machine-resources`: device views' column labels come from the shared row header, and every Machine list uses the shared row.
- `multirepo-desktop`: the multirepo agent is shown with the agent indicator, By change group headers show their worktrees' agents, and the settings' repository rows use the shared row.
- `desktop-ui-testing`: UI tests cover the row, inspector section, property list and agent indicator components and the inspector's order.

## Impact

- Code: `desktopApp/.../ui/components` (new `DataRow.kt`, `InspectorSection.kt`, `PropertyList.kt`, `AgentIndicator.kt`), `worktree/WorktreeRow.kt`, `worktree/WorktreeRowModel.kt`, `worktree/WorktreeDetailPane.kt` (body only), new `worktree/inspector/*`, `worktree/WorktreeListPane.kt` (change group header), `worktree/WorktreeScreen.kt` (multirepo agent text only), `worktree/ProjectWorktrees.kt` and `worktree/WorktreeBoard.kt` (time of the last agent read), `machine/*View*.kt`, `machine/MachineViewParts.kt`, `multirepo/MultirepoSettingsView.kt`, `i18n/*`.
- Not touched: the detail's action block, the row's context menu content, `OpenActions.kt`, `AgentPicker.kt`, the palette, the tray, the app menu and keyboard shortcuts (owned by `ui-v2-action-catalog`); `multirepo/MultirepoActions.kt` (planned by `orca-multirepos`).
- No core or CLI changes; no new dependencies.
