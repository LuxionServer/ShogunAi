## Why

The desktop app has no Compose UI test infrastructure, so the guards fixed in `fix-validation-findings` (a disabled dropdown option can't be selected by any input method, segmented labels are never truncated, "Create worktree" is only enabled for an available branch) are only covered by a manual check and can regress unnoticed.

## What Changes

- Add the Compose Desktop UI test dependencies to `desktopApp` so composables can be tested headlessly with `runComposeUiTest`.
- Add UI tests for `DropdownSelector` (disabled options ignore selection, enabled ones select and close the menu, group headers toggle) and `SegmentedSelector` (every label is rendered in full, selection callback fires, disabled state).
- Add a UI test for the "Create worktree" enabled state in "Existing branch" mode.
- Run the UI tests with the existing `./gradlew test` so CI picks them up.

## Capabilities

### New Capabilities
- `desktop-ui-testing`: automated Compose UI tests for the desktop app's reusable controls and the creation form guards.

### Modified Capabilities

## Impact

- `desktopApp/build.gradle.kts` and `gradle/libs.versions.toml`: test dependencies only.
- `desktopApp/src/test`: new UI test classes.
- No production code or behavior changes beyond the testability seams the tests need.
