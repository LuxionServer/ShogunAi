# Design

## Context

See proposal.md for the motivation. The worktree row (`worktree/WorktreeRow.kt`) is a `Row` of fixed-width cells built by hand, with its widths and compact widths as private constants and its context menu wrapping the whole row. Machine draws its lists with `MachineRowLine` (a `Row` with vertical padding) and `MachineColumnHeader`, and each view sets its own widths. The multirepo settings draw each repository as a `Row` with a `Field`, the path and inline "Rename" and "Remove" text buttons. The detail pane (`worktree/WorktreeDetailPane.kt`, 600 lines) is one column: header, the action block (agent picker, copy launch command, open actions, Herdr, Orca, delete), the action error and release notices, and private section composables (Status, Agents, Provisioning, Environment, Devices, Desktop turn, Headless app, Compose, Disk) each with an eyebrow title, returning early when they have nothing. Agents come from `RunningAgent(name, state: AgentState?, stateSource: AgentStateSource?)`; core already maps Orca's "waiting" to `BLOCKED`, and nothing records when agents were read.

`ui-v2-tokens`, `ui-v2-status-bar` and `ui-v2-forms-states` are merged into the epic, so `Spacing.smd`, `ShogunTheme.colors.accent`/`accentContainer`, `ShogunTheme.typography.eyebrow`/`data`/`dataSmall`, `Eyebrow`, `RowShape`, `ShogunLayout.compactBreakpoint`, `SkeletonRows`, `InlineConfirm` and the title components exist. `ui-v2-action-catalog` runs in parallel and owns the detail's action block (including the agent picker), the row's context menu content and everything about actions.

Design v2 rules applied (section 04 of the designer's document): "The inspector has a fixed order", "Columns before lines", "Chip, dot or text", "Monospace is for data, not states", "Every asynchronous surface defines its four states", "Trivial confirmations happen in place", "Titles are declared as titles" (the section component sets `heading()` itself), "How a new agent is presented" (one vocabulary, a source label never shown in the row) and "How a new runner is presented" (its only place in the agent's presentation is the indicator's tooltip).

## Goals / Non-Goals

**Goals:**
- Each component lives in `ui/components`, depends only on `MaterialTheme`, `ShogunTheme` and plain values (no domain types), and keeps its internal sizes as named constants.
- Coexist with `ui-v2-action-catalog` with trivial merges: the detail's action block keeps its lines and position; the row keeps its context menu wrapper as one call around the `DataRow`.
- Every mapping that decides what is shown (agent phase, tooltip, section summaries and states) is a pure function with unit tests.

**Non-Goals:**
- Actions: `ActionBar`, `SplitButton`, `ShogunMenu`, the catalog, the row's context menu entries and the detail's agent picker. Moving the agent picker into the Agent section, as the mockup shows, is left to the catalog or a follow-up, since the picker belongs to its block.
- New columns (base branch, PR state): this change gives them their place, not their data.
- The sidebar, which isn't a list of data rows.
- Tabs in the inspector (direction B).

## Decisions

### `DataRow` takes cells, each with its `ColumnSpec`

```kotlin
sealed interface CompactBehavior { Keep; Hide; data class Shrink(val width: Dp) }
data class ColumnSpec(val width: Dp? = null, val weight: Float = 1f, val compact: CompactBehavior = Keep)
class DataCell(val spec: ColumnSpec, val content: @Composable (compact: Boolean) -> Unit)
enum class RowLines(val minHeight: Dp) { ONE(32.dp), TWO(48.dp) }

fun DataRow(cells, modifier, lines, compact, selected, onSelect: (() -> Unit)?, actions: ActionSlot?)
fun DataHeader(labels: List<Pair<ColumnSpec, String>>, modifier, compact, actionSlotWidth)
```

A `null` width means a weighted column (`Modifier.weight`), otherwise a fixed width; compact applies `Shrink` or `Hide` per cell. The row's horizontal padding is `Spacing.smd` and its gap `Spacing.sm`, the same for the header, so labels align with cells. `onSelect != null` makes the row `interactive` (hand pointer, ripple hover, accent focus ring, Enter and Space through `clickable`) and draws `selected` on `accentContainer` with `RowShape`; `onSelect == null` adds nothing, so Machine rows don't take focus. `ActionSlot(width, pinned, content)` draws a fixed-width `Box` at the end whose content is shown while the row is hovered, focus is inside the row, or `pinned` (the row's copy confirmation). The compact switch stays the caller's decision (`maxWidth < ShogunLayout.compactBreakpoint` in the list), so every row of a list switches together.

Alternative considered: a table component that owns a list of rows and measures columns. The worktree list is a `LazyColumn` with sticky headers of three kinds; a per-row component with declared widths keeps it lazy and needs no measuring pass.

### The worktree row keeps its model and becomes cells

`WorktreeRowModel` stays the Compose-free model. `WorktreeRow` builds its cells from it (alias, badge, execution, name, Git, time) with the widths it has today as `ColumnSpec`s (`Shrink` for alias, execution, Git and time), and passes the copy action as the action slot pinned by `isCopied`. The `ContextMenuArea` around it stays one call with the same items, so the catalog's extraction of the menu merges without touching the grid. The execution cell's agent line is replaced by `AgentIndicator`, which is `compact`-aware. The time cell, the project's name and the agent state move to `bodySmall` (Geist); the branch, folder, alias, Git counts and device names stay in `data`/`dataSmall`.

### `AgentIndicator` and its pure model

`AgentPhase(status) { NEEDS_YOU(WARNING), DONE(SUCCESS), WORKING(INFO), RUNNING(INFO), IDLE(NEUTRAL) }` in urgency order, and `AgentState?.phase()` (`BLOCKED` → needs you, `null` → running). `agentIndicator(agents, readAt, now, strings): AgentIndicatorModel?` in `worktree/AgentIndicatorModel.kt` picks the most urgent agent, its display name, the count of others and the tooltip: one line per agent ("Claude · needs you"), a line with the source of the most urgent agent's state (`AgentStateSource` → "Herdr", "Orca", "Claude Code hooks", or "only the process was found" when `null`) and "updated <relative>" from `readAt`. The component in `components/AgentIndicator.kt` takes the model's plain values (`status`, `label`, `moreCount`, `tooltip`): a 8 dp dot in the status color, the label in `bodySmall` and `onSurfaceVariant`, "+N" in `dataSmall`, all in `WithTooltip`, with the label as the accessible name; compact draws the dot only. The label text stays neutral so the dot carries the color (rule "Chip, dot or text") and contrast isn't a concern.

`ProjectWorktrees.agentsReadAt` and `WorktreeBoard.multirepoAgentsReadAt` are set from their injected `clock` whenever agents are applied. `Strings.agentLabel` and `agentStateName` are replaced by `agentPhaseName(phase)` and `agentLabel(name, phase)`, plus `agentStateSource(source)`, `agentsUpdated(relative)` and `noAgentRunning`.

### `InspectorSection` with a state

```kotlin
sealed interface SectionState { Content; Loading; data class Empty(val message: String); NotApplicable }
fun InspectorSection(id, title, modifier, state = Content, summary: String? = null, content: @Composable ColumnScope.() -> Unit)
```

`NotApplicable` draws nothing. Otherwise the label line is an `interactive` row with the `Eyebrow` (`heading()` set on it by the component), the summary in `dataSmall` pushed to the end, and a chevron; it toggles a `rememberSaveable(id)` flag keyed by a stable section id, so collapsing Environment stays collapsed while the user moves between worktrees, and sets `stateDescription` to expanded or collapsed. `Loading` draws `SkeletonRows(count = 1)` and `Empty` one `bodySmall` muted sentence. `SectionCard` is left for forms.

### `PropertyList` with typed rows and a free row

```kotlin
data class Property(val key: String, val value: String, val copyable: Boolean = false)
fun PropertyList(properties, modifier, onCopy: (String) -> Unit = clipboard copy)
fun PropertyRow(key, modifier, content: @Composable RowScope.() -> Unit)
```

Each row is a `Row` with the key in `bodySmall`/`onSurfaceVariant` at a fixed `PropertyKeyWidth` (112 dp, a named constant, so keys align without measuring) and the value in `dataSmall` inside one `SelectionContainer` for the list. A copyable row shows an `InlineConfirmIconAction` while hovered or confirming, with its own `rememberInlineConfirm` keyed by the value. `PropertyRow` puts free content (a chip and a "Start" button for an emulator) in the value column, so device and Compose rows keep their actions and still align.

### The inspector's sections are files in `worktree/inspector/`

`InspectorHeader.kt` (project, branch, origin chip, path with the in-place copy), `StatusSection.kt`, `AgentSection.kt`, `EnvironmentSection.kt`, `ResourcesSection.kt` (emulator, simulator, Compose, headless app, desktop turn and disk as property rows, with the release dialog of the desktop turn), `ProvisioningSection.kt`, and `InspectorModels.kt` with the pure functions: `statusProperties`, `statusSummary` ("4 changes · ↑2"), `environmentProperties`, `environmentSummary` ("2 ports · 5 vars") and the section states. `WorktreeDetailPane` keeps its signature, the header call, the action block and the notices in place, and replaces the tail of section calls with the five sections in order; it no longer holds section code. States: Status `null`/`Loading` → loading and `Unavailable` → empty with "Status unavailable"; Agent `holder.agents == null` → loading and no agents → empty; Environment with no ports, variables or pending variables → not applicable (the repository declares none); Resources → not applicable when no device, Compose, app or desktop turn applies and the size is known to be absent, loading while only the size is pending; Provisioning → not applicable for the main or a missing worktree, loading while `null` or checking. The `bodySmall` labels inside buttons go away (pending task 7.1 of `ui-v2-tokens`), and device actions use `GhostButton` with the compact size.

`statusSegments` is kept for the project summary and its test if it still has callers; otherwise it is replaced by `statusProperties` and its test moves.

### Machine and multirepo settings adopt the row

`MachineRowLine` becomes `MachineRow(cells)` over `DataRow(lines = ONE, onSelect = null)` and `MachineColumnHeader` over `DataHeader`, with the existing Machine width constants as `ColumnSpec`s; each view passes cells instead of composables in a `RowScope`. Action buttons ("Stop", "Delete", "Release") stay as a fixed column, not a hover slot, because they are the reason those lists exist. In the multirepo settings each repository is a `DataRow(lines = ONE)` with a fixed alias column holding the `Field`, a weighted path column, a `Rename` column that's empty unless edited, and the remove action as a danger-tinted `IconAction` in an always-visible action slot (it's a settings form, not a hover surface), keeping its current behavior.

### Multirepo headers

The multirepo view header replaces its `Text(agentLabel(...))` loop with one `AgentIndicator` from `agentIndicator(board.multirepoAgentsOf(folder), board.multirepoAgentsReadAt, …)`; nothing else in that header changes, since `orca-multirepos` will add actions there. `ChangeGroupHeaderView` gains an `agent: AgentIndicatorModel?` parameter, computed by the board from the group's members' agents, drawn after the title column.

## Risks / Trade-offs

- [Conflicts with `ui-v2-action-catalog` in `WorktreeDetailPane.kt` and `WorktreeRow.kt`] → the action block lines and the context menu items are left byte-for-byte; this branch merges second and resolves.
- [Conflicts with `orca-multirepos` in `WorktreeListPane.kt` (group header actions) and the multirepo header] → only a new parameter and one composable call are added there; its worktree had no changes when this was planned.
- [A hover-only copy action can't be reached by keyboard] → the action slot also shows while focus is inside the row, and the context menu keeps "Copy launch command".
- [Machine rows lose a few pixels of vertical padding] → 32 dp minimum height as the design asks; the "Stop" button is the compact size.

## Migration Plan

No data or settings change. The change merges into `epic/ui-redesign-v2` after `ui-v2-action-catalog`; rollback is reverting its merge commit.
