## 1. Shared foundations

- [ ] 1.1 Add `org.jetbrains.compose.material:material-icons-core` to `gradle/libs.versions.toml` and wire it into `desktopApp`'s dependencies.
- [ ] 1.2 Create `ui/components/Spacing.kt` with the shared spacing scale (`xs`/`sm`/`md`/`lg`).
- [ ] 1.3 Create `ui/components/Selector.kt`:
  - [ ] 1.3.1 `SegmentedSelector<T>` using `SingleChoiceSegmentedButtonRow`/`SegmentedButton` (`@OptIn(ExperimentalMaterial3Api::class)` contained to this file).
  - [ ] 1.3.2 `DropdownSelector<T>` using `ExposedDropdownMenuBox`, accepting a plain `List<T>` and exposing `selected`/`onSelect`/`label`/`placeholder`.
- [ ] 1.4 Create `ui/components/SectionCard.kt`: `SectionCard(title: String, content: @Composable ColumnScope.() -> Unit)` built on `OutlinedCard`.

## 2. ProjectConfigScreen

- [ ] 2.1 Wrap the existing fields into `SectionCard`s: "Identidad" (name), "Rutas" (base repo path, worktrees root), "Archivos de secretos", "Terminal", "Agente".
- [ ] 2.2 Replace the `TerminalSelectionMode` radio group with `SegmentedSelector`.
- [ ] 2.3 Replace the nested `TerminalEmulator` radio group with `DropdownSelector`.
- [ ] 2.4 Replace hardcoded padding values with `Spacing` constants throughout the screen.
- [ ] 2.5 Manually verify via `./gradlew :desktopApp:run`: create a project, edit an existing one, switch terminal mode between auto/fixed/custom, save, confirm persisted `ProjectConfig` matches what was selected (per `project-configuration` spec scenarios).

## 3. WorktreeScreen

- [ ] 3.1 Move `taskId`, `branchType`, `createMode`, `selectedBranch` from composable-local `remember { mutableStateOf(...) }` into `WorktreeViewModel` as `mutableStateOf`-backed properties; move the task-id normalization (trim + collapse whitespace to `-`) into the ViewModel's setter.
- [ ] 3.2 Move the `LaunchedEffect(createMode) { loadEligibleBranches() }` trigger to key off `viewModel.createMode` instead of local state.
- [ ] 3.3 Split the screen root: header + `SectionCard` for "Nuevo worktree" (non-scrolling), followed by a `LazyColumn` containing only the worktree rows.
- [ ] 3.4 Replace the `CreateMode` and `BranchType` radio groups with `SegmentedSelector`.
- [ ] 3.5 Replace the eligible-branches radio-as-list with `DropdownSelector`, keeping the existing loading/empty-state text checks (`isLoadingBranches`, `eligibleBranches.isEmpty()`) at the call site.
- [ ] 3.6 Replace hardcoded padding values with `Spacing` constants throughout the screen.
- [ ] 3.7 Update/add `WorktreeViewModel` unit tests to cover the relocated state (task-id normalization, create-mode-triggered branch loading) if not already covered.
- [ ] 3.8 Manually verify via `./gradlew :desktopApp:run`: create a worktree from a new branch (with a task id containing spaces, confirming the field shows the normalized value while typing), create one from an existing branch, remove a worktree (including the force-removal dialog path), reload.

## 4. ProjectListScreen

- [ ] 4.1 Replace the emoji `IconButton`s (✎, 🗑) with `Icons.Default.Edit` / `Icons.Default.Delete` plus Spanish `contentDescription`s.
- [ ] 4.2 Manually verify via `./gradlew :desktopApp:run`: list, edit, and delete a project.

## 5. Cleanup and docs

- [ ] 5.1 Replace the remaining emoji icon (✕ in `ProjectConfigScreen`'s secret-file removal) with `Icons.Default.Close` plus `contentDescription`.
- [ ] 5.2 Run `./gradlew test` to confirm no regressions in `ProjectConfigViewModel`/`WorktreeViewModel`/`ProjectListViewModel` tests.
- [ ] 5.3 Update `docs/architecture/ui.md` and `ui.en.md` to describe the new shared components (`Selector`, `SectionCard`, `Spacing`) and `WorktreeViewModel`'s expanded state ownership.
