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
