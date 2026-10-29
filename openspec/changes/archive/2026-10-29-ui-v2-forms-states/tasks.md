# Tasks

## 1. Components

- [x] 1.1 Add `ViewTitle`, `SectionTitle` and `GroupTitle` with `heading()` semantics, and use `SectionTitle` in `SectionCard`; verify a UI test asserting heading semantics on each and on a `SectionCard` title
- [x] 1.2 Add `Field` and `PathField` (label above, 32 dp input, help or error below, focus tracking built in) and make `DropdownSelector`'s anchor a read-only `Field`; verify UI tests for label and help, error replacing help, "Browse…" invoking its callback, typing through the field, and the existing `SelectorUiTest` still passing
- [x] 1.3 Add `ShogunDialog` with `DialogSize` (400, 520, 680), heading title, scrollable body, `notices` slot, `dismissible` and end-aligned footer; verify UI tests for heading semantics, footer order (dismiss before confirm) and a body taller than the window scrolling with the footer visible (Escape keeps `BasicAlertDialog`'s own behavior)
- [x] 1.4 Add `EmptyState`, `ErrorState` (extracting the "Technical details" disclosure from `ErrorNotice` into a shared internal composable over plain text) and `SkeletonRows`; verify UI tests for one action plus optional link, details folded until clicked, and skeleton rows exposing a single "Loading" description with no click action
- [x] 1.5 Add `rememberInlineConfirm`, `InlineConfirmButton` and `InlineConfirmIconAction`; verify a UI test with the test clock: "Copied" right after the click, the original label after 1.5 s
- [x] 1.6 Add `DisabledReason` (tooltip plus state description, nothing when the reason is null); verify a UI test hovering a disabled button shows the reason, and that a null reason adds no tooltip
- [x] 1.7 Add `groupNotices` and `GroupedNotice` (summary, count, "Details" disclosure, one dismiss); verify unit tests for grouping by kind and source in first-appearance order, and a UI test for the count, the folded details and the single dismiss
- [x] 1.8 Add the new strings (`loading`, `details`, `browse`, notice count, released summary, every disabled reason) to `Strings`, `EnglishStrings` and `SpanishStrings`; verify `./gradlew :desktopApp:compileKotlin` (the interface enforces parity) and `StringsTest` cases for the pluralized summaries in both languages

## 2. Forms

- [x] 2.1 Migrate `projectconfig/ProjectSettingsView.kt`, `RepositoryConfigSection.kt`, `EmulatorSettingsSection.kt` and `SimulatorSettingsSection.kt` from `OutlinedTextField` to `Field`/`PathField`, and the simulator snippet copy to `InlineConfirmButton`; verify `LocalEntriesUiTest`, `ProjectConfigViewModelTest` and the other projectconfig tests pass unchanged
- [x] 2.2 Migrate `multirepo/MultirepoSettingsView.kt`, `multirepo/AddMultirepoDialog.kt` and `multirepo/NewChangeDialog.kt` to `Field`; verify `MultirepoViewUiTest` and `MultirepoSetupTest` pass
- [x] 2.3 Migrate `shell/PreferencesWindow.kt` and `shell/ResourcesSection.kt` to `Field`; verify `ResourcesSectionUiTest`, `ToolsSectionUiTest` and `InactivitySectionUiTest` pass
- [x] 2.4 Migrate `worktree/CreateWorktreeDialog.kt`'s task field and the worktree view's text filter to `Field`; verify `CreateGuardTest` and `WorktreeHeaderUiTest` pass

## 3. Dialogs

- [x] 3.1 Move `AddMultirepoDialog`, `NewChangeDialog` and `CreateWorktreeDialog` from `BasicAlertDialog` to `ShogunDialog` (Medium or Large, `scrollable = false` where the body has its own scroll); verify their UI tests and the Spanish button-label tests pass
- [x] 3.2 Move the confirmation `AlertDialog`s of `machine/ComposeProjectsView.kt`, `machine/DesktopTurnView.kt`, `machine/MachineViewParts.kt`, `multirepo/MultirepoSettingsView.kt`, `projectconfig/ProjectSettingsView.kt`, `projectconfig/SimulatorSettingsSection.kt`, `shell/AppShell.kt` (discard changes, remove project), `shell/ClaudeHooksViews.kt`, `shell/TourViews.kt` and `shell/UpdateViews.kt` to `ShogunDialog`; verify `ClaudeHooksUiTest`, `TourUiTest`, `UpdateViewsUiTest`, `DesktopTurnViewUiTest`, `XcodeDerivedDataViewUiTest` pass and no `AlertDialog` import remains outside the files of 8.1

## 4. States

- [x] 4.1 Replace the first load of a project group (`GroupState` on `ProjectLoadState.Loading`) with `SkeletonRows`, keeping rows during reloads; verify `SurfaceStatesUiTest` for the skeleton (one "Loading" node, nothing clickable); reloads keep rows because `loadState` only reports `Loading` before `hasLoaded`, as before
- [x] 4.2 Turn `NothingActive`, `NoMatch` and the first-launch screen into `EmptyState`; verify UI tests for their sentence, action and link
- [x] 4.3 Show `SkeletonRows` in the summary panel until every shown project has loaded once; verify a UI test with a loading project
- [x] 4.4 Give the worktree view title, the project, state and change group headers and `MachineViewFrame` their title components; verify a UI test listing the headings of the worktree view header and a group header

## 5. Machine

- [x] 5.1 Add `MachineResources.dismissNotices(resource)` and group the pool notices of each view into one `GroupedNotice` (trims summed within the last limit, releases counted, single notices unchanged); verify `MachineResourcesTest` for three trims, a release with two trims, and dismissing the group
- [x] 5.2 Show Compose's unavailable state as `ErrorState` with "Try again" calling a new `MachineResources.retryComposeProjects()` and the reason in the technical details; verify `MavenAndComposeMachineTest` for the retry re-reading the list, and a UI test that the reason is folded

## 6. Disabled reasons

- [x] 6.1 Add `CleanupBlock` and `WorktreeBoard.cleanUpBlock(projectId)` (with `canCleanUp` as its absence), and wrap the header's and group headers' "Clean up" and "Clean up group" in `DisabledReason`, adding the multirepo review reason in the header; verify `WorktreeBoardTest` for each block and a UI test for the header tooltip
- [x] 6.2 Add `DaemonStopBlock`, `MachineDaemon.stopBlock(all)` and `MachineResources.stopIdleBlock`, and wrap "Stop" and "Stop idle daemons" in `DisabledReason`; verify `MachineResourcesTest` for busy, unknown, Kotlin with a busy Gradle daemon, stopping and none idle

## 7. Documentation and validation

- [x] 7.1 Document the components and the rules for the four states, disabled controls, grouped notices, in-place confirmation and headings in `docs/architecture/ui.md` and `docs/architecture/ui.es.md`; verify `mkdocs build --strict`
- [x] 7.2 Run the app with `./gradlew :desktopApp:hotRun --auto` and check, in light and dark themes, a project's settings, Preferences, the new worktree and add multirepo dialogs, a confirmation dialog, the Gradle and Compose views, an empty Active view and the disabled tooltips; verify by screenshots through the compose-hot-reload MCP
- [x] 7.3 Run `./gradlew test` green with no warnings, and `grep` that no `OutlinedTextField` or `AlertDialog` remains outside the files of 8.1 and the command palette

## 8. After orca-integration

- [x] 8.1 After orca-integration: move `worktree/CleanupDialog.kt` and the remove worktree, force-delete branch and choose terminal dialogs of `worktree/WorktreeScreen.kt` to `ShogunDialog` (with the Herdr and Orca notices as warnings in its `notices` slot), the path copy of `WorktreeDetailPane.kt` to `InlineConfirmIconAction`, and declare the detail pane's section titles as headings (the row's copy icon already confirms in place through `isCopied`); verify `ButtonLabelsUiTest` on the removal and cleanup dialogs in Spanish and the cleanup dialog in the app

## 9. After ui-v2-tokens

- [x] 9.1 After ui-v2-tokens merges into the epic: merge `origin/epic/ui-redesign-v2` into this branch, replace the provisional constants (`RowPadding`, `DialogShape`, `DialogShadow`, the title styles) with its tokens, and run `./gradlew test`

## Handoff (2026-10-29)

- `orca-integration` merged into `develop`; `origin/develop` was merged into `epic/ui-redesign-v2` (`c0c1904a`, tests green) and the epic, which already had `ui-v2-tokens` and `ui-v2-status-bar`, merged into this branch. Conflicts were resolved towards the title components with tokens' styles; the provisional constants became `ShogunTheme.typography.viewTitle`/`sectionTitle`, `ShogunTheme.colors.accent`/`disabledContent`, `DialogShape`, `ShogunTheme.elevation.level2` and `Spacing.smd`.
- 7.2 checked on the running app, in dark and light: the new worktree dialog and its field error, Preferences, the project settings form, the Compose view without Docker, the Gradle view's disabled reasons and the cleanup dialog with the Herdr notice.

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` if needed, then the implementation commit.
- Ask Ciro before opening the pull request against `epic/ui-redesign-v2`, assigned to `CiroFeliu`, after merging `origin/epic/ui-redesign-v2` into the branch.
