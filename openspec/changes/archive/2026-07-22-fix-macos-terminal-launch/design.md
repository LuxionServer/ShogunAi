## Context

`TerminalCommandBuilder.macosTerminalCommand` (see the archived `worktree-open-in-terminal` change, decision #4) builds an `osascript` invocation for `Terminal.app` that tries to reuse the frontmost window by forcing a new tab via a Cmd+T keystroke sent through `System Events`. That keystroke is GUI scripting and requires the calling process to hold macOS Accessibility permission for `System Events`.

In practice this permission is rarely granted for the process tree that runs the app in dev mode (`./gradlew :desktopApp:run`), and the design doc already flagged this as a known gap, betting on packaging as a `.app`/dmg to fix the permission prompt. That bet doesn't pay off: the permission still has to be granted manually by the user regardless of how the app is packaged, and when it isn't, the keystroke line throws inside the AppleScript. Since the script has no error handling, the whole `osascript` process exits non-zero *before* reaching the `do script` line that actually opens the terminal. `ProcessTerminalLauncher` (`infrastructure/ProcessTerminalLauncher.kt`) only wraps `ProcessBuilder(...).start()` in `runCatching`, so it only fails if the OS can't spawn `osascript` itself — it never inspects the child process's exit code or stderr. The net effect: the "open terminal" button does nothing, with no error anywhere.

Manual reproduction confirmed the failure mode:
```
$ osascript -e '...' -e 'tell application "System Events" to keystroke "t" using command down' ...
execution error: No tienes autorización para enviar eventos Apple a System Events. (-1743)
exit code: 1
```
and confirmed the fix:
```
$ osascript -e 'tell application "Terminal" to do script "echo test"'
tab 1 of window id 8746   # succeeds, no extra permission needed, always opens a new window
```

## Goals / Non-Goals

**Goals:**
- Make the macOS Terminal.app launch path work reliably with zero additional permission setup, in both dev mode and packaged builds.
- Keep the fix scoped to `TerminalCommandBuilder`'s `MACOS_TERMINAL` branch; no other emulator, no domain/use-case code, changes.

**Non-Goals:**
- Preserving tab-reuse behavior for `Terminal.app`. A new window per launch is an accepted trade-off.
- Adding exit-code/stderr inspection to `ProcessTerminalLauncher` to surface future launch failures more precisely — out of scope for this fix, which removes the specific failure mode instead of making it visible.

## Decisions

### Replace Cmd+T/`System Events` GUI scripting with a plain `do script`, always creating a new window

`do script "<command>"` with no target window is documented AppleScript behavior for `Terminal.app`: when no window is specified, a new one is always created and the command runs there — regardless of whether other windows already exist. This requires only the ordinary Apple Events automation permission between the app and `Terminal.app` (the one macOS already prompts for reliably on first use), never Accessibility/GUI-scripting permission.

The new implementation:
```applescript
tell application "Terminal"
    do script "<escaped command>"
    activate
end tell
```
`activate` brings Terminal to the foreground so the user sees the new window immediately, matching the previous behavior's intent.

**Alternative considered**: keep trying Cmd+T for tab-reuse, but detect ahead of time whether Accessibility permission is granted and fall back to `do script` only when it isn't. Rejected: probing for Accessibility permission from the JVM adds real complexity (no clean Java/Kotlin API for it; would mean shelling out to another script just to check) for a cosmetic benefit (tabs vs. windows) — not worth it for a feature the team already said they'd rather remove than keep unreliable.

**Alternative considered**: surface `WorktreeError.TerminalLaunchFailed` when the Cmd+T path fails, instead of removing it. Rejected: this papers over the actual bug (button does nothing) without fixing it, and the failure would still occur on essentially every real user's machine — the fix needs to prevent the failure, not just report it.

## Risks / Trade-offs

- **[Risk] Terminal window clutter**: repeated launches now open a new `Terminal.app` window each time instead of a new tab in an existing one. → **Mitigation**: none needed at the code level; if this becomes a real annoyance, a future change could revisit tab-grouping once a permission-free mechanism exists (none is known today for `Terminal.app`'s public AppleScript dictionary).
- **[Risk] Divergence from `iTerm2`'s tab-based behavior**: iTerm2 already creates tabs (via its own AppleScript dictionary, no `System Events` needed) and is unaffected by this change, so `Terminal.app` and iTerm2 now behave visibly differently (windows vs. tabs) for the same button. → **Mitigation**: accepted; iTerm2's approach doesn't need GUI scripting so there's no reliability problem to fix there.

## Migration Plan

No data migration. Pure behavior change in `TerminalCommandBuilder`; no `ProjectConfig`/`TerminalPreference` schema changes, so no persisted-state migration is needed. Rollback is a straight revert of `TerminalCommandBuilder.kt` and its test.
