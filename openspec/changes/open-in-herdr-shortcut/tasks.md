## 1. Implementation

- [ ] 1.1 Add `WorktreeBoard.openSelectedInHerdr()` next to `copySelectedLaunchCommand()`
- [ ] 1.2 Add `onOpenSelectedInHerdr` to `WorktreeActions` and pass it in `AppShell.kt`
- [ ] 1.3 Add the "Open in Herdr" item with `shortcut(Key.E)` to the Worktree menu in `AppMenuBar.kt` and wire it in `main.kt`

## 2. Tests

- [ ] 2.1 `WorktreeBoardTest`: opens the selected worktree in Herdr, does nothing without a selection, does nothing with a text field focused
- [ ] 2.2 Update `ShellViewModelTest` for the new `WorktreeActions` field

## 3. Documentation

- [ ] 3.1 Update the Shortcuts section of `docs/architecture/ui.md` and `ui.es.md`
- [ ] 3.2 Run `./gradlew test` and check the shortcut in the running app
