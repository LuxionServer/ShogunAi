# Tasks

## 1. Machine summaries and the status bar model

- [ ] 1.1 Record per-resource summary failures in `MachineResources.refreshSummaries` (Gradle, Maven, emulators, simulators, DerivedData listing, Compose), cleared on the next successful read, keeping the last summary; verified by `MachineResourcesTest` cases for a failing and a recovering read.
- [ ] 1.2 Add `MachineStatusItem` and `machineStatusItems(...)` in `ui/machine/MachineStatusItems.kt` with the bar order, figures, tooltip and attention status; verified by `MachineStatusItemsTest` (items shown for the visible resources only, Desktop first, pool `inUse/limit` figure, warning over the limit and over the DerivedData quota, danger with the reason on failure, `null` figure while not measured).
- [ ] 1.3 Add the short names and the "not measured yet" text to `Strings`, `EnglishStrings` and `SpanishStrings`; verified by the existing i18n parity test.

## 2. Status bar in the shell

- [ ] 2.1 Add `MachineStatusBar` in `ui/shell/MachineStatusBar.kt`: single line, `StatusBarHeight` named constant, each item a button with icon, short name (omitted in compact mode), mono figure or dash, tooltip, focus ring, selected state and status colors only through `ShogunTheme.status`; verified by a Compose UI test (items, compact mode, click opens the view, button semantics with the full text).
- [ ] 2.2 Lay out `AppShell` as a column with the sidebar/content row and the status bar below, hidden when there are no items, wired to `machine.visibleResources`, `machine.summaries`, the failures and `ShellViewModel.selectMachine`; verified in the running app in light and dark themes.

## 3. Sidebar

- [ ] 3.1 Remove the Machine section and the machine rail icons from `Sidebar` and its parameters; verified by a Compose UI test asserting no "Machine" label nor machine entries in the expanded sidebar and in the rail.
- [ ] 3.2 Give every expanded row the shared geometry (leading slot): views with their icon, top-level projects with an empty slot, multirepos with a compact chevron toggle (tooltip and label), children one level deeper; verified by a Compose UI test comparing the left edges of a top-level project, a multirepo name and its children, and in the running app.
- [ ] 3.3 Add `SidebarMultirepo.labels` and `multirepoChildLabels(membership)` labelling the workspace repository's project with its alias, used for display and alphabetical order inside the folder while reordering keeps project ids; verified by `SidebarOrderTest` cases (no duplicate name, alias order, saved order still applied) and the existing reorder tests.

## 4. Docs and verification

- [ ] 4.1 Update `docs/architecture/ui.md` and `docs/guide/desktop-app.md` and their `.es.md` for the status bar, the sidebar without Machine and the multirepo row; verified by `mkdocs build --strict`.
- [ ] 4.2 Run `./gradlew test` with no warnings and check the app with `./gradlew :desktopApp:hotRun --auto` in light and dark, wide and narrow windows (status bar items, tooltips, keyboard focus, multirepo rows).

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` if needed, then the implementation commit.
- Before opening the pull request: merge `origin/epic/ui-redesign-v2`, re-run the tests, ask Ciro, and open it with `--base epic/ui-redesign-v2 --assignee CiroFeliu`.
