# Tasks

## 1. Settings

- [x] 1.1 Extend `JsonSettingsFile` and its repositories with `lastDestination`, `windowBounds` and `sidebarCollapsed`, each saved without touching the other keys; verify with `JsonSettingsFileTest` cases for each key, for a file written by the previous version and for a corrupt file.

## 2. Shell state

- [x] 2.1 Add `ShellDestination` and `ShellViewModel` (current destination, catalog, previous destination for Cancel, restore with fallbacks), moving catalog loading from `ProjectListViewModel`; verify with `ShellViewModelTest`: restore of a saved project, fallback when the project was deleted, empty catalog, removing the selected project, cancel returns to the previous destination.
- [x] 2.2 Reload the catalog when the window regains focus and on View > Reload; verify with a `ShellViewModelTest` case that the catalog is re-read.

## 3. Window frame

- [x] 3.1 Build `ui/shell/AppShell.kt` with the sidebar (sections, project rows with selection, context menu with Edit and Remove using `DangerButton` in the confirmation, "Add project") and the content area hosting the worktree screen and the configuration form; verify in the running app that switching projects keeps the sidebar and reloads the worktree list.
- [x] 3.2 Add the first-launch empty state; verify by running with an empty `projects.json` under a temporary `user.home`.
- [x] 3.3 Add the compact rail (automatic below 900 dp, manual toggle with Cmd/Ctrl+Backslash, remembered); verify in the running app by resizing and by restarting with the sidebar collapsed.
- [x] 3.4 Remove the worktree screen's "Projects" back button, `ui/projectlist/`, `Screen.kt`, the top toggle row and the version footer; verify the build passes and `ProjectListViewModelTest` is replaced by `ShellViewModelTest`.

## 4. Desktop conventions

- [x] 4.1 Add the `MenuBar` (File, View, Help) with localized labels and shortcuts, and the macOS `Desktop` About and Preferences handlers; verify on macOS that About and Settings appear in the app menu, and that Cmd+R reloads while a text field is focused without inserting text.
- [x] 4.2 Add the Preferences window (theme mode, language) with a single instance; verify in the running app that changes apply live to both windows and persist after a restart.
- [x] 4.3 Add the About window with icon, name, version and repository link; verify the shown version matches `BuildInfo.VERSION`.
- [x] 4.4 Restore window bounds and maximized state with the off-screen fallback; verify by restarting after moving and resizing, and by editing `windowBounds` to an off-screen position.

## 5. Strings and docs

- [x] 5.1 Add every new string (sidebar, menus, Preferences, About, empty state, remove-project dialog) to `Strings`, `EnglishStrings` and `SpanishStrings`; verify `StringsTest` passes.
- [x] 5.2 Update the navigation section of `docs/architecture/ui.md` and `ui.es.md`, and any project-list wording in `README.md`/`README.es.md` and `docs/getting-started*.md`; verify `mkdocs build --strict` succeeds.
- [x] 5.3 Run the app on the secondary display in light and dark, wide and narrow, and capture the shell with projects, the empty state, Preferences and About; verify against the `app-shell` scenarios.

## Workflow follow-up

- Archive the change (`/opsx:archive app-shell-layout`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `app-shell-layout`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
