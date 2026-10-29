# Proposal

## Why

The sidebar has no room for the Machine summaries: their labels are truncated ("Gradl…", "Android e…"), the DerivedData label disappears, and "9/3, over the limit" is only readable inside each view. The sidebar also shows a multirepo in two ways that read differently (a collapsible row whose children are indented less than its name, and top-level project rows that look like section headers), and the workspace repository's project appears inside its multirepo with the multirepo's own name. This is the first piece of direction B of the UI v2 design adopted inside direction A: Machine moves to a bottom status bar, and the sidebar keeps only views, projects and multirepos.

## What Changes

- A single-line status bar fixed at the bottom of the main window, with one item per Machine resource that applies on this machine (Desktop turn, Gradle daemons, Android emulators, iOS simulators, Xcode DerivedData, Maven daemons, Compose projects). Each item is a button with an icon, a short name and a mono figure, colored through the status mapping only when it needs attention (over the pool limit or the DerivedData quota: warning; failed read: danger), with a tooltip giving the full text, never truncated. Activating it opens that resource's existing view, which is marked as selected.
- Item states: a dash instead of "Calculating…" while a resource hasn't been measured, the error color and the reason in the tooltip when its read failed, and no item when the resource doesn't apply (no project declares its provider, or it needs macOS). With no item at all the bar isn't drawn.
- `MachineResources` remembers why each summary read failed, so the bar can explain it.
- **BREAKING (UI)**: the sidebar's "Machine" section is removed, in the expanded sidebar and in the rail. Machine views stay reachable from the status bar and the command palette.
- One representation for a multirepo in the sidebar: a collapsible row with a chevron in the leading slot, its repositories indented one level below its name; top-level projects align with the views' labels so no item reads as a section header. The workspace repository's project is shown inside its multirepo with its alias (`workspace`), never with the multirepo's name. Dragging and the context-menu reordering are kept unchanged.
- Docs (`docs/architecture/ui.md`, `docs/guide/desktop-app.md` and their Spanish versions) describe the status bar and the sidebar. The tour doesn't point at the Machine section, so it isn't changed.

## Capabilities

### New Capabilities
- `machine-status-bar`: the bottom status bar that summarizes the Machine resources, its items, states, tooltips and navigation to each view.

### Modified Capabilities
- `machine-resources`: the sidebar's "Machine" section is removed; the summaries, the Desktop entry, the pool usage and the DerivedData total are shown by the status bar instead.
- `multirepo-desktop`: the multirepo folder in the sidebar has a single representation and the workspace repository's project is labelled with its alias.

## Impact

- Code: `ui/shell/Sidebar.kt`, `ui/shell/AppShell.kt`, a new `ui/shell/MachineStatusBar.kt`, a new `ui/machine/MachineStatusItems.kt` (pure model), `ui/machine/MachineResources.kt` (summary failures), `ui/i18n` (English and Spanish strings).
- Untouched on purpose: `ui/theme/**`, `components/Spacing.kt` (owned by `ui-v2-tokens`), `palette/PaletteIndex.kt`, `shell/AppMenuBar.kt` and the surfaces vetoed by `orca-integration`.
- Tests: status bar model, sidebar entries and labels, i18n parity, Compose UI tests of the bar and the sidebar.
- Docs: `docs/architecture/ui.md`, `docs/guide/desktop-app.md`, and their `.es.md`.
