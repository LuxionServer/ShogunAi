## 1. Worktree screen reload

- [x] 1.1 In `ui/AppRoot.kt`, add `LaunchedEffect(current.project.id) { viewModel.refresh() }` inside the `Screen.WorktreeManagement` branch, next to the existing `viewModel(key = current.project.id) { ... }` call
- [x] 1.2 In `ui/worktree/WorktreeScreen.kt`, add a "Recargar" `OutlinedButton` to the header row (next to the "Proyectos" back button) that calls `viewModel.refresh()`, disabled while `viewModel.isLoading` is true
- [x] 1.3 In `ui/worktree/WorktreeScreen.kt`, show a loading indicator bound to `viewModel.isLoading` near the reload button

## 2. Project list screen reload

- [x] 2.1 In `ui/projectlist/ProjectListScreen.kt`, add a "Recargar" `OutlinedButton` to the header row (next to "Nuevo proyecto") that calls `viewModel.refresh()`

## 3. Window-focus reload

- [x] 3.1 In `ui/AppRoot.kt`, inside the `Screen.ProjectList` branch, add `val windowInfo = LocalWindowInfo.current` and a `LaunchedEffect(windowInfo) { snapshotFlow { windowInfo.isWindowFocused }.drop(1).filter { it }.collectLatest { viewModel.refresh() } }`
- [x] 3.2 In `ui/AppRoot.kt`, inside the `Screen.WorktreeManagement` branch, add the equivalent `LocalWindowInfo`-based effect keyed on `(current.project.id, windowInfo)`, calling `viewModel.refresh()` on focus regain

## 4. Tests

- [x] 4.1 Add a `WorktreeViewModelTest` case asserting `refresh()` re-invokes `ListWorktreesUseCase` and replaces `worktrees` with the new result (following the existing `StandardTestDispatcher` + `FakeShellCommandExecutor` pattern)
- [x] 4.2 Add a `WorktreeViewModelTest` case asserting `refresh()` surfaces a failure via `errorMessage` when `ListWorktreesUseCase` fails
- [x] 4.3 Add a `ProjectListViewModelTest` (new test file) covering `refresh()` re-reading from a fake `ProjectRepository` and replacing `projects` with the new result

## 5. Manual verification

- [x] 5.1 Run the app, open a project's worktree screen, delete a worktree/branch from another Git client, click "Recargar", and confirm the deleted entry disappears
- [x] 5.2 Run the app, open a project's worktree screen, delete a worktree/branch from another Git client, navigate back to the project list and back to the same project, and confirm the deleted entry disappears without clicking "Recargar"
- [x] 5.3 Confirm the reload button is disabled and a loading indicator is visible while a reload is in flight
- [x] 5.4 Run the app, open a project's worktree screen, switch to another app (e.g. SourceTree or Finder), delete a worktree/branch from it, switch back to ShogunAi, and confirm the deleted entry disappears without clicking "Recargar" or navigating away
- [x] 5.5 Repeat 5.4 on the project list screen (add/remove a project externally, switch away and back) and confirm the list updates on focus regain
- [x] 5.6 Confirm that switching focus away and back while already on a freshly-entered screen doesn't cause a redundant double-reload (only one `refresh()` call from re-entry, not one from re-entry plus one from the initial focus state)
