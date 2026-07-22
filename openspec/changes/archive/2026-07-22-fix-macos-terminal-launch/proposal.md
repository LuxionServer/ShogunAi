## Why

The "open terminal at worktree" button silently does nothing on macOS when the current process lacks Accessibility permission for `System Events`. That permission gates the Cmd+T keystroke used to force a new tab in `Terminal.app`, and macOS routinely fails to prompt for it in dev mode (`./gradlew :desktopApp:run`, no stable app bundle). When the permission is missing, the keystroke line throws and aborts the entire AppleScript before the actual `do script` command ever runs — with no error surfaced anywhere, since `ProcessTerminalLauncher` only checks whether the `osascript` process itself started, not its exit code or stderr.

## What Changes

- `TerminalCommandBuilder.macosTerminalCommand` no longer uses `System Events`/Cmd+T to force a new tab. It now always calls `tell application "Terminal" to do script "<command>"` with no target window, which reliably opens a new Terminal **window** and requires no Accessibility permission.
- iTerm2, Warp, and the Linux emulators (`gnome-terminal`, `konsole`, `xterm`) are unaffected — only the `MACOS_TERMINAL` branch changes.
- **BREAKING (behavioral, not API)**: repeated launches against `Terminal.app` now open a new window each time instead of reusing the existing window with a new tab.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
(none — `worktree-terminal-launch`'s requirements and scenarios don't specify tab-vs-window mechanics or the macOS GUI-scripting approach; this change is scoped to `TerminalCommandBuilder`'s internal implementation of the existing "resolve which terminal emulator to use" requirement, so no spec delta is needed)

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/domain/usecase/TerminalCommandBuilder.kt` (`macosTerminalCommand`)
- `desktopApp/src/test/kotlin/app/luxion/shogunai/domain/usecase/TerminalCommandBuilderTest.kt`
- Supersedes design decision #4 (macOS Terminal.app tab-forcing via `System Events`) from the archived `worktree-open-in-terminal` change, documented as a new decision here per this repo's convention of not editing archived design docs.
