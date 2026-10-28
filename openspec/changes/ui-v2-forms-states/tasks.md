# Tasks

## 1. Components

- [ ] 1.1 Add `ViewTitle`, `SectionTitle` and `GroupTitle` with `heading()` semantics, and use `SectionTitle` in `SectionCard`; verify a UI test asserting heading semantics on each and on a `SectionCard` title
- [ ] 1.2 Add `Field` and `PathField` (label above, 32 dp input, help or error below, focus tracking built in) and make `DropdownSelector`'s anchor a read-only `Field`; verify UI tests for label and help, error replacing help, "Browse…" invoking its callback, typing through the field, and the existing `SelectorUiTest` still passing
- [ ] 1.3 Add `ShogunDialog` with `DialogSize` (400, 520, 680), heading title, scrollable body, `notices` slot and end-aligned footer; verify UI tests for heading semantics, footer order (dismiss before confirm), dismiss on Escape and a body taller than the window scrolling with the footer visible
- [ ] 1.4 Add `EmptyState`, `ErrorState` (extracting the "Technical details" disclosure from `ErrorNotice` into a shared internal composable over plain text) and `SkeletonRows`; verify UI tests for one action plus optional link, details folded until clicked, and skeleton rows exposing a single "Loading" description with no click action
- [ ] 1.5 Add `rememberInlineConfirm`, `InlineConfirmButton` and `InlineConfirmIconAction`; verify a UI test with the test clock: "Copied" right after the click, the original label after 1.5 s
- [ ] 1.6 Add `DisabledReason` (tooltip plus state description, nothing when the reason is null); verify a UI test hovering a disabled button shows the reason, and that a null reason adds no tooltip
- [ ] 1.7 Add `groupNotices` and `GroupedNotice` (summary, count, "Details" disclosure, one dismiss); verify unit tests for grouping by kind and source in first-appearance order, and a UI test for the count, the folded details and the single dismiss
- [ ] 1.8 Add the new strings (`loading`, `details`, `browse`, notice count, released summary, every disabled reason) to `Strings`, `EnglishStrings` and `SpanishStrings`; verify `./gradlew :desktopApp:compileKotlin` (the interface enforces parity) and `StringsTest` cases for the pluralized summaries in both languages

## 2. Forms

- [ ] 2.1 Migrate `projectconfig/ProjectSettingsView.kt`, `RepositoryConfigSection.kt`, `EmulatorSettingsSection.kt` and `SimulatorSettingsSection.kt` from `OutlinedTextField` to `Field`/`PathField`, and the simulator snippet copy to `InlineConfirmButton`; verify `LocalEntriesUiTest`, `ProjectConfigViewModelTest` and the other projectconfig tests pass unchanged
- [ ] 2.2 Migrate `multirepo/MultirepoSettingsView.kt`, `multirepo/AddMultirepoDialog.kt` and `multirepo/NewChangeDialog.kt` to `Field`; verify `MultirepoViewUiTest` and `MultirepoSetupTest` pass
- [ ] 2.3 Migrate `shell/PreferencesWindow.kt` and `shell/ResourcesSection.kt` to `Field`; verify `ResourcesSectionUiTest`, `ToolsSectionUiTest` and `InactivitySectionUiTest` pass
- [ ] 2.4 Migrate `worktree/CreateWorktreeDialog.kt`'s task field and the worktree view's text filter to `Field`; verify `CreateGuardTest` and `WorktreeHeaderUiTest` pass

## 3. Dialogs

- [ ] 3.1 Move `AddMultirepoDialog`, `NewChangeDialog` and `CreateWorktreeDialog` from `BasicAlertDialog` to `ShogunDialog` (Medium or Large, `scrollable = false` where the body has its own scroll); verify their UI tests and the Spanish button-label tests pass
- [ ] 3.2 Move the confirmation `AlertDialog`s of `machine/ComposeProjectsView.kt`, `machine/DesktopTurnView.kt`, `machine/MachineViewParts.kt`, `multirepo/MultirepoSettingsView.kt`, `projectconfig/ProjectSettingsView.kt`, `projectconfig/SimulatorSettingsSection.kt`, `shell/AppShell.kt` (discard changes, remove project), `shell/ClaudeHooksViews.kt`, `shell/TourViews.kt` and `shell/UpdateViews.kt` to `ShogunDialog`; verify `ClaudeHooksUiTest`, `TourUiTest`, `UpdateViewsUiTest`, `DesktopTurnViewUiTest`, `XcodeDerivedDataViewUiTest` pass and no `AlertDialog` import remains outside the files of 8.1

## 4. States

- [ ] 4.1 Replace the first load of a project group (`GroupState` on `ProjectLoadState.Loading`) with `SkeletonRows`, keeping rows during reloads; verify a UI test for first load (skeleton, no rows) and reload (rows kept)
- [ ] 4.2 Turn `NothingActive`, `NoMatch` and the first-launch screen into `EmptyState`; verify UI tests for their sentence, action and link
- [ ] 4.3 Show `SkeletonRows` in the summary panel until every shown project has loaded once; verify a UI test with a loading project
- [ ] 4.4 Give the worktree view title, the project, state and change group headers and `MachineViewFrame` their title components; verify a UI test listing the headings of the worktree view header and a group header

## 5. Machine

- [ ] 5.1 Add `MachineResources.dismissNotices(resource)` and group the pool notices of each view into one `GroupedNotice` (trims summed within the last limit, releases counted, single notices unchanged); verify `MachineResourcesTest` for three trims, a release with two trims, and dismissing the group
- [ ] 5.2 Show Compose's unavailable state as `ErrorState` with "Try again" calling a new `MachineResources.retryComposeProjects()` and the reason in the technical details; verify `MavenAndComposeMachineTest` for the retry re-reading the list, and a UI test that the reason is folded

## 6. Disabled reasons

- [ ] 6.1 Add `CleanupBlock` and `WorktreeBoard.cleanUpBlock(projectId)` (with `canCleanUp` as its absence), and wrap the header's and group headers' "Clean up" and "Clean up group" in `DisabledReason`, adding the multirepo review reason in the header; verify `WorktreeBoardTest` for each block and a UI test for the header tooltip
- [ ] 6.2 Add `DaemonStopBlock`, `MachineDaemon.stopBlock(all)` and `MachineResources.stopIdleBlock`, and wrap "Stop" and "Stop idle daemons" in `DisabledReason`; verify `MachineResourcesTest` for busy, unknown, Kotlin with a busy Gradle daemon, stopping and none idle

## 7. Documentation and validation

- [ ] 7.1 Document the components and the rules for the four states, disabled controls, grouped notices, in-place confirmation and headings in `docs/architecture/ui.md` and `docs/architecture/ui.es.md`; verify `mkdocs build --strict`
- [ ] 7.2 Run the app with `./gradlew :desktopApp:hotRun --auto` and check, in light and dark themes, a project's settings, Preferences, the new worktree and add multirepo dialogs, a confirmation dialog, the Gradle and Compose views, an empty Active view and the disabled tooltips; verify by screenshots through the compose-hot-reload MCP
- [ ] 7.3 Run `./gradlew test` green with no warnings, and `grep` that no `OutlinedTextField` or `AlertDialog` remains outside the files of 8.1 and the command palette

## 8. After orca-integration

- [ ] 8.1 After orca-integration: move `worktree/CleanupDialog.kt` and the remove worktree, force-delete branch and choose terminal dialogs of `worktree/WorktreeScreen.kt` to `ShogunDialog` (with the runner notice in its `notices` slot), the path copy of `WorktreeDetailPane.kt` and of the row to `InlineConfirmIconAction`, and the detail pane's section titles to `SectionTitle`

## 9. After ui-v2-tokens

- [ ] 9.1 After ui-v2-tokens merges into the epic: merge `origin/epic/ui-redesign-v2` into this branch, replace the provisional constants (`RowPadding`, `DialogShape`, `DialogShadow`, the title styles) with its tokens, and run `./gradlew test`

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` if needed, then the implementation commit.
- Ask Ciro before opening the pull request against `epic/ui-redesign-v2`, assigned to `CiroFeliu`, after merging `origin/epic/ui-redesign-v2` into the branch.
