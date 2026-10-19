# Tasks

## 1. Local project filter

- [ ] 1.1 Add the per-view local project filter to `WorktreeBoard` and use it in `shownProjects` when the destination has no project; clear it when its project is removed; add `WorktreeBoardTest` cases (Active filtered to one project, All filtered, destination project wins, removed project clears the filter) and verify they pass
- [ ] 1.2 Show the header's project filter only in "Active" and "All worktrees", wired to the local filter instead of `ShellViewModel.selectFilter`, and remove `selectFilter` if unused; add a Compose UI test (filter present in Active and All, absent in a project's view, picking a project keeps the sidebar selection) and verify it passes
- [ ] 1.3 Clear the local filter when the palette selects a worktree of another project into "All worktrees"; add a test and verify it passes

## 2. State group hints

- [ ] 2.1 Add the hint and tooltip strings for every `StateGroupKind` in English and Spanish and verify `StringsTest` passes
- [ ] 2.2 Show the hint next to each state group's title, truncating first on narrow widths, with the full criterion in a tooltip; add a Compose UI test for a header's hint and verify it passes
- [ ] 2.3 Validate in the running app, on the secondary display, in both languages: hints readable in light and dark themes, tooltip shown on hover, project filter in Active and All only

## 3. Docs

- [ ] 3.1 Add a "States" section to `docs/guide/desktop-app.md` and `desktop-app.es.md` with every group and its criterion, matching the tooltips
- [ ] 3.2 Update the project filter and group header descriptions in `docs/architecture/ui.md` and `ui.es.md`, and verify `mkdocs build --strict` passes
- [ ] 3.3 Run `./gradlew test` and verify it passes
