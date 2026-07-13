## Why

Today, launching the Claude Code agent on a worktree requires manually opening a terminal, `cd`-ing into the worktree path, and running `headroom wrap claude`. This breaks flow every time a worktree is created — the app already knows the exact path, so it should be able to land the user directly in a ready terminal session. The exact agent invocation isn't universal either — not everyone runs `claude` wrapped in `headroom` (Headroom's context compressor) — so that command needs to be configurable, not hardcoded, same as the terminal choice.

## What Changes

- Add a new button to the worktree row (`WorktreeRow` in `WorktreeScreen.kt`) that opens a terminal at that worktree's path and runs the project's configured agent launch command in it.
- Introduce a `TerminalEmulator` concept (e.g. Terminal.app, iTerm2, Warp, gnome-terminal, konsole, custom command) instead of hardcoding one terminal/OS combination.
- Add terminal selection to project configuration: the user can pick a terminal emulator explicitly, or leave it on auto-detect and let the app pick a supported emulator installed on the current OS.
- Add an `AgentLaunchConfig` to project configuration: which agent command to run (default `claude`) and whether to wrap it with `headroom wrap` (default on), so users who don't use Headroom or use a different agent binary aren't stuck with `headroom wrap claude`.
- Add a new domain port (`TerminalLauncher`) plus OS/emulator-specific infrastructure implementations, wired through `AppContainer`.
- Add a new `WorktreeError` variant for when no terminal can be launched (not configured and none detected).

## Capabilities

### New Capabilities
- `worktree-terminal-launch`: opening a terminal at a worktree's path and running the project's configured agent launch command in it.

### Modified Capabilities
- `project-configuration`: adds a terminal emulator preference (explicit selection or auto-detect) and an agent launch preference (agent command + headroom wrapping toggle) as part of `ProjectConfig`, editable from the project configuration screen.

## Impact

- **Domain**: new `TerminalLauncher` port, new `TerminalEmulator` model, new `AgentLaunchConfig` model, new `OpenWorktreeTerminalUseCase`, new `WorktreeError` variant, extended `ProjectConfig`.
- **Infrastructure**: new per-emulator launchers (macOS: Terminal.app/iTerm2/Warp via `osascript`; Linux: gnome-terminal/konsole/x-terminal-emulator via direct process spawn), plus a detection helper.
- **UI**: `WorktreeRow`/`WorktreeScreen.kt`, `WorktreeViewModel.kt` (new action), `ProjectConfigScreen.kt`/`ProjectConfigViewModel.kt` (new terminal selector and agent launch fields).
- **Wiring**: `AppContainer.kt` gains the new use case and launcher implementations.
- **Tests**: new fakes (`FakeTerminalLauncher`) following the existing manual-fake pattern, no new mocking library.
