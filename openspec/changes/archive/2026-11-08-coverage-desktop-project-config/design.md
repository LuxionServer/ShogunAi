# Design

## Context

`ui/projectconfig` holds the project settings: `ProjectConfigViewModel` (the form state, validation, repository loading and saving), the repository configuration draft (`RepositoryConfigDraft`), the local file picker state, small helpers (`RepositoryProvisioningState`, `RepositoryRelocation`) and the composables that render them (`ProjectSettingsView`, `RepositoryConfigSection`, `EmulatorSettingsSection`, `SimulatorSettingsSection`, `LocalFilePickerDialog`).

Kover excludes functions annotated `@Composable`, but the lambdas inside them (`onClick`, `onValueChange`, a section's content, a dialog's buttons) are compiled into separate methods without the annotation, so it counts them. Most of what is uncovered in the composable files is those lambdas.

Measured on macOS on `develop` at 01d7b508, with the end-to-end tests:

| File | Lines uncovered | Branches uncovered |
|---|---|---|
| `RepositoryConfigSection.kt` | 120 of 179 | 38 of 59 |
| `ProjectSettingsView.kt` | 68 of 252 | 63 of 92 |
| `EmulatorSettingsSection.kt` | 31 of 31 | 12 of 12 |
| `SimulatorSettingsSection.kt` | 26 of 26 | 12 of 12 |
| `ProjectConfigViewModel.kt` | 21 of 438 | 101 of 426 |
| `RepositoryConfigDraft.kt` | 6 of 101 | 26 of 150 |
| `LocalFilePickerState.kt` | 3 of 60 | 3 of 62 |
| `RepositoryProvisioningState.kt` | 1 of 12 | 2 of 10 |
| `LocalFilePickerDialog.kt` | 1 of 23 | 0 of 6 |
| `RepositoryRelocation.kt` | 0 of 11 | 2 of 16 |
| `ui/projectconfig` | 277 of 1,141 | 259 of 859 |
| whole desktop app outside composables | 1,340 of 12,098 (88.92%) | 1,900 of 7,653 (75.17%) |

## Goals / Non-Goals

**Goals:**

- 100% of the lines and branches of `ui/projectconfig`.
- Raise the desktop floors to the whole percentages measured after this change.

**Non-Goals:**

- Other packages; they are the next changes of the plan in the archived `coverage-desktop-texts` design.
- Changing how the settings look or behave, beyond fixing a defect the tests find.
- A per-package Kover rule: Kover 0.9's `verify` rules apply to the whole module, so the package is held by the module floors and its tests.

## Decisions

### View model and models first, UI tests for the lambdas

The view model's and the draft's branches are covered with plain unit tests next to `ProjectConfigViewModelTest`, using the existing fakes from `core`'s test fixtures (`FakeProjectRepository`, `FakeRepositoryProvisioningSource`, `FakeRepositoryTrust`) and the constructor's function parameters (`loadAvds`, `inspectRepository`, `detectStacks`, `listLocalFolder`…). They are faster and say more than a UI test.

What only runs from a composable's lambdas is covered with Compose UI tests that render the section with a real view model in the state under test, act on it (click, type, toggle) and assert on the view model or on what is shown, like `LocalEntriesUiTest`. Each section gets its own test class (`RepositoryConfigSectionUiTest`, `ProjectSettingsViewUiTest`, `EmulatorSettingsSectionUiTest`, `SimulatorSettingsSectionUiTest`), so a failure points to the section.

Alternative considered: driving everything through the end-to-end tests with the whole app. They need a real repository per case and are an order of magnitude slower; they stay for the user journeys they already cover.

### Width-dependent and scroll-dependent code

The section index only shows when the form is at least 1,000 dp wide and the current section depends on the scroll. The UI tests render the form inside a box with a fixed width (wide and narrow) and scroll it with `performScrollToNode`/`scrollState`, so both sides of those conditions run without depending on the test window's size.

### Branches no input reaches

As in `coverage-desktop-texts`, a branch the compiler generates but no input reaches is not excluded: the code is rewritten in an equivalent form whose branches all run (for example `x?.let { … } ?: y` whose lambda never returns `null` becomes an `if`, and a `!!` after a null check becomes a local value). The rewrites made:

- `existingProject?.config?.x ?: default`: a project's `config` is never `null`, so the second safe call and the fallback after it only saw one value. The view model now takes the saved configuration once (`savedConfig`, an empty `ProjectConfig` for a new project, whose single default agent equals `AgentDraft()`) and reads its fields directly.
- `provisioning?.approvalSubject ?: RepositoryApprovalSubject()`, `provisioning?.providers ?: RepositoryProviders()` and `provisioning?.environment?.…`: same pattern; they became an `if` on `provisioning`, or read from `provisioning ?: RepositoryProvisioning()`, whose defaults are the same values.
- `(state as? Declared)?.provisioning?.providers?.emulator`, `maven?.mavenMaxHeap?.trim()?.…`, `emulatorAvd?.trim()?.ifEmpty { null }` and `detectedMainBranch?.let(…) ?: …`: chained safe calls on values that are never `null`, rewritten with one safe call and a `let`, or an `if`.
- `editorOverride?.let { … } == true` and the view's `editorOverrideMissing && override != null`: the second check could never fail. The view model now exposes `missingEditorOverride: EditorId?` and the view shows the warning when it is set.
- `approveRepository` and the approval refresh checked that the loaded path wasn't blank after checking the approval subject wasn't empty, but a subject only comes from a non-blank path; the path check was removed there and kept in `revokeRepositoryApproval`, where it is reachable.
- The local file picker read `listLocalFileCandidates ?: return` every time it loaded, although it can only open when the lister exists. The open picker now keeps the lister it was opened with, and `applyLocalFilePicker` no longer chains a safe call on `selection()`, which never returns `null`.
- `if (error && errorMessage != null)` for the base path: whenever the error is visible there is a message, so it became `errorMessage.takeIf { error }`.
- `duplicateProject != null -> …duplicateProject!!.name`: the `!!` became a local value.
- The emulator and simulator sections returned early on `Hidden` and then listed `Hidden -> Unit` again. Their states gain a sealed `Shown` subtype, and the sections render only a `Shown` state, so the `when` has no case that can't happen.
- A `when` over a sealed type or an enum inside a composable's lambda keeps the default branch the compiler adds, which Kover counts there although it filters it in ordinary functions. The `when` over the save outcome's invalid field moved to an ordinary function (`ProjectConfigField.focusIn`), the AVD label to `avdLabel`, and the `when` over the picker's and the sections' load states, which call composables, to small composables of their own (`CandidatesContent`, `EmulatorStateContent`, `SimulatorStateContent`). They are rendered by the same UI tests.
- `isValid` of the view model and of `RepositoryConfigDraft` had no caller and were removed.

### Floors

The floors in `desktopApp/build.gradle.kts` go up to the whole percentages measured after this change. If the package reaches 100% with nothing else changing, the whole desktop app goes to about 91.2% of lines and 78.6% of branches, so the floors would go from 88 to 91 and from 74 to 78. Measured on macOS after this change, with the end-to-end tests:

| Package | Lines | Branches |
|---|---|---|
| `ui/projectconfig` | 100% (1,130 of 1,130) | 100% (745 of 745) |
| whole desktop app outside composables | 91.21% (1,063 uncovered of 12,087) | 78.29% (1,637 uncovered of 7,539) |

The floors go from 88 to 91 (lines) and from 74 to 78 (branches).

## Risks / Trade-offs

- [UI tests of large composables are slow or flaky] → Each test renders one section, waits with `waitUntil` for asynchronous loads (emulator AVDs, simulator runtimes, local file candidates) instead of sleeping, and doesn't depend on animations.
- [A test that only executes a lambda without asserting anything] → Every UI test asserts on the view model's state or on the text shown after the action.
- [Rewriting code for coverage] → Only equivalent rewrites, with the tests of both sides running, and listed here.
- [Parallel changes touch the desktop floors] → Floors and any shared component are merged when bringing in `develop` and re-measured before opening the pull request.
