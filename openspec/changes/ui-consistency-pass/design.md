## Context

The app has three screens (`ProjectListScreen`, `ProjectConfigScreen`, `WorktreeScreen`) built incrementally across separate prior changes, each reinventing "pick one of N" as an inline `RadioButton` group with different layout (vertical, horizontal, nested, as a `LazyColumn` list). `ProjectConfigScreen` is one long unsectioned scrolling form. `WorktreeScreen` interleaves a creation form and the worktree list as items of the same `LazyColumn`, and keeps its form state (`taskId`, `branchType`, `createMode`, `selectedBranch`) local to the composable, unlike `ProjectConfigViewModel`/`ProjectListViewModel`, which own their screens' state.

Compose Multiplatform is at `1.11.1` / Material3 `1.11.0-alpha07`, so `SingleChoiceSegmentedButtonRow`/`SegmentedButton` and `ExposedDropdownMenuBox` are available (behind `@ExperimentalMaterial3Api`). No Material Icons dependency exists yet — icon buttons currently render raw emoji characters as `Text`.

An in-progress change, `system-theme-mode`, also touches `AppRoot.kt` and `ThemeModeToggle`. To avoid conflicting edits, this change does not modify `ThemeModeToggle` itself.

## Goals / Non-Goals

**Goals:**
- One reusable way to render each of the two selection shapes that recur across screens: a small fixed set of options, and a longer/dynamic list.
- Visually group `ProjectConfigScreen`'s fields into sections instead of one continuous form.
- Separate `WorktreeScreen`'s creation form from its worktree list.
- Make `WorktreeScreen` own its form state the same way the other two screens own theirs.
- Replace emoji icon buttons with real icons plus `contentDescription`.
- Preserve every existing spec scenario exactly (task id normalization while typing, reload loading-indicator behavior, branch/mode selection outcomes, terminal/agent config fields).

**Non-Goals:**
- No navigation-compose adoption (`Screen.kt`'s `when`-based navigation is untouched).
- No changes to `ThemeModeToggle`, theming, or light/dark mode (owned by `system-theme-mode`).
- No changes to domain models, use cases, or persisted JSON formats.
- No change to `WorktreeScreen`'s data-loading semantics (`isLoadingBranches`, `eligibleBranches`), only where that state now lives and how it's rendered.

## Decisions

### 1. Two reusable `Selector` composables, not one generic component

Add `ui/components/Selector.kt` with:
- `SegmentedSelector<T>(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String)` — built on Material3 `SingleChoiceSegmentedButtonRow`/`SegmentedButton`. Replaces the `TerminalSelectionMode`, `CreateMode`, and `BranchType` radio groups (all small, fixed option sets, 2-4 values).
- `DropdownSelector<T>(options: List<T>, selected: T?, onSelect: (T) -> Unit, label: (T) -> String, placeholder: String)` — built on Material3 `ExposedDropdownMenuBox`. Replaces the `TerminalEmulator` nested radio group and the eligible-branches radio-as-list. Handles the loading/empty states for the branches case via the existing `viewModel.isLoadingBranches` / `eligibleBranches.isEmpty()` checks at the call site (the component itself stays state-agnostic, taking a plain `List<T>`).

Rejected alternative: a single generic `Selector` that switches internally between segmented/dropdown based on option count. Rejected because the two have different semantics (segmented = compact/always-visible choice; dropdown = space-saving for longer lists) and forcing one API would need an escape hatch anyway.

`ThemeModeToggle` is deliberately left as its own bespoke `DropdownMenu` in this change (see Non-Goals) rather than migrated to `DropdownSelector`, to avoid touching a file `system-theme-mode` is actively changing. Migrating it is a natural follow-up once that change lands.

### 2. Shared `SectionCard` for grouped form layout

Add `ui/components/SectionCard.kt`: `SectionCard(title: String, content: @Composable ColumnScope.() -> Unit)`, rendering a title (`titleMedium`) plus an `OutlinedCard` wrapper with consistent internal padding. Used to group `ProjectConfigScreen` into "Identidad", "Rutas", "Archivos de secretos", "Terminal", "Agente" sections, and to wrap `WorktreeScreen`'s creation form as a single section above the worktree list.

### 3. Shared spacing scale

Add `ui/components/Spacing.kt` with named `Dp` constants (e.g. `Spacing.xs = 4.dp`, `.sm = 8.dp`, `.md = 16.dp`, `.lg = 24.dp`) replacing the ad hoc hardcoded padding values scattered across the three screens.

### 4. `WorktreeScreen` layout split

Change the screen root from a single `LazyColumn` mixing `item {}` (form) and `items {}` (worktree list) into: a non-scrolling header + creation `SectionCard`, followed by a `LazyColumn` containing only the worktree rows. This matches how `ProjectListScreen` already separates its header from its list.

### 5. Move creation-form state into `WorktreeViewModel`

Move `taskId`, `branchType`, `createMode`, `selectedBranch` from `remember { mutableStateOf(...) }` in `WorktreeScreen` into `WorktreeViewModel` as `var` properties backed by `mutableStateOf`, mirroring `ProjectConfigViewModel`'s pattern. The task-id setter keeps normalizing input (trim + collapse whitespace to `-`) inside the ViewModel so `worktree-workspace`'s "task id field shows the normalized value while typing" scenario is preserved unchanged; `LaunchedEffect(createMode)` triggering `loadEligibleBranches()` moves too, keyed off the ViewModel's own state instead of composable-local state.

### 6. Icons: add `material-icons-core`, not `-extended`

Add `org.jetbrains.compose.material:material-icons-core` (matching `composeMultiplatform` version) to the version catalog. `Icons.Default.Edit`, `.Delete`, and `.Close` — the three glyphs currently rendered as emoji — are all present in `-core`, so `-extended` (much larger) isn't needed. Each `IconButton` gets a Spanish `contentDescription` (e.g. "Editar proyecto", "Eliminar proyecto", "Quitar archivo de secretos") matching the app's existing Spanish UI copy.

## Risks / Trade-offs

- **Material3 `1.11.0-alpha07` experimental APIs** (`SegmentedButton`, `ExposedDropdownMenuBox` require `@OptIn(ExperimentalMaterial3Api::class)`) → contain the opt-in annotation to `Selector.kt` only, so a future breaking API change is a one-file fix, not scattered across screens.
- **`WorktreeViewModel` grows in responsibility** (list state + create-form state) → keep the two groups of properties visually separated with a comment-free naming convention (e.g. a `// creation form` grouping is unnecessary; property names already read as form fields); revisit extraction into a separate form-state holder only if it grows further, per AGENTS.md guidance against premature abstraction.
- **Touching three screens at once risks visual regressions** → tasks are scoped per screen so each can be run (`./gradlew :desktopApp:run`) and manually checked before moving to the next; existing tests for `WorktreeViewModel`/`ProjectConfigViewModel` behavior must keep passing after the state relocation.
- **Concurrent `system-theme-mode` change** touches `AppRoot.kt`/`ThemeModeToggle` → this change avoids `ThemeModeToggle.kt` entirely and only touches `AppRoot.kt` if needed for spacing constants, minimizing merge conflicts.
