# Tasks

## 1. Catalog

- [ ] 1.1 Add `defaultRunner` to `ViewActionContext` and make `viewOverflow` emit the default runner's multirepo agent plus an "Open multirepo agent with…" submenu with the others (strings in English and Spanish); verify `ActionSurfacesTest` cases for Herdr default with Orca, Orca default, and Orca not installed
- [ ] 1.2 Turn `groupCatalog` into a `GroupCatalog` with `OpenAll` per runner, `AddRepository(alias)` and `CleanUpGroup` (destructive, disabled with its reason), a `primary` for the default runner and `overflowEntries`; verify `ActionSurfacesTest` cases for the primary, "Open all with…", "Add repository", the separator before "Clean up group", its disabled reason and an opening in progress
- [ ] 1.3 Draw `ChangeGroupHeaderView` with the primary as its only button and a `MoreMenuButton` with the overflow, removing `AddRepositoryMenu` and the `DangerTextButton`, and add a tooltip to the branch line; verify `MultirepoViewUiTest`: one "Open all in …" button, no "Clean up group" button, the menu's entries, and the group actions reaching the board
- [ ] 1.4 Remove "Clean up" from `ProjectSummary`; verify `ProjectSummaryUiTest` shows the counts and no "Clean up"

## 2. Rows

- [ ] 2.1 Drop the origin from the name cell's second line, add `branchTooltip`, and separate the group tag, folder and project with " · "; verify `WorktreeRowModelTest` (origin only in the tooltip) and a `WorktreeRowUiTest` case for `TASK-7 · +app · <project>`
- [ ] 2.2 Add `compactRelativeTime` and `TimeCell.compactActivity`/`activityTooltip`, and show the compact form with the full tooltip in compact rows; verify `RelativeTimeTest`/`WorktreeRowModelTest` for every unit in English and Spanish and a `WorktreeRowUiTest` case for "12 h" in a compact row
- [ ] 2.3 Measure the multirepo aliases and pass a shared, clamped alias column width to every row; verify a `MultirepoViewUiTest` case where `workspace` is shown whole

## 3. Machine, palette and inspector

- [ ] 3.1 Return `DeviceLabel(data, note)` from the device labels, write notes in the interface font, split the profile out of `poolUsage`, and make the state and over-limit strings sentence case in both languages; verify `MachineViewLabelsTest`, `StatusMappingTest` and the Machine UI tests updated
- [ ] 3.2 Add `PaletteKind.SETTING` for Preferences, with its icon, kind label and tie order next to views, kept in the empty-query fallback; verify `PaletteIndexTest`, `PaletteMatcherTest` and a `CommandPaletteUiTest` case labelling Preferences as a setting
- [ ] 3.3 Add a tooltip for truncated keys and values in `PropertyList`; verify a `PropertyListUiTest` case with a long key and value showing their tooltips, and none for a short one

## 4. Verified fixes

- [ ] 4.1 Check in the running app whether Escape closes the command palette on macOS; if not, register the palette with `OpenMenus` while open; verify with the app and a `CommandPaletteUiTest`/`OpenMenus` test for the key-release path
- [ ] 4.2 Check in the running app whether Cmd+W closes Preferences; if not, handle `isCloseWindowShortcut` in `PreferencesWindow`; verify with the app and a unit test of the shortcut predicate

## 5. Documentation and validation

- [ ] 5.1 Update `docs/architecture/ui.md` and `docs/architecture/ui.es.md` (group header, view ⋯, summary, name and time cells, alias column, device labels, palette kinds, property tooltips, Escape and Cmd+W); verify `mkdocs build --strict`
- [ ] 5.2 Run `./gradlew test` green with no warnings
- [ ] 5.3 Run the app and check, on the secondary display in light and dark, the multirepo view (group header, its ⋯, the view's ⋯, alias column), Active narrow and wide, the summary, the Environment section, the emulators and simulators views and the palette; verify by screenshots

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md`.
- Implementation commit, then ask before opening the PR against `epic/ui-redesign-v2`.
