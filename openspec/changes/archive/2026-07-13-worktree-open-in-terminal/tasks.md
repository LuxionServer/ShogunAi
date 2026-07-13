## 1. Domain model and ports

- [x] 1.1 Add `TerminalEmulator` enum (`MACOS_TERMINAL`, `ITERM2`, `WARP`, `GNOME_TERMINAL`, `KONSOLE`, `XTERM`) and `TerminalSelectionMode` enum (`AUTO_DETECT`, `FIXED`, `CUSTOM`) in `domain/model/`
- [x] 1.2 Add `@Serializable data class TerminalPreference(mode, emulator, customCommandTemplate)` with `mode` defaulting to `AUTO_DETECT`
- [x] 1.3 Add `terminalPreference: TerminalPreference = TerminalPreference()` field to `ProjectConfig`
- [x] 1.4 Add `@Serializable data class AgentLaunchConfig(agentCommand: String = "claude", useHeadroom: Boolean = true)` with a `resolvedCommand()` helper, in `domain/model/`
- [x] 1.5 Add `agentLaunchConfig: AgentLaunchConfig = AgentLaunchConfig()` field to `ProjectConfig`
- [x] 1.6 Add `WorktreeError.NoTerminalAvailable` and `WorktreeError.TerminalLaunchFailed(cause)` variants
- [x] 1.7 Add `TerminalLauncher` port (`suspend fun launch(command: List<String>, workingDirectory: String): Result<Unit>`) in `domain/executor/`
- [x] 1.8 Add `TerminalEmulatorDetector` port (`suspend fun detectAvailable(): List<TerminalEmulator>`) in `domain/executor/`

## 2. Use case

- [x] 2.1 Add `TerminalCommandBuilder` (object) with `build(emulator, workingDirectory, command): List<String>`, one branch per `TerminalEmulator`, plus the `CUSTOM` template substitution (`{path}`/`{command}` replaced per argv element, never concatenated into a shell string)
- [x] 2.2 Add `OpenWorktreeTerminalUseCase(config, launcher, detector, commandBuilder)` resolving the emulator per `TerminalPreference.mode`, using `config.agentLaunchConfig.resolvedCommand()` as the command to run, and mapping failures to the new `WorktreeError` variants
- [x] 2.3 Unit tests for `OpenWorktreeTerminalUseCase` using `FakeTerminalLauncher`/`FakeTerminalEmulatorDetector` (manual fakes, following `FakeShellCommandExecutor`'s pattern): auto-detect success, fixed emulator success, custom template success, no terminal available, launch failure, Headroom-disabled command resolution
- [x] 2.4 Unit tests for `TerminalCommandBuilder` covering each emulator's generated argv and the custom-template placeholder substitution
- [x] 2.5 Unit tests for `AgentLaunchConfig.resolvedCommand()`: default (`headroom wrap claude`), `useHeadroom = false`, custom `agentCommand`

## 3. Infrastructure

- [x] 3.1 Add `ProcessTerminalLauncher` (implements `TerminalLauncher`): `ProcessBuilder(command).apply { directory(File(workingDirectory)) }.start()`, wrapped in `runCatching`, without waiting for exit
- [x] 3.2 Add `SystemTerminalEmulatorDetector` (implements `TerminalEmulatorDetector`): checks `/Applications/*.app` existence via `FileManager` for macOS entries, `which <binary>` via `ShellCommandExecutor` for Linux entries; returns available emulators in OS-appropriate priority order
- [x] 3.3 Wire `TerminalLauncher`, `TerminalEmulatorDetector`, and `OpenWorktreeTerminalUseCase` into `AppContainer.worktreeUseCases`, adding `openTerminal` to `WorktreeUseCases`

## 4. UI

- [x] 4.1 Add `onOpenTerminal: () -> Unit` callback and a second `OutlinedButton("Terminal")` to `WorktreeRow` in `WorktreeScreen.kt`, wired from `WorktreeScreen`'s `LazyColumn` item
- [x] 4.2 Add `fun openTerminal(worktree: Worktree)` to `WorktreeViewModel`, following the same `viewModelScope.launch { ...onSuccess/onFailure }` shape as `remove`
- [x] 4.3 Add a terminal-preference selector to `ProjectConfigScreen.kt`/`ProjectConfigViewModel.kt`: radio group over `TerminalSelectionMode`, emulator picker when `FIXED`, argv template text input when `CUSTOM`
- [x] 4.4 Add an agent launch section to `ProjectConfigScreen.kt`/`ProjectConfigViewModel.kt`: text field for `agentCommand`, checkbox/switch for `useHeadroom`
- [x] 4.5 Manually verify on macOS: opening a terminal from a worktree row with auto-detect, with a fixed emulator, and with a custom template; with Headroom wrapping on and off

## 5. Docs

- [x] 5.1 Update `docs/architecture/` with the new `TerminalLauncher`/`TerminalEmulatorDetector` ports if the architecture doc enumerates domain ports
