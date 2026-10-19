# Design

## Context

See proposal.md for motivation. `CopyWorktreeLaunchCommandUseCase` builds `cd <quoted path> && <agent>` with `ShellQuoting`. `OpenWorktreeInHerdrUseCase` already resolves the worktree's environment with `ResolveWorktreeEnvironmentUseCase` and passes it to a Herdr pane. Commands run as argv through `ShellCommandExecutor`, never through a shell string, and since `process-robustness` never wait for input. App-wide preferences live in `JsonSettingsFile.Settings`; machine-only project settings (emulator, simulator) live with the project in `projects.json`.

## Goals / Non-Goals

**Goals:**
- Each integration uses the terminal's or IDE's own documented automation interface, with argv only, and fails with a typed error and a message, never silently.
- The agent starts with the same environment `shogun env` prints.

**Non-Goals:**
- Generic "any terminal" launching through a configurable command line.
- Passing the worktree's environment to IDEs; IDEs read the worktree's files (`.env`, `local.properties`) as usual.
- Other terminals (Ghostty, Warp, Alacritty, Kitty, GNOME Terminal); they can be added later as adapters.

## Decisions

**One launch line, many terminals.** For POSIX terminals the use case builds one line: `cd <path> && export NAME=<value> … && <agent command>`, every value quoted with `ShellQuoting.posix`, and hands it to the adapter, which types or passes it into an interactive shell so the shell stays open after the agent exits. For Windows Terminal it builds the PowerShell equivalent (`Set-Location -LiteralPath <path>; $env:NAME = <value>; <agent>`) with PowerShell quoting.

**Adapters.**
- Terminal (macOS): `osascript` with a fixed script that takes the line as `argv` (`on run argv … do script (item 1 of argv)`), so nothing is interpolated into AppleScript.
- iTerm2 (macOS): `osascript` with a fixed script that creates a window with the default profile and `write text (item 1 of argv)`.
- Windows Terminal: `wt.exe -w 0 new-tab -d <path> powershell.exe -NoExit -Command <line>`.
- tmux: when `tmux list-sessions` finds a server, `tmux new-window -t <most recently attached session> -c <path>` with `-e NAME=value` for each variable, then `tmux send-keys -t <window> -l <agent>` and `Enter`. Requires tmux 3.0+ for `-e`; older versions are reported as unsupported.
- Zellij: when `zellij list-sessions --short` finds a session, `zellij --session <most recent> action new-tab --cwd <path>`, then `action write-chars <line>` and `action write 13`.
A terminal multiplexer without a running session isn't offered, since there's no window to open in.

**Detection.** On macOS, terminals and IDEs are found by bundle identifier (`mdfind "kMDItemCFBundleIdentifier == '<id>'"`, falling back to `/Applications` and `~/Applications`), and opened with `open -b <id>`; elsewhere by executable on `PATH` and the JetBrains Toolbox script folder (`code`, `idea`, `studio`, `wt.exe`, `tmux`, `zellij`). Detection runs when Preferences opens and once at start-up in the background; its result is cached for the session.

**IDE launching.** macOS: `open -b <bundle id> <path>`; Windows and Linux: the launcher executable with the path. VS Code (`com.microsoft.VSCode`, `code`), IntelliJ IDEA (`com.jetbrains.intellij`, `com.jetbrains.intellij.ce`, `idea`), Android Studio (`com.google.android.studio`, `studio`). "Open folder" uses `open`, `explorer.exe` and `xdg-open`.

**Choice and defaults.** `Settings.terminal` (an id or `null` for none chosen) and `Settings.editor`; the project's machine settings gain `editorOverride`. With no terminal chosen, the first detected one is preselected in Preferences but "Open in terminal" asks to choose one the first time. The detail keeps "Copy launch command" as its primary button; "Open in <terminal>" and "Open in <IDE>" are secondary buttons next to it, and "Open folder" an icon action. The row context menu and palette offer the same three.

**Errors.** `WorktreeError.TerminalLaunchFailed(terminal, message)` and `EditorLaunchFailed(editor, message)`, shown as the app's usual error notice with the command's error output. A macOS automation refusal (`osascript` error -1743) gets its own message explaining where to allow it in System Settings.

## Risks / Trade-offs

- [macOS automation permission prompt surprises the user] → It appears only on the first "Open in terminal" for Terminal or iTerm2; the refusal message explains how to allow it later.
- [Typing the launch line into a shell with custom key bindings or a slow start-up] → Terminal and iTerm2 queue the text until the shell reads it; tmux and Zellij use their own write commands, not simulated keystrokes.
- [Environment values visible in the terminal's scrollback] → Same as `eval "$(shogun env)"`; the values come from repository templates, not secrets.
- [Windows Terminal can't be validated on this Mac] → Unit tests cover the argv and quoting; a manual check on Windows is a validation task.
