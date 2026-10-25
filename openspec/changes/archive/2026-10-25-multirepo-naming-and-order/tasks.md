# Tasks

## 1. Display name (core and CLI)

- [x] 1.1 Add `name` to `MultirepoDeclaration`, `Multirepo.folderName`, and make `Multirepo.name` the trimmed declared name or the folder's name; switch path and alias callers (`init`'s default `worktreesRoot`, `proposedAlias` callers in the add dialog and settings) to `folderName`. Verify with core tests for declared, blank and missing names and for round-tripping a declaration with and without `name`.
- [x] 1.2 Add `ConfigureMultirepoUseCase.setName` and the `name` parameter of `init`. Verify with use case tests (set, trim, clear, init with name keeps `../<folder>-worktrees`).
- [x] 1.3 CLI: parse `multirepo name <name> | --clear` and `init --name`, reject a blank name with exit 2, print the name in `show` and add `name` to `multirepoJson`, update `CliHelp`. Verify with CLI tests for each scenario of "Name a multirepo from the command line" and "Show a multirepo from the command line".
- [x] 1.4 Check the agents' context (`GroupContext`) and the Herdr workspace label use the display name. Verify with a test of the session-start context for a named multirepo.

## 2. Moved multirepos (core and CLI)

- [x] 2.1 Add `MultirepoSource.forgetMissing()` to `JsonMultirepoSource` and `FakeMultirepoSource`, removing under the lock only entries whose folder doesn't exist. Verify with infrastructure tests (missing folder removed, folder without declaration kept).
- [x] 2.2 Extract the project registration of `addRepository` into a helper and add the register path to `init` for an existing declaration: validate, reject a repository claimed by another known multirepo, remember, register missing projects with the `WorktreesFolderDecision`, report what changed; reject `--worktrees-root`/`--name` there. Verify with use case tests for each scenario of "Register an existing multirepo".
- [x] 2.3 CLI: call `forgetMissing` at the start of `multirepo init`, accept `--use-worktrees-root`/`--keep-worktrees-folder` on `init`, print "registered existing multirepo"/"already registered" and the registered projects (and their JSON). Verify with CLI tests for the moved-folder and already-known scenarios and the exit codes.

## 3. Multirepo name and registration in the app

- [x] 3.1 Add the "Name" field to the multirepo settings (placeholder: the folder's name), saved through `MultirepoSetup.setName`; English and Spanish strings. Verify with a `MultirepoSettingsState` test (set, clear, `hasChanges`) and in the real app that the sidebar and the view title update.
- [x] 3.2 Make `MultirepoSetup.inspect` report an existing declaration or its validation error, show the declared repositories in `AddMultirepoState`/`AddMultirepoDialog`, and confirm through a new `setup.register`; an already known multirepo is just selected. Verify with `AddMultirepoState` tests for the three scenarios of "Register an existing multirepo from the app".
- [x] 3.3 Call `forgetMissing` from `ShellViewModel.loadMultirepos`. Verify with a `ShellViewModel` test where a multirepo's folder disappears and its projects return to the top level.

## 4. Sidebar order

- [x] 4.1 Add `SidebarOrder` to `JsonSettingsFile.Settings` and `loadSidebarOrder`/`saveSidebarOrder` to `ShellPreferenceRepository`, its JSON implementation and fakes. Verify with a settings round-trip test and that a settings file without the key loads.
- [x] 4.2 Apply the saved order in `sidebarEntries` (top level and each folder; unknown keys ignored; unordered entries last, alphabetically). Verify with unit tests for the "persists" scenarios (restart, new project at the end, removed and re-added project).
- [x] 4.3 Add `moveSidebarEntry` and `sortSidebarAlphabetically` to `ShellViewModel`, saving the whole level. Verify with `ShellViewModel` tests for move down, move into the folder level, clamping and sorting one level while keeping the others.
- [x] 4.4 Add "Move up", "Move down" (hidden at the edges) and "Sort alphabetically" to the project and multirepo context menus in `Sidebar.kt`, for both levels and the rail; strings in English and Spanish. Verify with a UI test that the menu items appear as specified and reorder the list.
- [x] 4.5 Add drag to reorder within a level in `Sidebar.kt` (the other entries shift to show the drop place, drop kept within the level, click preserved), in the expanded and the compact sidebar. Verify with a UI test that drags a row and checks the order, and one that clicks without dragging.
- [x] 4.6 Validate in the real app (on the secondary display): drag and menu reorder in both sidebar modes, order kept after restart, new project at the end, sort alphabetically, click still selects, context menu still opens.

## 5. Docs and verification

- [x] 5.1 Update `docs/guide/multirepo.md`, `docs/guide/cli.md` and `docs/guide/desktop-app.md` and their `.es.md` translations: the `name` field and subcommand, `init` over an existing declaration and the forgotten folders, reordering the sidebar. Verify with `mkdocs build --strict`.
- [x] 5.2 Run `./gradlew test` and confirm every module passes.

## Workflow follow-up

- Implementation commit once validated, then `/opsx:archive`, removing roadmap item 6's first two bullets in `docs/roadmap.md` and `docs/roadmap.es.md` (part 3 is only added to the roadmap if it's left unfinished).
- Bring the branch up to date with `origin/develop` and open the pull request against `develop`, assigned to CiroFeliu.
