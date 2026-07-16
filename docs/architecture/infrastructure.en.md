# Infrastructure layer

Lives in `desktopApp/src/main/kotlin/app/luxion/shogunai/infrastructure`. Implements the ports defined in the domain.

## `ProcessBuilderShellCommandExecutor`

Implementation of `ShellCommandExecutor` on top of `ProcessBuilder`. Robustness details:

- **Reads stdout and stderr concurrently** (two `async`s). If read sequentially, a process that fills the buffer of the unread stream would block (the classic `ProcessBuilder` deadlock).
- `waitFor()` is wrapped in `runInterruptible` so that coroutine cancellation interrupts waiting for the process.
- Blocking work is confined to `Dispatchers.IO` (injectable via constructor for tests).
- In the `finally` block, `process.destroy()` is called if the process is still alive, to avoid leaving orphan processes if the coroutine is cancelled.

## `NioFileManager`

Implementation of `FileManager` on top of `java.nio.file`. `copy` uses `REPLACE_EXISTING` and `COPY_ATTRIBUTES`. Works the same on macOS and Linux (supported targets).

## `ProcessTerminalLauncher`

Implementation of `TerminalLauncher`: `ProcessBuilder(command).directory(File(workingDirectory)).start()`, wrapped in `runCatching`, without waiting for the process to finish (fire-and-forget — unlike `ProcessBuilderShellCommandExecutor`, which does wait for the exit code).

## `SystemTerminalEmulatorDetector`

Implementation of `TerminalEmulatorDetector`. Branches on `System.getProperty("os.name")`:

- **macOS**: checks for the existence of `/Applications/iTerm.app` and `/Applications/Warp.app` via `FileManager.exists`; `MACOS_TERMINAL` is always appended at the end (Terminal.app ships with the system).
- **Linux**: `which <binary>` via `ShellCommandExecutor.execute(...).isSuccess` for `gnome-terminal`, `konsole`, `xterm`.

## `TerminalCommandBuilder`

Pure object in `domain/usecase/` (not infrastructure, but documented here since it builds the per-emulator invocation detail): translates `TerminalEmulator` + path + command into the concrete argv. For `MACOS_TERMINAL`/`ITERM2` it generates an AppleScript passed to `osascript` (as several `-e` arguments, never as a single shell string):

- `ITERM2`: opens a **new tab** — `create tab with default profile` on `current window` (or `create window with default profile` if there's none). iTerm2's dictionary supports creating tabs directly, with no extra permissions.
- `MACOS_TERMINAL`: if no Terminal window is open, `do script` with no target creates one directly (fallback, no tabs). If one already exists, it forces a **new tab** deterministically: `activate` + `System Events` simulating Cmd+T, then `do script ... in front window` — a freshly created tab is always idle, so that `do script` uses it unambiguously. Terminal.app has no AppleScript command to create tabs directly; Cmd+T via `System Events` requires Accessibility permission, which in dev mode (`./gradlew :desktopApp:run`, with no bundle identity of its own) macOS doesn't always get around to requesting — the keystroke has no effect with no visible error, degrading to reusing the current tab (the original bug: consecutive launches writing over each other with no separator). Packaging the app as a `.app`/dmg should let macOS prompt for the permission correctly (pending verification).

## Test doubles

In `desktopApp/src/test/kotlin/app/luxion/shogunai/domain/`:

- `FakeShellCommandExecutor` — substitutes `ShellCommandExecutor` in use case tests.
- `FakeFileManager` — substitutes `FileManager`.
- `FakeTerminalLauncher` — substitutes `TerminalLauncher`.
- `FakeTerminalEmulatorDetector` — substitutes `TerminalEmulatorDetector`.

Thanks to the domain depending on interfaces, `CreateWorktreeUseCaseTest`, `RemoveWorktreeUseCaseTest`, and `ListWorktreesUseCaseTest` tests don't touch disk or launch real processes.
