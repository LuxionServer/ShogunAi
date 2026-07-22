## 1. DropdownSelector grouping support

- [x] 1.1 Add `groupBy: (T) -> String? = { null }` parameter to `DropdownSelector` in `Selector.kt`
- [x] 1.2 Render a clickable `DropdownMenuItem` header whenever `groupBy(option)` differs from the previous option's group value (only when `groupBy` returns non-null)
- [x] 1.3 Verify `ProjectConfigScreen`'s existing `DropdownSelector` call (terminal emulator picker) compiles unchanged and renders without headers
- [x] 1.4 Track per-group expanded state in `DropdownSelector` (`remember { mutableStateMapOf<String, Boolean>() }`), starting collapsed; clicking a header toggles its group's state and hides/shows the options under it without closing the menu

## 2. Wire grouping into the worktree branch picker

- [x] 2.1 Pass `groupBy = { it.name.substringBefore('/', "otras") }` to the `DropdownSelector` call in `WorktreeScreen.kt`'s "Existing branch" mode

## 3. Verification

- [x] 3.1 Run `./gradlew test` for the desktop app module
- [x] 3.2 Manually run the app (`./gradlew :desktopApp:run`), open the worktree creation dialog, switch to "Existing branch", and confirm branches are grouped under clickable domain headers (e.g. "feature", "fix", "otras") that start collapsed and expand/collapse on click, and that individual branches remain selectable
