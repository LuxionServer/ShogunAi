## Context

The desktop UI is Compose Desktop + Material3, organized as three screens
(`ProjectListScreen`, `ProjectConfigScreen`, `WorktreeScreen`) driven by
per-screen `ViewModel`s holding `mutableStateOf` fields, plus shared
components (`SectionCard`, `SegmentedSelector`, `DropdownSelector`,
`Spacing`) and a theme module (`Theme.kt`, `Color.kt`) that already defines
proper `error`/`onError` and accent tokens per color scheme (light/dark).
Several screens bypass these tokens (e.g. `Color.Red` literals in
`WorktreeScreen.kt`) or use ad hoc affordances (Unicode "▼"/"▶" instead of
Material icons in `DropdownSelector`). This is a UI-only polish change: no
domain/use-case or persistence changes are needed, only `ViewModel` state
additions and Composable changes within `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/**`.

## Goals / Non-Goals

**Goals:**
- Make error/loading/validation feedback consistent across screens and
  driven by theme tokens instead of hardcoded colors.
- Close small UX gaps found in the review (duplicate secret files, lost
  unsaved edits, no search in a growing project list, no keyboard shortcuts,
  no group-state memory in `DropdownSelector`) with minimal, local changes.
- Keep every change additive: no persisted data format, API, or domain
  model changes.

**Non-Goals:**
- Redesigning navigation (e.g. adding a router, breadcrumbs, or screen
  transition animations) — out of scope for this pass.
- Changing the light/dark/system theme model itself (`ThemeMode`) — only
  the colors used within screens are affected.
- Adding a full toast/snackbar framework — worktree-creation success
  feedback uses the simplest mechanism that fits the existing `Scaffold`-less
  layout (see Decisions).

## Decisions

- **Error color**: replace `Color.Red` literals in `WorktreeScreen.kt` with
  `MaterialTheme.colorScheme.error`, which already exists per color scheme.
  No new tokens needed.
- **Error message presentation**: wrap `viewModel.errorMessage` in a small
  row with an `Icons.Default.Error` icon and a trailing `IconButton` (Close
  icon) that clears it, instead of bare `Text`. Clearing is a pure UI
  concern (`errorMessage = null` in the `ViewModel`), no new domain state.
- **Loading indicator consistency**: reuse the existing
  `CircularProgressIndicator` pattern (small, inline, next to the label)
  for `isLoadingBranches`, replacing the "Cargando ramas..." text, so both
  loading states look the same.
- **`DropdownSelector` icons and group memory**: swap the `"▼"/"▶"` text
  for `Icons.Default.ExpandMore`/`Icons.Default.ChevronRight` (already used
  elsewhere via `androidx.compose.material.icons`). Group expansion state
  (`expandedGroups`) is currently `remember`ed inside the composable, so it
  resets each time the dropdown is recomposed from scratch (e.g. screen
  re-entry); hoist it to survive across dropdown opens within the same
  screen instance by keeping the same `remember` key scope, and additionally
  auto-expand the group containing the currently `selected` option the first
  time the dropdown opens.
- **Inline validation feedback**: add `isError`/`supportingText` to the
  "Nombre" and "Ruta del repositorio base" fields in `ProjectConfigScreen`,
  driven by simple blank-checks already available via
  `ProjectConfigViewModel.isValid`. No new validation logic — just
  surfacing the existing check per-field instead of only on the submit
  button.
- **Multi-line custom command field**: add `minLines = 3` to the custom
  command `OutlinedTextField`; the backing value is already a single
  newline-joined `String` (`customCommandTemplateText`), so no `ViewModel`
  change is needed.
- **Duplicate secret-file prevention**: change
  `ProjectConfigViewModel.addSecretFile` to check
  `fileName !in secretFiles` before appending, matching the existing
  no-op-on-blank behavior.
- **Unsaved-changes confirmation**: track a `hasUnsavedChanges` derived
  comparison (current field values vs. the initial snapshot taken at
  `ViewModel` construction) and show an `AlertDialog` confirmation from
  `onCancel` only when true, reusing the existing dialog pattern already
  used for delete confirmations.
- **Project list search/sort**: add local `searchQuery` and `sortOrder`
  state in `ProjectListScreen` (or `ProjectListViewModel`, for testability)
  filtering/sorting the already-loaded `projects` list client-side; no
  repository/query changes since the project count is expected to stay
  small (local JSON-backed catalog).
- **Collapsible "Nuevo worktree" section**: reuse `SectionCard` with an
  added optional `collapsible: Boolean` / `initiallyExpanded: Boolean`
  parameter (or a thin wrapper) rather than introducing a new component,
  keeping the visual style consistent with other sections.
- **Worktree path truncation**: add `maxLines = 1` and
  `overflow = TextOverflow.Ellipsis` to the path `Text` in `WorktreeRow`.
- **Success feedback on worktree creation**: since there is no `Scaffold`/
  `SnackbarHost` in the current layout, add a lightweight transient banner
  (reusing the same row style as the error banner, with a success color and
  auto-dismiss via `LaunchedEffect(message) { delay(...); clear() }`) rather
  than introducing a `SnackbarHost` and its host-state plumbing.
- **Keyboard shortcuts**: handle global shortcuts (e.g. Cmd/Ctrl+N for
  "new project" on the project list, Cmd/Ctrl+N for "new worktree" on the
  worktree screen) via Compose Desktop's `onPreviewKeyEvent`/
  `Modifier.onKeyEvent` at the screen root, scoped per-screen rather than a
  single global dispatcher, since each screen already owns its own
  "create" action and there is no shared app-level command registry today.

## Risks / Trade-offs

- [Hoisting `DropdownSelector` group-expansion state changes its default
  behavior for every existing call site] → Mitigation: default to the
  current "collapsed unless it's the selected option's group" behavior, so
  callers see improved defaults without needing new parameters.
- [Ad hoc success-banner instead of `SnackbarHost`] → Mitigation: keep it
  visually and structurally similar to the existing error banner so a
  future move to `SnackbarHost` (if the app adopts a `Scaffold` later) is a
  small follow-up, not a rewrite.
- [Per-screen keyboard shortcut handling could drift/duplicate logic across
  screens] → Mitigation: keep the shortcut-matching logic in one small
  shared helper in `components/`, even though dispatch stays per-screen.
- [Client-side search/sort in `ProjectListScreen` won't scale to very large
  project counts] → Mitigation: acceptable given the local, single-user
  JSON catalog; revisit only if project counts grow enough to matter.
