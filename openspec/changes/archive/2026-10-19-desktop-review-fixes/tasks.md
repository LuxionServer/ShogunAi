# Tasks

## 1. Worktree list

- [x] 1.1 Skip refreshes during creation and merge the created worktree by path; add a UI or holder test that refreshes during a slow creation and checks one row, and verify it passes
- [x] 1.2 Show project notices in state grouping and collapsed groups, and exclude failed projects from "Nothing active"; add `listItems` tests for both groupings and a board test, and verify they pass

## 2. Create and remove dialogs

- [x] 2.1 Show removal errors in the removal dialog, block dismissal while removing and clear the selection of a removed worktree; add UI tests and verify they pass
- [x] 2.2 Show branch loading errors with retry in the create dialog; add a UI test and verify it passes

## 3. Project settings

- [x] 3.1 Debounce the base path reload, keep the draft for the same repository and ask before discarding for another one; add view-model tests (typo fix keeps the Gradle edit, other repository asks) and verify they pass
- [x] 3.2 Validate the base path like "Add project" on Browse, and add blank and duplicate validation; add `ProjectConfigDraft` tests and verify they pass
- [x] 3.3 Reset the preview confirmation on reload; add a view-model test for conflict, reload and save, and verify it passes

## 4. Detail, shortcuts and palette

- [x] 4.1 Add stopping state for emulators and simulators and the copy path confirmation; add UI tests and verify they pass
- [x] 4.2 Disable worktree menu items without a selection or while a text field has focus; add a UI test typing Cmd/Ctrl+Backspace in the filter and verify the delete dialog doesn't open
- [x] 4.3 Show the Cmd/Ctrl+Enter hint for highlighted worktrees in the palette; add a UI test and verify it passes
- [x] 4.4 Add or reuse strings in English and Spanish and verify `StringsTest` passes

## 5. Validation

- [x] 5.1 Run `./gradlew :desktopApp:test` and verify it passes
- [x] 5.2 In the running app (secondary display), verify a slow creation with a focus switch, a failed project in the Active view, a failed removal and a path typo in settings

## Workflow follow-up

- Archive the change before opening its pull request.
