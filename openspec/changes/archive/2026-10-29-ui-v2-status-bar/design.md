# Design

## Context

See proposal.md for the motivation. Today `Sidebar` receives `machineResources`, `machineSummaries` and `selectedMachine` from `AppShell` and draws a "Machine" section (and rail icons) after the projects. `MachineResources.visibleResources` already decides which resources apply (a saved project declares the provider, iOS simulators and DerivedData only on macOS, Desktop while the turn is active), and `MachineResources.summaries` holds a `MachineSummary` per resource refreshed every 30 seconds. A summary read that fails is silently dropped (`getOrNull()`), so nothing can say why a figure is missing. `MachineSummary.text()` builds one long string ("10 · 1.2 GB · 9/3") meant for a sidebar row.

In the sidebar, `MultirepoRow` puts a 40 dp `IconButton` chevron before the name, so the name starts about 44 dp further right than a top-level project's name, while its children use a 24 dp start padding: children start *left* of their parent's name and top-level projects start at the same x as the section labels. That's why a top-level project without marks reads as a section header. The workspace repository's project is a member of its multirepo (`MultirepoMembership.aliases`) and is drawn with its project name, which is the folder's name, the same as the multirepo's.

The epic runs `ui-v2-tokens` and `ui-v2-forms-states` in parallel: this change doesn't touch `ui/theme/**`, `components/Spacing.kt`, `PaletteIndex.kt`, `AppMenuBar.kt` or the surfaces vetoed by `orca-integration`, and uses only `MaterialTheme`/`ShogunTheme` roles so the restyle reaches it.

## Goals / Non-Goals

**Goals:**
- A pure, tested model of the status bar items, independent of Compose.
- The status bar as a shell component, fed by `MachineResources`, with the four states of norm "each asynchronous surface defines its four states" (loading → dash, error → danger color and reason, not applicable → not drawn; there is no "empty" state since a resource that applies always has a figure).
- One sidebar row geometry shared by views, projects, multirepos and their children.

**Non-Goals:**
- Changing the Machine views, the palette index, the View menu or the tray.
- The eyebrow style of section labels and the accent focus ring: they come with `ui-v2-tokens` and are adopted when it is merged into the epic.
- Default runner in the status bar (mockup B shows it): it belongs to `ui-v2-action-catalog`/`default-runner`.

## Decisions

### Status bar model in `ui/machine/MachineStatusItems.kt`
`machineStatusItems(resources, summaries, failures, strings): List<MachineStatusItem>` returns, for each visible resource in the bar's order, a `MachineStatusItem(resource, figure: String?, tooltip: String, attention: Status?)`. `figure == null` means "not measured yet" and the view draws a dash. `attention` is `Status.WARNING` when the pool is over its limit (`poolInUse > poolLimit`) or DerivedData is over its quota, `Status.DANGER` when the resource has a failure, `null` otherwise. The tooltip is the full title plus the full summary (`MachineSummary.text`) plus "over the limit" or the failure's described summary. Keeping it pure lets the unit tests cover which items appear, the warning and the not-applicable cases without a UI.
- Alternative: compute it inside the composable. Rejected: untestable without Compose and duplicates the string logic.

### Order: Desktop first
The bar follows mockup B: Desktop turn, then Gradle, emulators, simulators, DerivedData, Maven and Compose. The desktop turn is the most time-sensitive signal (an agent is waiting for the screen). It's a fixed list in the model, not a reorder of `MachineResource.entries`, so the enum and the saved `machine:<RESOURCE>` destinations don't change.

### Figures in mono, names in sans
Per norm "mono is for data, not for states": the short name ("Gradle", "Emulators", "Simulators", "DerivedData", "Maven", "Compose", "Desktop") uses `labelMedium` in `onSurfaceVariant`, the figure `ShogunTheme.typography.dataSmall`. Figures: pool `inUse/limit` when a limit applies, else the count; DerivedData `size / quota` or the size; Desktop the existing `desktopTurnSummary` (holder and waiters). Short names are new strings in English and Spanish.

### Summary failures in `MachineResources`
`refreshSummaries` records, per resource, the `Throwable` of the read that failed in `summaryFailures`, and clears it when the read succeeds. The previous summary is kept (the existing behaviour), so a failed item still shows its last known figure. Emulator and simulator reads return `Result` internally so their failure isn't lost; Compose keeps the failure next to `composeUnavailableReason`; DerivedData records its listing failure. The tooltip renders the reason with `Strings.describe(error).summary`, the same text an `ErrorNotice` shows.
- Alternative: a `MachineLoad`-like state per summary. Rejected: a larger refactor of `MachineResources` (910 lines) than this change needs.

### Shell layout
`AppShell` wraps its `Row(sidebar, divider, content)` in a `Column` with the row taking the remaining height, then a `HorizontalDivider` and `MachineStatusBar` when it has items. The bar spans the whole window width, under the sidebar too, like mockup B. Height is a named private constant (`StatusBarHeight = 28.dp`, the top of the brief's 24–28 dp so the focus ring fits). Background `surfaceContainerLow` like the sidebar; items spaced with `Spacing.md`. In rail mode (narrow window) items drop the short name so the bar fits without truncating; the full text stays in the tooltip.
- Alternative: a horizontal scroll for overflow. Rejected: hides items with no affordance; the compact mode fits the seven items in the 900 dp threshold.

### Items as buttons
Each item uses `Modifier.interactive` (keyboard focus, focus ring from the theme, hand pointer, ripple), `semantics { role = Role.Button; contentDescription = tooltip }`, and `WithTooltip`. The selected item (the shown machine view) uses `secondaryContainer`/`onSecondaryContainer`, the same selection roles as the sidebar today, so the tokens change can restyle both together. Colors for attention come only from `ShogunTheme.status.of(status).color`.

### Sidebar geometry
All expanded rows share one geometry: an 8 dp outer padding, an 8 dp inner padding, a 16 dp leading slot and an 8 dp gap, so labels start at 40 dp. Views put their icon in the slot; top-level projects leave it empty; a multirepo puts its chevron there (a compact 16 dp toggle with tooltip and label, replacing the 40 dp `IconButton`), so its name starts at 40 dp like a top-level project's; its children add one level (`Spacing.lg`) and start at 64 dp. Section labels keep starting at 16 dp, so items and headers no longer share an edge.
- Alternative: a folder icon on every multirepo and project. Rejected: mockup A uses no project icons, and the empty slot is enough to separate items from headers.

### Workspace label
`SidebarMultirepo` gains `labels: Map<String, String>` (project id → label). `AppShell` fills it from the membership with `multirepoChildLabels(membership)`: the workspace repository's project (`Multirepo.workspaceRepository` alias, matched through `MultirepoMembership.aliasOf`) is labelled with its alias; others keep their names. `sidebarEntries` sorts children by that label, and drag and context-menu reordering keep using project ids as keys, so saved orders stay valid.
- Alternative: hide the workspace project inside its multirepo. Rejected: its worktrees and settings must stay reachable, and the existing spec already names it `workspace` under the multirepo.

### Tour
`TourScript` and `TourViews` never mention the Machine section (only "Add project" in the sidebar), so the tour isn't changed.

## Risks / Trade-offs

- [The bar takes 28 dp of height from every view] → It replaces a sidebar section that already took ~200 dp, and is hidden when no resource applies.
- [Tooltips at the bottom edge may open off-window] → `TooltipArea` popups are clamped to the window by Compose Desktop; checked in the running app.
- [Parallel `ui-v2-tokens` restyles the sidebar colors and the focus ring] → This change uses only theme roles; after tokens merges, merge the epic and adopt the section-title and accent tokens.
- [Users who navigated to Machine through the sidebar lose that path] → The status bar is always visible when any resource applies, and the palette keeps every machine view.

## Migration Plan

No data migration: `lastDestination = machine:<RESOURCE>` still restores the view, and the sidebar order keys don't change.
