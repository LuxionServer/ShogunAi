# Tasks

## 1. Index and matching

- [ ] 1.1 Add `PaletteItem` and `buildPaletteIndex(...)` over the catalog, board rows, visible machine entries and the action list, with no destructive actions; verify with `PaletteIndexTest`: every kind present, machine entries only when visible, no item deletes anything.
- [ ] 1.2 Add `matchScore` and the ranking (exact, prefix, word starts, contiguity, gaps, recency, alphabetical); verify with `PaletteMatcherTest` covering every "Matching and ranking" scenario and case changes as word starts.
- [ ] 1.3 Persist `recentPaletteItems` (unique, most recent first, at most 20, stale ids dropped) in `settings.json`; verify with `JsonSettingsFileTest` and `PaletteIndexTest` cases.

## 2. Palette UI

- [ ] 2.1 Build the overlay (search field, results with kind icon, highlighted matches, context and shortcut) opened with Cmd/Ctrl+K and View > Command palette, closed with Escape or an outside click; verify in the running app from every view, including project settings.
- [ ] 2.2 Handle arrows, Enter (primary action through `ShellViewModel.navigate`) and Cmd/Ctrl+Enter (copy launch command through the board) and record recent items; verify in the running app that selecting a worktree of another project opens "All worktrees" with it selected, and that leaving changed settings asks first.

## 3. Strings and docs

- [ ] 3.1 Add the palette strings (kinds, action titles, empty states) in both languages; verify `StringsTest` passes.
- [ ] 3.2 Add the palette to `docs/architecture/ui.md` and `ui.es.md` and list the app's keyboard shortcuts in `README.md` and `README.es.md`; verify `mkdocs build --strict` succeeds.
- [ ] 3.3 Run the app on the secondary display with three demo projects and check every `command-palette` scenario.

## Workflow follow-up

- Archive the change (`/opsx:archive command-palette`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `command-palette`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
- After this change is merged into the epic, open the epic's pull request against `develop` once the whole epic has been walked through end to end.
