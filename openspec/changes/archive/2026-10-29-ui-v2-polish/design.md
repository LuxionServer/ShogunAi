# Design

## Context

See proposal.md for the why. The rules being enforced are summarized in `docs/architecture/ui.md` ("Action catalog", "Rules for rows and the inspector", "Rules"). The action catalog (`ui/actions`) already declares the view's and the group's actions, but the group header draws its catalog entries as buttons and adds "Add repository" (a Material `DropdownMenu`) and "Clean up group" (a `DangerTextButton`) outside it. The view's ⋯ already comes from `viewOverflow`, but lists one entry per runner. `ToolsPreferences.defaultRunner` is the default runner for worktrees; the board reads it through any holder's `actionTools`.

## Goals / Non-Goals

**Goals:**
- Every entry moved by this change is built from the catalog and unit-tested without Compose.
- Truncation fixes keep fixed column widths per list (the row grid stays aligned).

**Non-Goals:**
- No new runner, action or setting; no change to `core` or the CLI.
- No palette entries for group actions.
- No ⌘W for the main window, whose close behavior depends on the tray setting.

## Decisions

### Group catalog with a primary and an overflow
`groupCatalog` returns a `GroupCatalog` with every group action: `OpenAll(target)` per runner, new `AddRepository(alias)` ids (manage group) and a new `CleanUpGroup` id (destructive group, `ConfirmKind.CLEAN_UP`, disabled with the `CleanupBlock` reason). `GroupActionContext` gains the default runner, the installed runners, the addable aliases, whether an addition is running and the cleanup block. `primary` is `OpenAll(defaultRunner)`; `overflowEntries(strings)` gives "Open all with…" (a `Nested` of the other installed runners, labelled with the runner's name), "Add repository" (a `Nested` of aliases), a divider and "Clean up group". The header draws the primary as a `NeutralTextButton` and the rest with `MoreMenuButton`, so `ShogunMenu` gives disabled reasons, keyboard navigation and Escape handling. The Material `DropdownMenu` of "Add repository" goes away.

Alternative considered: a split button "Open all in Herdr ▾". Rejected because the ⋯ is needed anyway for "Clean up group", and two menus in one header is noise.

### Multirepo agent: default plus "with…" submenu
`ViewActionContext` gains `defaultRunner`. Both `OpenMultirepoAgent` specs stay in the catalog (the palette and tests can find them); `viewOverflow` emits the default runner's spec as a plain entry and the others in a `Nested("Open multirepo agent with…")` labelled with the runner's name. With Orca missing, the default falls back to Herdr, which the view always offers, as today.

### Informative summary
`ProjectSummary` loses `canCleanUp`/`onCleanUp`. The view's ⋯ already has "Clean up", and the palette too. Alternative: route the summary's button through the catalog. Rejected: a second, loose copy of a destructive action is what the rule forbids.

### Row second line and origin
`NameCell` drops `origin` from what the second line shows and gains `branchTooltip` (branch, then origin, on two lines). The view renders the group tag, the folder and the project separated by " · " (the tag stays clickable). The origin chip of the inspector header is unchanged. Alternative: a `StatusChip` for the origin in the row. Rejected: the row already allows at most two chips and the origin isn't a value compared across rows.

### Compact time
A pure `compactRelativeTime(from, now, strings)` next to `relativeTime` with the same thresholds; `TimeCell` gains `compactActivity` and `activityTooltip`. `TimeCellView` receives the row's `compact` flag (already passed by `DataRow`) and shows the compact text with the full one as tooltip. New strings: `compactNow`, `compactMinutes`, `compactHours`, `compactDays`, `compactMonths`, `compactYears` (en: now, 5 min, 12 h, 2 d, 3 mo, 1 y; es: ahora, 5 min, 12 h, 2 d, 3 mes, 1 a).

### Alias column width
`WorktreeListPane` measures the longest alias of the shown multirepo with a `TextMeasurer` in the alias style and passes the width, clamped between 48 and 128 dp, to every row (`aliasColumnWidth`), so all rows share it; the column keeps that width when compact. `AliasCellView` already has a tooltip. The group header's branch line gets a `WithTooltip` with its full text; fewer buttons in the header leave it more room.

### Machine: modes and states
`emulatorDeviceLabel`/`simulatorDeviceLabel` return `DeviceLabel(data: String?, note: String?)`: data in `MachineDataText`, the note (Automatic, Not created) in `MachineStateText`. `strings.poolUsage` drops the profile; `PoolUsageSection` writes the profile apart in the interface font after a " · ". The state strings (`emulatorStopped`, `emulatorBooting`, `emulatorRunning`, `simulatorShutdown`, `simulatorBooting`, `simulatorBooted`, `overLimit`) become sentence case in both languages; they're only used through `StatusMapping` and chips, and the row's device line, where sentence case reads as well.

### Palette setting kind
`PaletteKind.SETTING` with the settings icon and `paletteKindSetting`, used by Preferences. Its tie order sits with views, and the empty-query fallback lists views and settings, so Preferences keeps showing there.

### Truncated properties
`PropertyRow` and `PropertyValueRow` use a small `OverflowText` that records `TextLayoutResult.hasVisualOverflow` and wraps itself in `WithTooltip` only when truncated, so untruncated rows get no tooltip.

### Escape and ⌘W
Both are checked in the running app first. If Escape fails on macOS (the window only gets the key release, as already documented for menus), the palette registers its dismissal with `OpenMenus` while open, so the main window's existing key-up handler closes it before touching the selection. If ⌘W fails, `PreferencesWindow` handles it in `onPreviewKeyEvent` with a pure `isCloseWindowShortcut(key, type, meta, ctrl, shift, alt, mac)`, unit-tested.

## Risks / Trade-offs

- [Moving "Clean up group" and "Add repository" into ⋯ makes them one click further] → They're infrequent and the rule puts them there; the ⋯ is next to the button.
- [Sentence case changes strings asserted by existing tests] → Update those tests with the new expectations.
- [Registering the palette in `OpenMenus` couples the palette to the menu registry] → It's the same mechanism menus use for the same macOS limitation; documented in `ui.md`.
