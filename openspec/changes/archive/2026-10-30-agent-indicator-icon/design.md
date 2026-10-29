# Design

## Context

`AgentIndicator` (`ui/components/AgentIndicator.kt`) is the shared composable for an agent's state since the v2 redesign. It draws an 8 dp `CircleShape` box tinted with `ShogunTheme.status.of(phase.status).color`, followed by the label in `onSurfaceVariant` and an optional "+N". It's used in four places:

- `WorktreeRow.ExecutionCellView`, full or compact; the device line below it uses a 14 dp icon (`CellIconSize`).
- `WorktreeListPane.ChangeGroupHeaderView` (group header by change).
- `WorktreeScreen` (multirepo view's header).
- `inspector/AgentSection` (the inspector's Agent section).

The sidebar doesn't use it: `Sidebar.ActivityMarks` draws its own 8 dp dot (`AgentDotSize`), always in `Status.INFO`, wrapped in a tooltip with `strings.agentsRunning(names)` and without an accessible name.

The designer's decision is recorded in the v2 design artifact, section "AgentIndicator: el robot en lugar del punto".

## Goals / Non-Goals

**Goals:**
- One agent glyph everywhere an agent's presence is shown, sized by place, with no loose `.dp` at call sites.
- The sidebar mark goes through `AgentIndicator`, so it gains the same accessible name and tooltip handling as the rest.

**Non-Goals:**
- Different icons per agent (Claude, Codex…) or per state.
- Changing the state-to-color mapping, the label, the "+N" or the tooltip contents.
- Changing the sidebar's count or what counts as activity.

## Decisions

### Icon: `Icons.Default.SmartToy`, filled

Same glyph as `ActionIcon.AGENT` in `ui/actions/ActionPresentation.kt`, so "open agent" and "agent here" read as the same thing. The icon replaces the `Box` inside the existing `Row`; spacing stays `Spacing.xs`. `contentDescription = null`, since the `Row` already does `clearAndSetSemantics { contentDescription = description }`.

Alternative considered: keep the dot and add the icon only in the full form. Rejected: the designer wants one mark, and the compact form is where a bare dot is most ambiguous.

### Size as an enum parameter

```kotlin
enum class AgentIndicatorSize(val icon: Dp) { SMALL(12.dp), MEDIUM(14.dp), LARGE(16.dp) }

fun AgentIndicator(model, modifier = Modifier, compact = false, size = AgentIndicatorSize.MEDIUM)
```

- `SMALL` (12 dp): sidebar.
- `MEDIUM` (14 dp, default): execution cell, group header by change, multirepo header. Same value as `CellIconSize` so the agent icon lines up with the device icon in the execution cell. `WorktreeRow` keeps its own `CellIconSize`; the equality is pinned by the spec scenario "Aligned with the device line" and checked visually, rather than coupling a row constant to the component.
- `LARGE` (16 dp): inspector's Agent section.

An enum, not a raw `Dp`, keeps screens free of loose `.dp` and limits sizes to the three the designer chose. `MEDIUM` as default means only the sidebar and inspector call sites change.

### Sidebar through `AgentIndicator` compact

`ActivityMarks` builds an `AgentIndicatorModel(phase = AgentPhase.RUNNING, label = strings.agentsRunning(names), moreCount = 0, tooltip = strings.agentsRunning(names))` and draws `AgentIndicator(model, compact = true, size = SMALL)` before the count. `RUNNING` maps to `Status.INFO`, keeping today's always-info color: the sidebar summarizes presence, not the most urgent state. `AgentDotSize` is removed from `Sidebar.kt` and `AgentIndicator.kt`; the `background`/`CircleShape` imports go with them if unused.

Alternative considered: a sidebar-specific `Icon`. Rejected: it would duplicate tooltip and semantics handling and drift from the shared component.

### Contrast test at 3:1

`ThemeContrastTest` already checks status colors at 4.5:1 on the M3 surfaces and on `accentContainer` (selected row). The icon is a non-text graphic, so its target is 3:1 (`INDICATOR_CONTRAST`). A new test, `agent icon colors reach indicator contrast where the indicator is shown`, iterates every `Status` color in both themes over the actual backgrounds of the indicator:

- normal row and headers: `surfaceContainerLow`, `surfaceContainer`;
- selected row: `ShogunColors.accentContainer`;
- sidebar: `surfaceContainerLow`; selected sidebar entry: `secondaryContainer` (not covered today);
- inspector: `surface`.

It's partly redundant with the 4.5:1 tests, but it states the icon's own requirement and adds `secondaryContainer`, which no test covers now. If a state color misses 3:1 on `secondaryContainer`, the fix is in the theme tokens, decided with the designer.

### UI tests

The icon sits inside the `Row` that does `clearAndSetSemantics`, so it has no semantics node of its own and tests can't find it by tag. They work on the indicator's node (found by its content description) instead:

- **Icon, not dot**: in compact mode the icon is the node's only child, so the node's bounds equal the icon's. Asserting width and height equal to the size's `icon` value fails with the 8 dp dot and passes with the icon. A second check captures the node as an image and asserts it contains the state color, so an empty box of the right size doesn't pass.
- **Compact**: no label text, content description kept, tooltip on hover (existing test, kept).
- **One test per `AgentIndicatorSize`**: the compact node measures `SMALL` 12 dp, `MEDIUM` 14 dp, `LARGE` 16 dp, with the usual model and label.
- **Sidebar**: a project with running agents exposes the "agents running" text as content description of a node of 12 dp.

The unused `AgentIndicatorTag` constant is either used by these tests (on the `Row`'s modifier) or removed, whichever the implementation proves works with `clearAndSetSemantics`.

## Risks / Trade-offs

- [The robot at 12 dp may read as a blob in the sidebar] → visual check in the headless app in both themes; the designer chose 12 dp knowing the glyph.
- [Taller than the 8 dp dot, the icon could change row heights] → every place already hosts 14–16 dp icons (`CellLine` is 18 dp, sidebar rows 32 dp); checked visually.
- [A state color below 3:1 on `secondaryContainer`] → the new test fails before merge; the token is adjusted with the designer, not the test.
