# Tasks

## 1. Domain

- [ ] 1.1 Add `attributeToProjects` and `ProjectWorktreeSet` in `core`, with the deepest-worktree rule, removed worktrees and outside folders; verify with `ProjectAttributionTest` (nested worktrees, two projects, removed folder, outside folder, project still loading).

## 2. State

- [ ] 2.1 Add `MachineResources` with one loader per resource, started and stopped with its view, 5-second ticks, and machine-wide use cases built in `AppContainer`; verify with `MachineResourcesTest` using fakes: loaders only run while shown, a stop result is kept until dismissed, stop and delete rules are delegated to the existing use cases.
- [ ] 2.2 Add the 30-second summary tick and `declaredProviders` from the board; verify with tests that entries appear only for declared providers and that summaries update.

## 3. Views

- [ ] 3.1 Add the sidebar "Machine" section with its entries and summaries, and `ShellDestination.Machine(resource)`; verify in the running app with a demo project that declares Gradle and one that declares nothing.
- [ ] 3.2 Build the Gradle daemons view (table, owners, stop one and stop idle, result notice, per-project settings and build cache notes); verify in the running app with daemons from two projects.
- [ ] 3.3 Build the Android emulators view (worktree emulators with Start/Stop, unowned running emulators with Stop); verify in the running app with one worktree emulator and one emulator started outside ShogunAi.
- [ ] 3.4 Build the iOS simulators view (owned simulators with Start/Stop, orphans with a confirmed "Delete orphans"); verify on macOS with a demo repository that declares the provider.
- [ ] 3.5 Build the DerivedData view (folders, owners, totals, unavailable volumes, confirmed "Delete orphans", compilation cache size); verify on macOS with an orphaned folder.
- [ ] 3.6 Make owners link to the worktree in "All worktrees"; verify in the running app that the worktree is selected and its detail shown.

## 4. Project view cleanup

- [ ] 4.1 Remove `GradleDaemonsSection` and `XcodeDerivedDataSection` from `ui/worktree/` and their loading from `ProjectWorktrees`, add the "Gradle" chip to the project header linking to the machine view, and keep the cleanup options working; verify with `ProjectWorktreesTest` cleanup cases and in the running app.

## 5. Strings and docs

- [ ] 5.1 Add the new strings in both languages; verify `StringsTest` passes.
- [ ] 5.2 Update the Gradle, emulator, simulator and DerivedData sections of `docs/architecture/ui.md` and `ui.es.md`; verify `mkdocs build --strict` succeeds.
- [ ] 5.3 Run the app on the secondary display with demo projects declaring each provider and check every `machine-resources` scenario, in light and dark.

## Workflow follow-up

- Archive the change (`/opsx:archive machine-resources-view`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `machine-resources-view`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
