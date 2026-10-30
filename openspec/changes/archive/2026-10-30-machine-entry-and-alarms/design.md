# Design

## Context

The status bar is built from `machineStatusItems(...)`, which maps every visible `MachineResource` to a `MachineStatusItem` (name, figure, tooltip, attention), and `AppShell` hides the bar when the list is empty. A Machine destination is always `ShellDestination.Machine(resource)`, so without a declared provider (and nobody holding the desktop turn) there is nothing to navigate to. Any read failure becomes `Status.DANGER`. Preferences is opened by `onOpenPreferences`, already passed into `AppShell`.

The Gradle daemons view (`GradleDaemonsView.kt`) always draws the state cell, gives the disabled reason of "Stop idle daemons" only in a tooltip ("No daemon is idle"), leaves the owner cell blank when the owner is `null`, uses `SecondaryButton` for each row's Stop, and draws one info line per project with the build cache off.

The desktop app's lines outside composables have a coverage floor, so the decisions below keep the logic in plain functions that unit tests reach.

## Goals / Non-Goals

**Goals:**
- One entry point to Machine that exists in every state of the window.
- Alarms only for real failures, with missing tools explained instead.
- A Gradle daemons view that doesn't show noise (unknown columns, blank owners, repeated notices) and explains its disabled action.

**Non-Goals:**
- The count of Gradle daemons shown by the bar, its tooltip and the view (ux-quick-fixes).
- The tour notice in the sidebar footer (ux-quick-fixes); the gear leaves room for it.
- The same treatment for the Maven, emulator, simulator or Compose views' lists, and the Compose view's Docker error state, which stays as specified.

## Decisions

### A Machine overview destination
`ShellDestination.MachineOverview` is a new data object with its own storage key (`machine`), so it's restored like any view. Its content is a `MachineOverviewView` that reuses `machineStatusItems` for the rows (icon, full name, figure, tooltip text as summary), so the overview and the bar can never disagree. The overview lists the desktop turn always: `MachineResources.overviewResources` is `visibleResources` plus `DESKTOP_TURN`, and `overviewSummaries` always holds the desktop turn's summary, while `summaries` keeps leaving it out when the turn is free so the bar and the summary refresh behave as before. When `declared` is empty it adds an explanatory text below the Desktop row.

Alternatives: making `Machine(resource)` nullable ripples through every `when` on the destination and the palette; opening the first visible resource (or Desktop) when "Machine" is clicked gives no place to explain what Machine is when nothing is declared.

### The "Machine" item is part of the bar, not of the items list
`MachineStatusBar` gets `overviewSelected` and `onOpenOverview`, and draws a `MachineEntry` item before the resource items with a `Memory` icon and the "Machine" label (icon only when compact). The bar is shown unconditionally. Keeping it out of `MachineStatusItem` avoids a fake resource in `MachineResource`, whose `when`s are exhaustive across the app.

### Missing tools are a property of the failure
A function `Throwable.isMissingTool()` in `MachineStatusItems.kt` returns true for `WorktreeError.AndroidSdkNotFound`, `WorktreeError.DockerUnavailable` and `WorktreeError.IosSimulatorsNotSupported`. `machineStatusItems` keeps the reason in the tooltip but sets `attention = null` for them. Mapping by error type keeps the rule testable and in one place; the error presentation layer already describes each of them with a next step.

### Gradle daemons view logic in plain functions
- `showsDaemonState(daemons)`: true when any daemon's state is `Busy` or `Idle`. The row omits the state cell when false (the shared `DataRow` takes the list of cells, so leaving one out keeps alignment across rows).
- `stopIdleReason(daemons, strings)`: when `stopIdleBlock` is `NONE_IDLE`, counts the Gradle daemons that are busy and those of unknown state (`Unknown` or `Untracked`) and picks "All N are busy, none is idle", "The state of all N is unknown, none can be stopped safely" or "B are busy and the state of U is unknown, none is idle". It's shown as secondary text before the button and also used as the tooltip. With no Gradle daemon (only Kotlin ones) and none stoppable, the existing "No daemon is idle" stays.
- The owner cell shows `strings.ownerUnknown` with `MachineStateText` in the dimmed color (`onSurfaceVariant` at the theme's disabled alpha is too faint; `outline` is the dimmed secondary color used elsewhere for hints) when the owner is `null`.
- Row Stop buttons use `GhostButton`. Its disabled content uses `ShogunTheme.colors.disabledContent`, which keeps a readable contrast against the surface; the reason stays in `DisabledReason`'s tooltip.
- `buildCacheNotice(projectNames, strings)` returns `null`, a single-project text, or a summary with the names as details. The view draws it with the existing info line plus a "Details (N)" toggle, the same pattern `GroupedNotice` uses, without a dismiss button because the notice reflects configuration, not an event.

### The gear in the sidebar
`Sidebar` gets `onOpenPreferences` and draws a footer row, outside the scrolling column, with an `IconAction` gear (`Icons.Default.Settings`) labelled with `strings.preferencesTitle`, aligned to the start in the expanded sidebar and centered in the rail. ux-quick-fixes adds the tour notice to the same footer, so the footer is a `Row` that can take another child.

## Risks / Trade-offs

- [The always-visible bar costs 28 dp in every window] → It's the same height it has today whenever anything is declared; it's the only way into Machine otherwise.
- [Parallel changes touch `Sidebar.kt`, `MachineStatusBar.kt`, `AppShell.kt` and the roadmap] → Keep the edits local (new parameters with defaults, a footer block, a leading item), and merge `origin/develop` before opening the pull request.
- [A tool missing on purpose hides a misconfiguration] → The tooltip still gives the reason and the next step; the view itself keeps its error state.
