## 1. Test infrastructure

- [x] 1.1 Add `compose.uiTest` to `desktopApp` test dependencies and verify a trivial `runComposeUiTest` runs under `./gradlew :desktopApp:test`

## 2. Selector tests

- [x] 2.1 `DropdownSelector` tests: disabled option ignored via semantics click, enabled option selects and closes, group header toggles its options
- [x] 2.2 `SegmentedSelector` tests: full labels rendered, selection callback fires, disabled state ignores clicks

## 3. Creation form guard

- [x] 3.1 Expose the "Create worktree" enabled computation for testing if needed, and test unavailable vs available branch in "Existing branch" mode

## 4. Validation

- [x] 4.1 Run `./gradlew test` and confirm all modules pass
- [x] 4.2 Update `AGENTS.md`/docs testing notes if they state there is no UI test infrastructure, then archive the change
