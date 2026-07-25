## 1. Domain model

- [x] 1.1 Remove `TerminalPreference`, `TerminalSelectionMode`, and `TerminalEmulator` from `domain/model/TerminalPreference.kt` (delete the file).
- [x] 1.2 Remove `terminalPreference` from `ProjectConfig` (`domain/model/ProjectConfig.kt`), including its KDoc reference.
- [x] 1.3 Replace `WorktreeError.NoTerminalAvailable` and `WorktreeError.TerminalLaunchFailed` with `WorktreeError.ClipboardWriteFailed(cause: Throwable)` in `domain/model/WorktreeError.kt`.

## 2. Domain port and use case

- [x] 2.1 Add `domain/executor/ClipboardWriter.kt` with `interface ClipboardWriter { fun write(text: String): Result<Unit> }`.
- [x] 2.2 Delete `domain/executor/TerminalLauncher.kt` and `domain/executor/TerminalEmulatorDetector.kt`.
- [x] 2.3 Delete `domain/usecase/OpenWorktreeTerminalUseCase.kt` and `domain/usecase/TerminalCommandBuilder.kt`.
- [x] 2.4 Add `domain/usecase/CopyWorktreeLaunchCommandUseCase.kt` implementing the design's `cd '<path>' && <resolved command>` template, writing via `ClipboardWriter` and mapping failure to `WorktreeError.ClipboardWriteFailed`.

## 3. Infrastructure

- [x] 3.1 Delete `infrastructure/ProcessTerminalLauncher.kt` and `infrastructure/SystemTerminalEmulatorDetector.kt`.
- [x] 3.2 Add `infrastructure/AwtClipboardWriter.kt` implementing `ClipboardWriter` via `java.awt.Toolkit.getDefaultToolkit().systemClipboard`.
- [x] 3.3 Add `ignoreUnknownKeys = true` to the `Json` instance in `infrastructure/JsonProjectRepository.kt` so stale `terminalPreference` keys in previously persisted `ProjectConfig` files don't break deserialization.

## 4. Wiring

- [x] 4.1 Update `AppContainer.kt`: remove `TerminalLauncher`/`TerminalEmulatorDetector`/`OpenWorktreeTerminalUseCase` wiring, add `ClipboardWriter`/`AwtClipboardWriter`/`CopyWorktreeLaunchCommandUseCase` wiring, and rename the exposed use case field accordingly (e.g. `copyLaunchCommand`).

## 5. Worktree UI

- [x] 5.1 Update `ui/worktree/WorktreeViewModel.kt`: rename `openTerminal(worktree)` to invoke `useCases.copyLaunchCommand(worktree.path)` (rename the method, e.g. `copyLaunchCommand`), keeping the existing success/failure -> `errorMessage` handling.
- [x] 5.2 Update `ui/worktree/WorktreeScreen.kt`: rename the "Terminal" button/action to reflect the new behavior (e.g. "Copiar comando") and wire it to the renamed ViewModel method.

## 6. Project configuration UI

- [x] 6.1 Remove the "Terminal" `SectionCard` and its `TerminalSelectionMode`/`TerminalEmulator` pickers from `ui/projectconfig/ProjectConfigScreen.kt`, including the now-unused `label()` extension functions and imports.
- [x] 6.2 Remove `terminalSelectionMode`, `terminalEmulator`, and the custom-template text state from `ui/projectconfig/ProjectConfigViewModel.kt`, and drop `terminalPreference` from the `ProjectConfig` construction on save.

## 7. Tests

- [x] 7.1 Delete `domain/FakeTerminalLauncher.kt`, `domain/FakeTerminalEmulatorDetector.kt`, `domain/usecase/OpenWorktreeTerminalUseCaseTest.kt`, and `domain/usecase/TerminalCommandBuilderTest.kt`.
- [x] 7.2 Add a `FakeClipboardWriter` test double and `CopyWorktreeLaunchCommandUseCaseTest` covering: successful copy with default (Headroom-wrapped) command, successful copy with Headroom disabled, and clipboard failure mapping to `WorktreeError.ClipboardWriteFailed`.
- [x] 7.3 Update `ui/worktree/WorktreeViewModelTest.kt` for the renamed method/use case.
- [x] 7.4 Add a `JsonProjectRepository` test that loads a `ProjectConfig` JSON fixture containing a stale `terminalPreference` key and asserts it deserializes successfully.

## 8. Verification

- [x] 8.1 Run `./gradlew test` and fix any remaining references to the removed types.
- [x] 8.2 Run `./gradlew :desktopApp:run`, click the renamed worktree action, and confirm the expected `cd '<path>' && <command>` string is on the system clipboard (paste it into a terminal to verify).
