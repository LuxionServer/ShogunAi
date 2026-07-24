## 1. Theme color tokens

- [x] 1.1 Add a `success` color pair (or reuse an existing accent) to `Color.kt`/`Theme.kt` for both light and dark schemes, for use by success feedback banners
- [x] 1.2 Replace `Color.Red` literals in `WorktreeScreen.kt` (main error text, task id error text) with `MaterialTheme.colorScheme.error`
- [x] 1.3 Audit disabled-button styling (`Guardar`, `Crear worktree`) against the active color scheme and adjust so disabled state remains legible/distinguishable in both light and dark

## 2. Shared `DropdownSelector` component

- [x] 2.1 Replace the `"▼"/"▶"` Unicode characters with `Icons.Default.ExpandMore`/`Icons.Default.ChevronRight`
- [x] 2.2 Hoist `expandedGroups` state so it survives across dropdown open/close within the same screen instance instead of resetting
- [x] 2.3 Auto-expand the group containing the currently `selected` option the first time the dropdown is shown
- [x] 2.4 Verify existing call sites (`WorktreeScreen`'s branch dropdown, `ProjectConfigScreen`'s terminal emulator dropdown) still behave correctly with the new state handling

## 3. Project configuration form

- [x] 3.1 Add `isError`/`supportingText` to the "Nombre" field driven by `name.isBlank()`
- [x] 3.2 Add `isError`/`supportingText` to the "Ruta del repositorio base" field driven by `baseRepositoryPath.isBlank()`
- [x] 3.3 Add `minLines = 3` to the custom terminal command `OutlinedTextField`
- [x] 3.4 Add a tooltip/help text next to "Usar Headroom" explaining what it does
- [x] 3.5 Update `ProjectConfigViewModel.addSecretFile` to skip file names already present in `secretFiles`
- [x] 3.6 Snapshot initial field values in `ProjectConfigViewModel` and expose a `hasUnsavedChanges` check
- [x] 3.7 Show a confirmation `AlertDialog` from `onCancel` when `hasUnsavedChanges` is true, reusing the existing dialog pattern; skip it and cancel immediately otherwise

## 4. Project list

- [x] 4.1 Add `searchQuery` state to `ProjectListViewModel` (or the screen) and a search `OutlinedTextField` above the list
- [x] 4.2 Filter the displayed list by case-insensitive name match against `searchQuery`
- [x] 4.3 Add a sort control (e.g. alphabetical) and apply it to the displayed list without mutating persisted order
- [x] 4.4 Show the existing empty-state message when a search yields no results, distinguishing it from the "no projects at all" case if needed

## 5. Worktree screen — loading, errors, feedback

- [x] 5.1 Replace the "Cargando ramas..." text with the same `CircularProgressIndicator` pattern used for `isLoading`
- [x] 5.2 Wrap the error message in a row with an error icon and a trailing dismiss `IconButton` that clears `viewModel.errorMessage`
- [x] 5.3 Add a transient success message state to `WorktreeViewModel`, set on successful worktree creation
- [x] 5.4 Render the success message as a banner (reusing the error banner's layout with the success color) that auto-clears via `LaunchedEffect` after a short delay
- [x] 5.5 Add a format hint/example next to the "Id de tarea" field (e.g. below the field as supporting text)
- [x] 5.6 Truncate `WorktreeRow`'s path `Text` with `maxLines = 1` and `overflow = TextOverflow.Ellipsis`

## 6. Collapsible "Nuevo worktree" section

- [x] 6.1 Add an optional collapsible mode to `SectionCard` (or a thin wrapper) with a header click target and a chevron icon
- [x] 6.2 Track expanded/collapsed state for the "Nuevo worktree" section in `WorktreeScreen` (default: expanded)
- [x] 6.3 Wire the section's content visibility to that state

## 7. Global keyboard shortcuts

- [x] 7.1 Add a small shared helper (in `components/`) for matching a key event against a platform-appropriate "new item" shortcut (Cmd+N on macOS, Ctrl+N elsewhere)
- [x] 7.2 Wire the shortcut on `ProjectListScreen` to trigger `onNewProject`
- [x] 7.3 Wire the shortcut on `WorktreeScreen` to trigger worktree creation using the current create-worktree form state, respecting the same enabled/disabled conditions as the "Crear worktree" button

## 8. Verification

- [x] 8.1 Run `./gradlew test` and fix any failures introduced by the above changes
- [x] 8.2 Run `./gradlew :desktopApp:run` and manually walk through: project search/sort, form validation feedback, cancel-with-unsaved-changes prompt, duplicate secret file, collapsible worktree form, worktree creation success feedback, dismissible error, branch dropdown group memory, and the new keyboard shortcuts
