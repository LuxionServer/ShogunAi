# UI layer

Lives in `desktopApp/src/main/kotlin/app/luxion/shogunai/ui`, plus `AppContainer.kt` at the package root. Added in the `project-worktree-ui` change (see `openspec/changes/archive/`).

## Navigation

No `navigation-compose`: `AppRoot` holds a `var screen by remember { mutableStateOf<Screen>(...) }` and does a `when` over a `sealed class Screen` (`ProjectList`, `ProjectConfigForm`, `WorktreeManagement`) to decide which screen to compose. Enough for three linear screens with no deep linking or complex back-stack.

## Screens and ViewModels

| Screen | ViewModel | Responsibility |
|---|---|---|
| `ProjectListScreen` | `ProjectListViewModel` | Loads projects via `ProjectRepository` on start; lets you select one or create a new one. |
| `ProjectConfigScreen` | `ProjectConfigViewModel` | `ProjectConfig` form (name, paths, secret files, terminal preference, agent command); validates required fields before enabling save. |
| `WorktreeScreen` | `WorktreeViewModel` | Lists, creates, and removes worktrees for the active project, and opens a terminal at a worktree's path via the existing use cases; translates `WorktreeError` into user messages. |

Each ViewModel uses `androidx.lifecycle` viewmodel-compose (`viewModelScope`) to call use cases without blocking the UI thread.

`WorktreeViewModel` also owns the transient "New worktree" form state (`taskId`, `branchType`, `createMode`, `selectedBranch`), for consistency with how `ProjectConfigViewModel` owns its form state: `updateTaskId` applies the normalization (trim + whitespace runs collapsed to `-`) before storing the value, so the field always shows the already-normalized id while typing.

`WorktreeRow` has a "Terminal" button next to "Delete" that calls `WorktreeViewModel.openTerminal(worktree)`. `ProjectConfigScreen` has a "Terminal" section (`SegmentedSelector` over `TerminalSelectionMode`, with a `DropdownSelector` of `TerminalEmulator` when `FIXED` and a text field for the argv template when `CUSTOM`) and an "Agent" section (agent command, Headroom switch).

## Shared components (`ui/components`)

Introduced in the `ui-consistency-pass` change to unify look & feel across the three screens:

- **`Spacing`**: spacing scale (`xs=4dp`, `sm=8dp`, `md=16dp`, `lg=24dp`) used instead of ad hoc `dp` values.
- **`SegmentedSelector<T>`**: segmented control (`SingleChoiceSegmentedButtonRow`/`SegmentedButton`) for a small, fixed set of options that should all be visible at once (e.g. `TerminalSelectionMode`, `CreateMode`, `BranchType`).
- **`DropdownSelector<T>`**: dropdown (`ExposedDropdownMenuBox`) for longer or dynamically loaded lists (e.g. `TerminalEmulator`, branches eligible to create a worktree from).
- **`SectionCard`**: titled `OutlinedCard`, used to visually group sections of a long form (every section of `ProjectConfigScreen`, and the "New worktree" block of `WorktreeScreen`).

## `AppContainer`

Instantiates `JsonProjectRepository`, `NioFileManager`, `ProcessBuilderShellCommandExecutor`, `ProcessTerminalLauncher`, `SystemTerminalEmulatorDetector`, and the worktree use cases once at app startup, and exposes them to `AppRoot` to build each ViewModel. There's no DI framework.

## `FilePicker`

Wraps `JFileChooser` for the "Browse…" buttons on the configuration screen (base repo path, worktrees folder, secret files). Chosen over a native Compose picker because Compose Desktop doesn't ship one and `JFileChooser` is already available on the JVM target with no new dependencies. `relativeToBase` converts the absolute path of a chosen secret file into the path relative to the repo that `ProjectConfig.secretFiles` expects.

## Languages (`ui/i18n`)

The UI is available in English (default) and Spanish. Every visible text lives in `interface Strings`, with one implementation per language (`EnglishStrings`, `SpanishStrings`); texts with parameters are functions (`deleteProjectMessage(projectName)`), so arguments are checked at compile time.

- `AppRoot` loads the stored `AppLanguage` (`LanguagePreferenceRepository`) and provides the active table with `CompositionLocalProvider(LocalStrings provides language.strings())` above the screen `when`: switching language recomposes texts without recreating ViewModels or losing screen state.
- Composables read texts with `val strings = LocalStrings.current`. Non-composable code (such as `FilePicker`) receives the already-translated text as a parameter.
- `LanguageToggle`, next to `ThemeModeToggle` in the top bar, shows each language by its own name ("English", "Español").
- ViewModels expose data, not messages: `WorktreeViewModel` exposes `error: Throwable?` and `success: WorktreeSuccess?` (worktree created, worktrees repaired after a move), and the screen formats them with `strings.worktreeError(error)` and the matching `Strings` text. `worktreeError` is exhaustive over the sealed `WorktreeError` hierarchy, so a new variant doesn't compile until it's translated. Git's error output is shown as-is, untranslated.

**Adding a text**: declare the member in `Strings` and implement it in every language (the compiler flags any missing one). **Adding a language**: new `AppLanguage` value, new `object` implementing `Strings`, and its branch in `AppLanguage.strings()`.
