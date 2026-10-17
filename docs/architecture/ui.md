# UI layer

Lives in the `desktopApp` module: `desktopApp/src/main/kotlin/app/luxion/shogunai/ui`, plus `AppContainer.kt` at the package root. It uses the domain and infrastructure from the `core` module.

## Navigation

No `navigation-compose`: the main window is a shell (`ui/shell/AppShell`) with a persistent `Sidebar` and a content area. `ShellViewModel` owns the selected `ShellDestination` (`Project`, `ProjectSettings`, `AddProject`), the project catalog (sorted alphabetically) and the sidebar state, and the content area does a `when` over the destination. A sidebar app has no meaningful back stack: Cancel on a form returns to the destination selected before it, and saving selects the saved project. With no destination (no projects), the content area shows the first-launch empty state.

The selected project is saved as `lastDestination` in `settings.json` and restored on startup; forms are never restored, and a project that no longer exists falls back to the first one. The catalog is re-read when the window regains focus and on View > Reload, which also bumps `reloadRequests` so the shown view reloads its data.

The sidebar is 240 dp wide, and becomes a 64 dp rail (each project's initial with a tooltip) when the window is narrower than 900 dp or the user collapses it (View > Toggle sidebar, Cmd/Ctrl+Backslash). The manual choice is saved as `sidebarCollapsed` and only applies to wide windows; toggling on a narrow window expands it until the width changes. Each project row has a context menu with Edit project and Remove project.

`main.kt` adds the desktop conventions around the shell:

- A `MenuBar` (`AppMenuBar`) with File (Add project; Preferences and Quit off macOS), View (Reload, Toggle sidebar) and Help (About, off macOS), labelled from `Strings` so it follows the language. On macOS, `java.awt.Desktop` handlers put About and Settings… in the ShogunAi menu, and a quit handler saves the window bounds before Cmd+Q quits.
- `PreferencesWindow` (theme mode and language, a single instance brought to the front when reopened) and `AboutWindow` (icon, name, `BuildInfo.VERSION`, repository link). Every window reads the same `Appearance` holder, so a change applies to all of them at once.
- The window's bounds and maximized state are saved as `windowBounds` when it closes and restored on startup; bounds that don't intersect any connected display open the window centered instead. The minimum size is 720×480.

## Screens and ViewModels

| Screen | ViewModel | Responsibility |
|---|---|---|
| `AppShell` | `ShellViewModel` | Sidebar with the project catalog from `ProjectRepository` (alphabetical; add, edit and remove with confirmation) and the content area for the selected destination. |
| `ProjectConfigScreen` | `ProjectConfigViewModel` | Form for a `Project` (name, base repository path, worktrees root, main branch, the repository's provisioning, local files, agent command and Headroom switch); validates required fields and asks before discarding unsaved changes. |
| `WorktreeScreen` | `WorktreeViewModel` | Lists the worktrees of the active project with their status; creates them from a new or an existing branch, removes them (optionally deleting the branch, retrying with `--force` when Git asks for it) and copies their launch command. |

Each ViewModel uses `androidx.lifecycle` viewmodel-compose (`viewModelScope`) to call use cases without blocking the UI thread. Lists are reloaded when the window regains focus, so changes made outside the app (a branch created from the terminal, a worktree removed by hand) show up without pressing "Reload". `ProjectConfigViewModel` lives in a `ViewModelStore` of its own for each visit of the configuration screen (`rememberScreenViewModelStoreOwner`), cleared when the screen leaves the composition, so every visit starts from the saved project and rereads the repository.

### Creating worktrees

`WorktreeViewModel` owns the transient "New worktree" form state (`taskId`, `branchType`, `createMode`, `selectedBranch`). `updateTaskId` applies the normalization (trim + whitespace runs collapsed to `-`) before storing the value, so the field always shows the already-normalized id while typing.

In "Existing branch" mode the branches from `ListLocalBranchesUseCase` are shown in a `DropdownSelector` grouped by the segment before the first `/` (or "other" for branches without one). Branches that already have a worktree are rendered disabled and can't be selected, and "Create worktree" is only enabled for an available branch (`isSelectedBranchAvailable`).

### Worktree status

Each row shows its worktree's status under the branch name. `WorktreeViewModel` keeps it in `statuses`, a map from worktree path to `WorktreeStatusState` (`Loading`, `Loaded` or `Unavailable`):

- Once the list is read, the main branch is resolved with `ResolveMainBranchUseCase` (exposed as `mainBranch`) and every worktree's status is read with `ReadWorktreeStatusUseCase`, at most 4 at a time. The list shows right away and each row shows a small progress indicator until its status arrives.
- A worktree whose status can't be read shows "Status unavailable" without affecting the others or the screen's error. A worktree whose folder is gone (`isPrunable`) shows "Folder missing" instead, and a locked one (`isLocked`, e.g. in use by an agent session) shows a neutral "locked" chip next to its branch.
- Reloading the list, including when the window regains focus, reads every status again; results from an older load are dropped. Creating a worktree reads only the new one's status, and removing one drops its status.

`WorktreeStatusLine` renders the status as status chips followed by segments separated by `·` (`statusSegments`). Uncommitted changes (only when there are any) are a warning chip, and a merged branch shows a "merged into <main branch>" success chip instead of its main branch divergence. The segments are `↑N ↓N upstream`, `↑N ↓N <main branch>` and the last commit date as relative time (`relativeTime`, in `RelativeTime.kt`).

### Auto refresh

While `WorktreeScreen` is shown, a `DisposableEffect` calls `WorktreeViewModel.startWatching()`, and `stopWatching()` when it leaves the composition. Every 3 seconds the ViewModel reads `ReadGitMetadataFingerprintUseCase`: if the fingerprint differs from the last one it calls `refresh()`; otherwise it only runs `DetectRunningAgentsUseCase` again, so agents starting or ending show up without a full reload. A check is skipped while the list is loading or a create, remove or cleanup action is running. `refresh()` stores the fingerprint it starts from, so a reload triggered by the app itself isn't repeated on the next check. Focus and manual reloads keep working as before.

### Worktree overview

`WorktreeScreen` receives the project's `ProjectConfig` to classify each row's origin with `classifyOrigin`, together with Herdr's worktrees folder, which `WorktreeViewModel` reads on each list load (`herdrWorktreesRoot`, through `WorktreeUseCases.herdr`). After each list load, `WorktreeViewModel` also runs `DetectRunningAgentsUseCase` once (`agents`, `null` when processes can't be listed) and measures every worktree's size one at a time with `MeasureWorktreeSizeUseCase` (`sizes`, added up in `totalSize`); stale results are dropped with `listGeneration`. It also reads each worktree's reserved ports (`ports`, through `WorktreeUseCases.readPorts`, which resolves the environment with `reserve = false`, so opening the screen never reserves a port) and `WorktreeOverviewLine` shows them after the size with `portsLabel` (`PORT 20001 · api 20002`).

- `WorktreeBranchLine` shows, after the branch and the "locked" label, the origin (ShogunAi, Claude Code, Herdr or other location) in muted text and each running agent as an info chip with its name.
- `WorktreeOverviewLine`, under the status, shows the size (`formatSize`, binary units with one decimal) and an "inactive" label when `isInactive` holds.
- The "Worktrees" title is followed by "N worktrees · <total size>".

### Cleaning up worktrees

The "Clean up" button next to the "Worktrees" title is enabled when `cleanupCandidates` (from `selectCleanupCandidates` over the listed worktrees and their loaded statuses) isn't empty. It opens `CleanupDialog`, which lists the candidates by folder name and branch and offers "Also delete their local branches", unchecked by default. Confirming runs `CleanUpWorktreesUseCase`, shows a progress indicator with the dialog's actions disabled, then closes the dialog, shows "N worktrees cleaned up" as a success message and reloads the list. Failures and kept branches stay visible in `CleanupIssues` until dismissed.

When the repository declares the Gradle provider, the dialog also offers "Also stop the idle Gradle daemons of the worktrees being removed", checked by default (`cleanupStopGradleDaemons`): stopping an idle daemon only costs a restart on the next build. Before cleaning up, `confirmCleanup` lists the daemons with the worktrees as they were; after the removals it stops the idle ones whose last build was in a removed worktree (`idleDaemonsOfRemovedWorktrees`, `StopGradleDaemonsUseCase`). The success message then adds how many daemons were stopped and kept. A daemon that can't be listed or stopped never turns the cleanup into a failure.

### Android emulators

Only for repositories whose `.shogunai.json` declares the emulator provider (`EmulatorUseCases.declared`, read on every list load); otherwise nothing below is shown and no Android tool runs.

- **Worktree row**: `WorktreeViewModel` loads `emulators` (`ReadEmulatorStatesUseCase`, one `adb devices` for all rows, no reservation) after each list load and on each auto-refresh tick without changes. `EmulatorLine` shows `Emulator · emulator-5556 · running` with Stop, or Start when stopped or not reserved yet. `startEmulator(worktree)` marks the worktree in `startingEmulators` (a progress indicator replaces the button), runs `StartWorktreeEmulatorUseCase` (which reserves the port if needed and waits for the boot), then reloads emulators and ports; errors go to the screen's error banner.
- **Cleanup**: the dialog adds "Also stop the emulators of the worktrees being removed", checked by default (`cleanupStopEmulators`). The serials of the candidates' running emulators are taken from the loaded states before removing (removal deletes their reservations) and stopped after (`stopSerials`); the success message adds how many were stopped.
- **Project configuration**: `EmulatorSettingsSection` shows the "Android emulator" section when `ProjectConfigViewModel.emulatorSection` isn't `Hidden`: the AVD list loaded in the background through `AppContainer.loadAvds` (with "Automatic" first, and a saved AVD the machine lacks marked as missing), the "Run without a window" switch, and the SDK-not-found message with the places checked. The settings are saved in `ProjectConfig.emulator` and count as unsaved changes. The section follows the base repository path as it's edited.

### iOS simulators

Only on macOS and for repositories whose `.shogunai.json` declares the iOS simulator provider (`SimulatorUseCases.declared`, read on every list load); otherwise nothing below is shown and no Xcode tool runs.

- **Worktree row**: `WorktreeViewModel` loads `simulators` (`ReadSimulatorStatesUseCase`, one `simctl list devices` for all rows, nothing created) after each list load. `SimulatorLine` shows `iOS simulator · booted` with Stop, or Start when it's shut down or doesn't exist yet. `startSimulator(worktree)` marks the worktree in `startingSimulators`, runs `StartWorktreeSimulatorUseCase` (which creates the simulator if needed, waits for the boot and opens it in Simulator or Device Hub) and reloads the states; errors go to the screen's error banner.
- **Cleanup**: the dialog adds "Also delete the iOS simulators of the worktrees being removed, and orphaned ShogunAi simulators", checked by default (`cleanupDeleteSimulators`), and lists the orphaned ShogunAi simulators (`orphanedSimulators`, loaded with the list) that will be deleted too. "Clean up" is enabled when there are orphans even without candidates (`canCleanUp`). The candidates' UDIDs are taken from the loaded states before removing and deleted after, with the orphans, looked for again after removing (a worktree whose folder was already gone has no readable simulator before, and becomes an orphan once removed); the success message adds how many were deleted.
- **Project configuration**: `SimulatorSettingsSection` shows the "iOS simulator" section when `ProjectConfigViewModel.simulatorSection` isn't `Hidden`: runtime and device dropdowns built from the machine's iOS runtimes (`AppContainer.simulatorSetup`, "Automatic" first, a saved name the machine lacks marked as missing), and, in a Kotlin Multiplatform repository whose KMP tests aren't wired (`KmpTestWiring`), the repository snippet with Copy and "Install for every project on this machine", which installs the Gradle init script only after a confirmation dialog. The settings are saved in `ProjectConfig.simulator` and count as unsaved changes.

### Open in Herdr

Each worktree row whose folder exists has an "Open in Herdr" button before "Copy command". `openInHerdr(worktree)` marks the worktree in `openingInHerdr` (the button is disabled meanwhile) and runs `WorktreeUseCases.herdr.open` (`OpenWorktreeInHerdrUseCase`, built in `AppContainer` with `CliHerdrClient`) with that worktree. The success message says the worktree was opened with its agent, or that it was already open and Herdr switched to it (`WorktreeSuccess.OpenedInHerdr`); a missing `herdr` or a failing command goes to the error banner, with the link to herdr.dev or Herdr's own message.

### Gradle daemons

When `.shogunai.json` declares `"providers": { "gradle": {} }` (`GradleUseCases.provider`, read on every list load), `WorktreeScreen` shows `GradleDaemonsSection` below the worktree list. `WorktreeViewModel` loads `gradleDaemons` (`Loading`, `Loaded` or `Unavailable` when processes can't be listed) with `ListGradleDaemonsUseCase` after each list load and on every auto-refresh check without changes, like the running agents, and reads `isGradleBuildCacheEnabled` with `CheckGradleBuildCacheUseCase`.

The section shows the number of daemons and their total memory, one row per daemon (kind and version, PID, memory, "busy" / "idle · 12 minutes ago" / "state unknown", and the worktree's folder name, "removed worktree" or "other project"), a "Stop" button per row and "Stop idle daemons". A button is enabled only for daemons the stop rules allow (`isStoppable`: an idle Gradle daemon, or a Kotlin daemon while no Gradle daemon is busy or unknown); `StopGradleDaemonsUseCase` checks the state again anyway. The result (`daemonStopReport`) stays visible until dismissed, and the list is reloaded. Without the build cache, an informational note explains how to turn it on. When the provider declares settings (`gradleSettings`), a line states them for worktree builds, e.g. "Worktree builds: 2 GB heap · idle after 30 min · Kotlin 1 GB" (`formatMemorySetting` turns `2g` into "2 GB").

### Xcode DerivedData

Only on macOS and for repositories whose `.shogunai.json` declares the Xcode provider (`XcodeUseCases.provider` and `supported`, read on every list load); otherwise nothing below is shown and no Xcode tool runs.

- **Section**: `WorktreeViewModel` loads `derivedData` (`Loading`, `Loaded(listing)` or `Unavailable`) with `ListDerivedDataUseCase` after each list load, but not on the auto-refresh checks: measuring gigabytes of DerivedData on every check would be too expensive. `XcodeDerivedDataSection` shows the number of the worktrees' folders and their total size, one row per folder (worktree folder name, size, last access), the orphans' count and size with "Delete orphans" (disabled without orphans), the folders on unavailable volumes as information, and the shared compilation cache size when caching is declared. `deleteOrphanedDerivedData()` runs `DeleteOrphanedDerivedDataUseCase`, keeps the result in `derivedDataDeletionReport` until dismissed and reloads the section.
- **Cleanup**: the dialog adds "Also delete orphaned Xcode DerivedData", checked by default (`cleanupDeleteDerivedData`), with the orphans already found. "Clean up" is enabled when there are orphans even without candidates (`canCleanUp`). After the removals, `DeleteOrphanedDerivedDataUseCase` deletes the orphans, the removed worktrees' folders included; the success message adds the folders deleted and the space freed.

### Launch command

Each worktree row has a "Copy command" button that calls `WorktreeViewModel.copyLaunchCommand(worktree)`; the button shows a "Copied" state for a couple of seconds. The command (`cd "<path>" && <agent command>`) is pasted by the user in the terminal of their choice.

### Repository provisioning and local files

The configuration form has two file sections:

- **From the repository** (read-only): the patterns of the base repository's `.shogunai.json` and `.worktreeinclude`, each with the file it comes from. `ProjectConfigViewModel.repositoryProvisioning` holds a `RepositoryProvisioningState` (`None`, `Declared`, or `Unreadable` with the file and the parse error), read through `RepositoryProvisioningSource` when the form opens and every time the base repository path changes.
- **Local files**: the project's `secretFiles`, exact paths stored only in `~/.shogunai/projects.json`.

"Move to .shogunai.json" (`moveLocalFilesToRepository`) is offered when some local file is inside the repository (relative, not starting with `..`) and `.shogunai.json` isn't unreadable. It turns those entries into patterns anchored to the root (`local.properties` → `/local.properties`), lists them under "From the repository" marked "on save", and removes them from the local list; absolute entries stay local. Nothing is written yet: pending patterns count as an unsaved change, so cancelling discards them. `save()` returns `Result<Project>`: it first appends the pending patterns with `addCopyPatterns`, then saves the project; if writing the repository file fails, the project isn't saved and the screen shows the error above the buttons, keeping the form open.

### Moving a repository

When the user edits a project because its repository moved on disk:

- `ProjectConfigViewModel` turns absolute local file entries inside the base repository into relative ones, so they follow the repository.
- Changing the base repository path also moves the worktrees root when it was inside the repository, its parent or a sibling folder, unless the user edited it by hand (`RepositoryRelocation.kt`).
- After saving with a different base path or worktrees root, `ShellViewModel.onProjectSaved` selects the project with the previous worktrees root, and `WorktreeViewModel.repairAfterMove` runs `RepairWorktreesUseCase` before listing. A failed repair stays visible as an error.
- The form and worktree ViewModels are keyed by the project's configuration, not only its id, so an edited project never reuses use cases bound to the old paths.

List loads are tagged with a generation number: a result is only applied if no newer load started meanwhile, so a slow earlier load can't overwrite a newer state (or clear a newer error).

## Shared components (`ui/components`)

- **`Spacing`**: spacing scale (`xxs=2dp`, `xs=4dp`, `sm=8dp`, `md=16dp`, `lg=24dp`, `xl=32dp`) used instead of ad hoc `dp` values. Component-internal sizes, such as icon sizes, are named constants.
- **`SegmentedSelector<T>`**: segmented control (`SingleChoiceSegmentedButtonRow`/`SegmentedButton`) for a small, fixed set of options that should all be visible at once (e.g. `CreateMode`, `BranchType`). Labels are sized to their full text (`Modifier.width(IntrinsicSize.Max)`): the row sizes its segments from their minimum intrinsic width, which for plain text is only its longest word.
- **`DropdownSelector<T>`**: dropdown (`ExposedDropdownMenuBox`) for longer or dynamically loaded lists, with optional collapsible groups (`groupBy`) and disabled options (`enabled`), which are ignored whatever the input method.
- **`SectionCard`**: titled `OutlinedCard`, used to group sections of a long form (every section of `ProjectConfigScreen`, and the "New worktree" block of `WorktreeScreen`).
- **Keyboard shortcuts** (`KeyboardShortcuts.kt`): `onEnterKey` triggers the main action of a text field (e.g. adding a local file), and `onNewItemShortcut` binds Cmd+N (macOS) / Ctrl+N (other systems) to "New project" and "Create worktree".

## Theme (`ui/theme`)

`ShogunAiTheme` applies the light or dark color scheme from the stored `ThemeMode`, chosen in the Preferences window. In `SYSTEM` mode, `rememberSystemInDarkTheme` polls the OS theme every second, because Compose Desktop's `isSystemInDarkTheme()` doesn't react to changes while the app is open.

## Design system

The theme and the shared components form the app's design system. Screens use its tokens and components instead of literal colors, sizes or per-screen copies.

### Palette

Every Material 3 color role of the light and dark schemes is defined by the app (`ShogunLightColorScheme`, `ShogunDarkColorScheme` in `Theme.kt`), so no dialog, menu or selected control falls back to Material's baseline purple. The roles come from one cool neutral ramp (`Neutral*` in `Color.kt`) and one brand accent. The brand red is `primary`: it's used for the main action of a view and for brand marks, never to convey a status. It's slightly darker in the light scheme, so white text on it stays readable.

Status colors are separate tokens with one meaning each, exposed as `ShogunTheme.status` (`ShogunStatusColors`). Each status has a `color` for text and icons, an `onColor`, and a `container`/`onContainer` pair for chips and notices:

| Status | Meaning | Examples |
|---|---|---|
| success | done or healthy | merged, device running, operation succeeded |
| warning | needs your attention, not broken | uncommitted changes, inactive |
| info | work in progress | agent running, device booting |
| danger | failed or destructive | errors, missing folder, delete actions |
| neutral | plain state | locked, device stopped |

The mapping from worktree indicators and device states to a status lives in `StatusMapping.kt`.

Contrast is checked by tests, not by eye: `ThemeContrastTest` uses `contrastRatio` (WCAG relative luminance) to assert that text reaches 4.5:1 and outlines 3:1 on every surface, in both schemes, and `ColorSchemeTest` asserts that no role keeps Material's baseline value.

### Type

The app bundles Geist for UI text and Geist Mono for data, in weights 400, 500 and 600, under `desktopApp/src/main/resources/fonts/` with their SIL Open Font License (`OFL.txt`). Bundling them makes text render the same on macOS, Windows and Linux. Every `Typography` slot uses Geist, with sizes tuned for a dense desktop tool. `ShogunTheme.typography` adds two styles for data, `data` and `dataSmall`, in Geist Mono with tabular figures: paths, branches, ports, PIDs, sizes, counts and relative times use them, so values that refresh don't shift the layout.

### Shapes and focus

`ShogunShapes` sets the corner radius scale (`extraSmall` 4, `small` 6, `medium` 10, `large` 14, `extraLarge` 20 dp). The theme raises the focused state layer of Material's ripple, so keyboard focus is visible on every Material control.

### Shared components

- **`InlineProgress`**: the one inline spinner, in `Small` and `Medium` sizes.
- **`Notice`**: error, warning, success and info messages with their status icon and color, the message and a dismiss action. It has a `details` slot for extra content.
- **`StatusChip`**: a compact label on a status container color, with an optional icon.
- **`DangerButton`, `DangerTextButton`**: destructive actions, where they're offered and in the confirmation that follows. **`NeutralTextButton`** is the text button for neutral actions such as "Cancel", so the brand color stays reserved for main actions.
- **`IconAction`**: an icon-only action whose label is both its tooltip and its accessible description.
- **`Modifier.interactive`**: makes a custom row or header clickable, with a hover state, the hand pointer and a focus ring (`Modifier.focusRing`).

## `AppContainer`

Instantiates `JsonProjectRepository`, the settings repositories, `NioFileManager`, `ProcessBuilderShellCommandExecutor` and `AwtClipboardWriter` once at app startup. The worktree use cases are scoped to a project, so `worktreeUseCases(config)` builds a `WorktreeUseCases` bundle for the selected `ProjectConfig`, including its `GradleUseCases` over a single `ProcessGradleDaemonSource`. There's no DI framework.

## `FilePicker`

Wraps `JFileChooser` for the "Browse…" buttons on the configuration screen (base repo path, worktrees folder, local files). Chosen over a native Compose picker because Compose Desktop doesn't ship one and `JFileChooser` is already available on the JVM target with no new dependencies. Both dialogs show hidden files (`isFileHidingEnabled = false`), so dotfiles such as `.env` can be picked. `relativeToBase` converts the absolute path of a chosen local file into the path relative to the repo that `ProjectConfig.secretFiles` expects.

## Languages (`ui/i18n`)

The UI is available in English (default) and Spanish. Every visible text lives in `interface Strings`, with one implementation per language (`EnglishStrings`, `SpanishStrings`); texts with parameters are functions (`deleteProjectMessage(projectName)`), so arguments are checked at compile time.

- `Appearance` loads the stored `AppLanguage` (`LanguagePreferenceRepository`) and `AppearanceScope` provides the active table with `CompositionLocalProvider(LocalStrings provides ...)` in every window: switching language recomposes texts, including the menu bar, without recreating ViewModels or losing screen state.
- Composables read texts with `val strings = LocalStrings.current`. Non-composable code (such as `FilePicker`) receives the already-translated text as a parameter.
- The language selector in Preferences shows each language by its own name ("English", "Español").
- ViewModels expose data, not messages: `WorktreeViewModel` exposes `error: Throwable?` and `success: WorktreeSuccess?` (worktree created, worktrees repaired after a move), and the screen formats them with `strings.worktreeError(error)` and the matching `Strings` text. `worktreeError` is exhaustive over the sealed `WorktreeError` hierarchy, so a new variant doesn't compile until it's translated; a process that can't start (`CommandExecutionException`) gets its own message, and anything else falls back to a generic one. Git's error output is shown as-is, untranslated.

**Adding a text**: declare the member in `Strings` and implement it in every language (the compiler flags any missing one). **Adding a language**: new `AppLanguage` value, new `object` implementing `Strings`, and its branch in `AppLanguage.strings()`.
