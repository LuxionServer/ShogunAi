## Context

`OpenWorktreeTerminalUseCase` currently resolves a `TerminalEmulator` (via `TerminalPreference.mode`: `AUTO_DETECT`/`FIXED`/`CUSTOM`), builds an emulator-specific argv (`TerminalCommandBuilder`), and launches it as an OS process (`ProcessTerminalLauncher`). Two emulators (`MACOS_TERMINAL`, `ITERM2`) are driven via `osascript`/AppleScript; the archived `fix-macos-terminal-launch` change already had to work around an Accessibility-permission failure in that path, and the underlying approach (scripting external GUI apps, guessing install locations, OS-specific shell wrapping for `gnome-terminal`/`konsole`/`xterm`) remains inherently fragile — every new OS version, emulator update, or sandboxing change can break it silently for the user (button does nothing).

This change removes that whole mechanism. Instead of launching anything, the worktree row action builds the shell command the user would need to type (`cd '<worktree path>' && <resolved agent command>`) and copies it to the system clipboard, so the user pastes it into whatever terminal they already use. This trades one click-and-forget interaction for one click-and-paste interaction, in exchange for eliminating an entire category of environment-dependent failures.

## Goals / Non-Goals

**Goals:**
- Replace terminal launching with a reliable, OS/emulator-agnostic "copy the command" action.
- Remove all now-dead code: `TerminalLauncher`, `TerminalEmulatorDetector` ports and their infra adapters, `TerminalCommandBuilder`'s per-emulator branches, `TerminalPreference`/`TerminalSelectionMode`/`TerminalEmulator` models, and the terminal-preference section of the project configuration UI.
- Keep the domain layer decoupled from the concrete clipboard mechanism, consistent with the existing ports/adapters pattern (`ShellCommandExecutor`, `FileManager`).
- Preserve `AgentLaunchConfig` (agent command + Headroom wrapping) unchanged, since it still defines the command being copied.

**Non-Goals:**
- Any UI for editing the copied command's format (e.g. shell dialect, quoting style) — a single, fixed `cd '<path>' && <command>` format is enough.
- Detecting or launching any terminal emulator, for any OS. That capability is removed, not deprioritized.
- Copying anything other than plain text (e.g. rich text, multiple clipboard formats).

## Decisions

### Introduce a `ClipboardWriter` port instead of calling AWT/Compose clipboard APIs from the domain

The domain currently never touches OS-level UI APIs directly (`TerminalLauncher`/`TerminalEmulatorDetector` are ports for the same reason). To keep that boundary, add `domain/executor/ClipboardWriter.kt`:
```kotlin
interface ClipboardWriter {
    fun write(text: String): Result<Unit>
}
```
with an infrastructure adapter `infrastructure/AwtClipboardWriter.kt` backed by `java.awt.Toolkit.getDefaultToolkit().systemClipboard` (available in any JVM desktop process, no Compose-specific API needed, so it stays a plain infrastructure class rather than something living in `ui/`).

**Alternative considered**: copy directly from the Compose UI layer using `androidx.compose.ui.platform.ClipboardManager` (`LocalClipboardManager`), skipping a domain port entirely. Rejected: it would mean the ViewModel builds the command string itself, duplicating `AgentLaunchConfig.resolvedCommand()` composition logic that today lives in the use case, and it breaks the project's convention that use cases own the domain operation and return a typed `Result`.

### Replace `OpenWorktreeTerminalUseCase` with `CopyWorktreeLaunchCommandUseCase`

New use case, same shape as the one it replaces:
```kotlin
class CopyWorktreeLaunchCommandUseCase(
    private val config: ProjectConfig,
    private val clipboard: ClipboardWriter,
) {
    suspend operator fun invoke(worktreePath: String): Result<Unit> = runCatching {
        val command = "cd '$worktreePath' && ${config.agentLaunchConfig.resolvedCommand()}"
        clipboard.write(command).getOrElse { throw WorktreeError.ClipboardWriteFailed(it) }
    }
}
```
This drops the `TerminalSelectionMode` branching and the `TerminalEmulatorDetector` dependency entirely — there is exactly one code path.

**Alternative considered**: keep escaping/building logic in `TerminalCommandBuilder` (renamed) for symmetry with the old code. Rejected: with only one target format (a `cd && command` string, no per-emulator argv), a dedicated builder object is unnecessary indirection for a one-line string template; it lives directly in the use case.

### New typed error: `WorktreeError.ClipboardWriteFailed`, replacing `NoTerminalAvailable`/`TerminalLaunchFailed`

`NoTerminalAvailable` (no emulator detected/configured) and `TerminalLaunchFailed` (OS failed to start the process) both stop applying — there is no emulator to detect and no process to start. The only failure mode left is the clipboard write itself failing (e.g. no display/clipboard owner in a headless environment), surfaced as `WorktreeError.ClipboardWriteFailed(cause)`.

### Drop `TerminalPreference` from `ProjectConfig`, and add `ignoreUnknownKeys` to the project JSON codec

`ProjectConfig.terminalPreference` is removed from the data class. Existing persisted `ProjectConfig` JSON files created before this change may still contain a `"terminalPreference"` key. `JsonProjectRepository` currently uses `Json { prettyPrint = true }`, which rejects unknown keys by default — without a change there, every pre-existing project would fail to load after upgrading. `JsonProjectRepository`'s `Json` instance needs `ignoreUnknownKeys = true` so the stale key is silently ignored on read (and stops being written on the next save).

**Alternative considered**: write an explicit one-time migration that strips `terminalPreference` from stored JSON files. Rejected: `ignoreUnknownKeys = true` is a one-line, permanent, zero-maintenance fix for this exact situation (a schema field being retired), and this project has no existing migration-runner infrastructure to justify building one just for this.

## Risks / Trade-offs

- **[Risk] Users on machines without a "paste into a terminal" habit find the new flow less convenient than a button that just opens a terminal.** → **Mitigation**: accepted; the team explicitly asked for this trade-off because the automatic launch was "muy inconsistente" (very inconsistent) in practice — a reliable two-step action beats an unreliable one-step action.
- **[Risk] Forgetting `ignoreUnknownKeys = true` breaks loading every existing project on upgrade.** → **Mitigation**: called out explicitly as a task; covered by a repository-level test that loads a `ProjectConfig` JSON fixture containing a stale `terminalPreference` key.
- **[Risk] Clipboard write can still fail (e.g. CI/headless runs, sandboxed environments without a display server).** → **Mitigation**: surfaced via the typed `WorktreeError.ClipboardWriteFailed`, same pattern as the launch failure it replaces — never silently swallowed.

## Migration Plan

No data migration beyond the `ignoreUnknownKeys` codec change described above (stale `terminalPreference` keys are ignored, not migrated). Rollback is a straight revert of this change's commits; there is no irreversible data transformation involved.
