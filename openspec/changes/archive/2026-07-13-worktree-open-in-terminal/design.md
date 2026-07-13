## Context

`WorktreeRow` (`ui/worktree/WorktreeScreen.kt:88`) only shows a worktree's path/branch and an "Eliminar" button. Launching the agent on a worktree today means manually switching to a terminal, `cd`-ing to `worktree.path`, and running `headroom wrap claude`.

The domain already has one process-execution port, `ShellCommandExecutor` (`domain/executor/ShellCommandExecutor.kt`), implemented with `ProcessBuilder` and used exclusively to run `git` and wait for its exit code/stdout/stderr. Opening a terminal window is a different shape of operation: we don't want to wait for it to exit (a terminal stays open for the whole session), and *how* you open one is inherently OS- and emulator-specific — unlike `git`, there's no single binary to shell out to. The team explicitly wants to avoid hardcoding one terminal (e.g. AppleScript for Terminal.app only), since the app already targets both macOS and Arch Linux (comment in `ShellCommandExecutor.kt:11`), and different developers use different terminal emulators (Terminal.app, iTerm2, Warp, gnome-terminal, konsole...).

## Goals / Non-Goals

**Goals:**
- Add a button on the worktree row that opens a terminal at that worktree's path and runs the project's configured agent launch command in it.
- Support choosing the terminal emulator per project (explicit choice) or auto-detecting one installed on the current OS.
- Support configuring the agent launch command itself: which agent binary to run (default `claude`) and whether to wrap it with Headroom's `headroom wrap` context compressor (default on, but toggleable/off for users who don't have Headroom installed or don't want it).
- Keep the terminal-launch mechanism pluggable so adding a new emulator later doesn't touch domain/use-case code.
- Preserve the existing architectural rules: domain depends on ports, commands run as argv (not shell strings), errors are typed via `WorktreeError`.

**Non-Goals:**
- Cross-platform parity beyond macOS and Linux (no Windows support).
- Managing/monitoring the lifecycle of the spawned terminal session (we fire-and-forget; we don't track whether the user later closes it or whether the agent command succeeds inside it).
- Validating that the configured agent binary or `headroom` actually exist on `PATH` before launch — a bad command simply fails inside the opened terminal, visibly, same as if the user had typed it wrong themselves.

## Decisions

### 1. New domain port: `TerminalLauncher`, separate from `ShellCommandExecutor`
```kotlin
interface TerminalLauncher {
    suspend fun launch(workingDirectory: String, command: String): Result<Unit>
}
```
`ShellCommandExecutor.execute` waits for the process to exit and captures stdout/stderr — the right contract for `git`, wrong for a terminal window that's meant to stay open. `TerminalLauncher.launch` only confirms the terminal process *started*; it never waits for it to close. This keeps `ShellCommandExecutor`'s contract unchanged for its existing callers.

**Alternative considered**: reuse `ShellCommandExecutor.execute` directly for the emulator's launch command. Rejected because a coroutine would sit blocked on `waitFor()` for as long as the terminal window stays open (e.g. `xterm` without `-hold`/detachment), wasting a coroutine/IO thread per opened terminal for no benefit — we only ever want the exit code of the *launch*, not of the terminal session.

### 2. Terminal selection modeled as a flat, serializable config, not a sealed class
```kotlin
enum class TerminalEmulator { MACOS_TERMINAL, ITERM2, WARP, GNOME_TERMINAL, KONSOLE, XTERM }

enum class TerminalSelectionMode { AUTO_DETECT, FIXED, CUSTOM }

@Serializable
data class TerminalPreference(
    val mode: TerminalSelectionMode = TerminalSelectionMode.AUTO_DETECT,
    val emulator: TerminalEmulator? = null,          // required when mode == FIXED
    val customCommandTemplate: List<String>? = null, // required when mode == CUSTOM
)
```
Added to `ProjectConfig` as `val terminalPreference: TerminalPreference = TerminalPreference()`, defaulting to auto-detect so existing `projects.json` files deserialize without migration (`JsonProjectRepository` uses plain `Json { prettyPrint = true }`, no default-value leniency concerns since the field has a default).

**Alternative considered**: a `sealed class TerminalPreference` (`AutoDetect`, `Fixed(emulator)`, `Custom(template)`) — more idiomatic Kotlin, but kotlinx.serialization needs a `SerializersModule` with polymorphic registration for sealed classes, which `JsonProjectRepository`'s `Json { prettyPrint = true }` doesn't set up today. The flat enum+nullable-fields shape serializes with zero extra configuration and keeps `JsonProjectRepository` untouched.

**`CUSTOM` mode is the generalist escape hatch**: `customCommandTemplate` is an argv list (never a shell string, per project convention) containing the literal placeholder tokens `{path}` and `{command}`, substituted per-argument before exec. Example: `["konsole", "--workdir", "{path}", "-e", "bash", "-c", "{command}"]`. This covers any terminal emulator not in the built-in enum without adding new code.

### 3. Agent launch command is configurable too (`AgentLaunchConfig`)
```kotlin
@Serializable
data class AgentLaunchConfig(
    val agentCommand: String = "claude",
    val useHeadroom: Boolean = true,
) {
    fun resolvedCommand(): String = if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
```
Added to `ProjectConfig` as `val agentLaunchConfig: AgentLaunchConfig = AgentLaunchConfig()`, same additive/default-value approach as `TerminalPreference`. `OpenWorktreeTerminalUseCase` calls `config.agentLaunchConfig.resolvedCommand()` instead of hardcoding `"headroom wrap claude"`, so a project without Headroom installed can turn `useHeadroom` off and just run `claude` (or any other agent binary/CLI the user names in `agentCommand`).

**Why a flat `agentCommand: String` instead of an enum like `TerminalEmulator`**: unlike terminal emulators, the "agent" here is just an arbitrary CLI invocation (`claude`, a wrapper script, another vendor's CLI) with no OS-specific launch mechanics to branch on — there's nothing to build per case, so a free-text field is both simpler and already fully generalist. `useHeadroom` stays a separate boolean rather than folding it into `agentCommand` as free text, so the UI can offer it as a single toggle instead of the user having to remember to type `headroom wrap` themselves.

**Alternative considered**: fold Headroom wrapping into `agentCommand` as plain text (e.g. user types `"headroom wrap claude"` directly, no separate toggle). Rejected — it works but loses the one-click "I don't have Headroom, turn it off" affordance and makes the common case (swapping compressor on/off) a text-edit instead of a toggle.

### 4. Emulator → argv mapping lives in infrastructure, resolved via a small strategy table
`infrastructure/terminal/` gets one `TerminalLauncher` implementation per OS-family concern:
- `ProcessTerminalLauncher`: takes an already-resolved argv (`List<String>`) and does `ProcessBuilder(argv).start()`, not waiting for exit — the actual "launch a process" mechanic, analogous to `ProcessBuilderShellCommandExecutor` but fire-and-forget.
- `TerminalCommandBuilder` (pure function/object): `fun build(emulator: TerminalEmulator, workingDirectory: String, command: String): List<String>`, one branch per enum value producing the right argv:
  - `MACOS_TERMINAL` / `ITERM2`: `osascript` (built as multiple `-e` arguments, one per script line, never a single shell string) telling the respective app to run `cd '<path>' && <command>`:
    - `ITERM2`: opens a **new tab**, not a new window — `create tab with default profile` in `current window` (falls back to `create window with default profile` if no window exists yet), then `write text` runs the command in it. iTerm2's AppleScript dictionary supports tab creation directly, no extra permissions needed.
    - `MACOS_TERMINAL`: if no Terminal window exists yet, plain `do script` creates one and runs the command directly (fallback). Otherwise, forces a **new tab** deterministically before running the command: `activate` + `System Events` keystroke Cmd+T, then `do script ... in front window`. Terminal.app has no AppleScript command to create a tab directly — GUI-scripting Cmd+T through `System Events` is the only way, which requires the user to grant Accessibility permission; without it the keystroke silently no-ops and `do script ... in front window` degrades to reusing whatever tab is frontmost (the original bug: back-to-back launches typed over each other in the same tab, e.g. `cd '<path1>' && headroom wrap claudecd '<path2>' && headroom wrap claude`, concatenated with no separator — happens because Terminal only auto-opens a new tab for a "busy" target, and right as the agent starts the tab still reads as idle). A freshly-Cmd+T'd tab is always idle, so `do script ... in front window` uses it deterministically once the permission is granted — expected to work reliably once the app is packaged as a proper `.app`/dmg with a stable bundle identity (macOS should then prompt for the permission correctly, unlike the dev-mode `./gradlew :desktopApp:run` process tree which doesn't reliably trigger it).
  - `WARP`: `open -a Warp <path>` — Warp's public scripting surface doesn't reliably support injecting a startup command across versions, so v1 opens Warp at the path and leaves running `headroom wrap claude` to the user (documented limitation, see Risks).
  - `GNOME_TERMINAL`: `gnome-terminal --working-directory=<path> -- bash -c "<command>; exec bash"`.
  - `KONSOLE`: `konsole --workdir <path> -e bash -c "<command>; exec bash"`.
  - `XTERM`: `xterm -e bash -c "cd '<path>' && <command>; exec bash"`.

### 5. Auto-detection is a domain port too, kept separate from `TerminalLauncher`
```kotlin
interface TerminalEmulatorDetector {
    suspend fun detectAvailable(): List<TerminalEmulator>
}
```
Implementation checks, per OS: app-bundle existence under `/Applications/*.app` (via the existing `FileManager.exists`) for macOS entries, and `which <binary>` (via the existing `ShellCommandExecutor`, since that's exactly what it's for — running a command and reading its exit code) for Linux binaries. `OpenWorktreeTerminalUseCase` calls this when `TerminalPreference.mode == AUTO_DETECT`, picking the first available emulator in a fixed priority order (platform-native first: Terminal.app/iTerm2 on macOS, gnome-terminal/konsole/xterm on Linux).

**Alternative considered**: detect once at project-creation time and store the resolved emulator. Rejected — the user's installed terminals can change after project creation (e.g. installs iTerm2 later), and re-detecting per-launch is cheap (one `which`/file-exists check).

### 6. New use case and error variant, following the existing pattern
```kotlin
class OpenWorktreeTerminalUseCase(
    private val config: ProjectConfig,
    private val launcher: TerminalLauncher,
    private val detector: TerminalEmulatorDetector,
    private val commandBuilder: TerminalCommandBuilder = TerminalCommandBuilder,
) {
    suspend operator fun invoke(worktree: Worktree): Result<Unit> = runCatching {
        val emulator = resolveEmulator(config.terminalPreference) // throws WorktreeError.NoTerminalAvailable if none
        val argv = commandBuilder.build(emulator, worktree.path, config.agentLaunchConfig.resolvedCommand())
        launcher.launch(argv, worktree.path)
            .getOrElse { throw WorktreeError.TerminalLaunchFailed(it) }
    }
}
```
New `WorktreeError` variants: `NoTerminalAvailable` (auto-detect found nothing, or `FIXED` points at an emulator not installed) and `TerminalLaunchFailed(cause)` (process failed to start), mirroring `GitCommandFailed`/`SecretCopyFailed`'s shape.

### 7. Wiring and UI
- `AppContainer.worktreeUseCases` gains `openTerminal: OpenWorktreeTerminalUseCase`, built from a new `TerminalLauncher`/`TerminalEmulatorDetector` pair chosen by `System.getProperty("os.name")` at container-construction time (mirrors how `ProcessBuilderShellCommandExecutor` is a single stateless instance today).
- `WorktreeViewModel` gains `fun openTerminal(worktree: Worktree)`, same `viewModelScope.launch { ... onSuccess/onFailure }` shape as `remove`.
- `WorktreeRow` gains a second `OutlinedButton("Terminal")` next to "Eliminar", calling a new `onOpenTerminal: () -> Unit` callback — no layout restructuring needed, same `Row`/`SpaceBetween` pattern.
- `ProjectConfigScreen`/`ProjectConfigViewModel` gain a terminal-emulator selector (radio group over `TerminalSelectionMode` + `TerminalEmulator.entries`, plus a text field for the `CUSTOM` template) and an agent launch section (text field for `agentCommand`, checkbox/switch for `useHeadroom`), following the existing form pattern (`secretFiles` list editing) already in that screen.

## Risks / Trade-offs

- **Warp has no reliable scripted "run this command" API** → v1 opens Warp at the right path but doesn't auto-run `headroom wrap claude`; the user still runs it manually once. Documented as a known limitation in the emulator picker's UI copy.
- **AppleScript/`osascript` and Linux terminal flags are brittle across app versions** (e.g. iTerm2 AppleScript dictionary changes between major versions) → mitigate by keeping `TerminalCommandBuilder` a single pure, well-tested function per emulator, and surfacing `WorktreeError.TerminalLaunchFailed` with the underlying process error so failures are visible rather than silent.
- **Fire-and-forget means we never know if `headroom wrap claude` actually succeeded inside the terminal** → accepted as a non-goal; the terminal window itself is the feedback surface for that.
- **`CUSTOM` template substitution must not reintroduce shell-string execution** → mitigate by requiring `customCommandTemplate` to be an argv list with placeholder tokens substituted per-element (never concatenated into one string passed to `sh -c`), consistent with the project's argv-only convention.
- **Terminal.app's tab-forcing depends on Accessibility permission** for `System Events` to control Terminal via Cmd+T → in dev mode (`./gradlew :desktopApp:run`, no stable bundle identity) macOS doesn't reliably prompt for it, so the keystroke silently no-ops and launches degrade to reusing whatever tab is frontmost instead of a fresh one. Mitigated by the empty-window fallback (no windows → plain `do script` opens one directly, no tab ambiguity). Packaging the app as a proper `.app`/dmg is expected to let macOS prompt for the permission correctly — pending verification.

## Migration Plan

Additive change: `ProjectConfig.terminalPreference` and `ProjectConfig.agentLaunchConfig` both have default values, so existing persisted `projects.json` entries deserialize unchanged as `AUTO_DETECT` / `claude` wrapped with `headroom wrap`. No data migration step needed. No rollback concerns beyond reverting the code change.

## Open Questions

- Is auto-detect priority order (native app first, then common Linux emulators) what the user actually wants as default, or should the first-ever launch prompt the user to pick explicitly instead of silently picking one?
