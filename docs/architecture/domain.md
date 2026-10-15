# Domain layer

Lives in `desktopApp/src/main/kotlin/app/luxion/shogunai/domain`. Doesn't depend on any concrete infrastructure implementation.

## Models

### `ProjectConfig`

Configuration of a repository that worktrees are created against. It's what makes the flow reusable across projects, instead of hardcoding paths or file names.

| Field | Description |
|---|---|
| `baseRepositoryPath` | Absolute path of the main repository, from which Git is run. |
| `worktreesRoot` | Parent folder where worktrees are created. |
| `secretFiles` | Local credential files to copy into the new worktree (e.g. `local.properties`). |
| `terminalPreference` | How to choose the terminal emulator when opening one from a worktree (`TerminalPreference`, see below). |
| `agentLaunchConfig` | Which agent command to run in that terminal (`AgentLaunchConfig`, see below). |

Exposes two path helpers: `worktreePathFor(taskId)` (`<worktreesRoot>/<taskId>`) and `baseSecretPath(fileName)`.

### `TerminalPreference` / `AgentLaunchConfig`

```kotlin
enum class TerminalEmulator { MACOS_TERMINAL, ITERM2, WARP, GNOME_TERMINAL, KONSOLE, XTERM }
enum class TerminalSelectionMode { AUTO_DETECT, FIXED, CUSTOM }

@Serializable
data class TerminalPreference(
    val mode: TerminalSelectionMode = TerminalSelectionMode.AUTO_DETECT,
    val emulator: TerminalEmulator? = null,          // required if mode == FIXED
    val customCommandTemplate: List<String>? = null, // required if mode == CUSTOM
)

@Serializable
data class AgentLaunchConfig(
    val agentCommand: String = "claude",
    val useHeadroom: Boolean = true,
) {
    fun resolvedCommand(): String = if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
```

Both are flat `data class`es with default values (not `sealed class`) so they serialize with `kotlinx.serialization` without configuring a polymorphic `SerializersModule` — `JsonProjectRepository` just uses `Json { prettyPrint = true }`. `customCommandTemplate` is an argv list with the literal placeholders `{path}`/`{command}`, substituted per element (never concatenated into a shell string), which covers any emulator outside the enum without touching code.

### `BranchType`

Enum `FEATURE` / `FIX`, each with a `prefix` (`"feature"` / `"fix"`) prepended to the task ID when naming the branch: `feature/TASK-123`.

### `Worktree`

Represents an active worktree as reported by `git worktree list`: `path`, `branch` (`null` if `detached`), `head`, `isMain`, `isBare`.

### `WorktreeError`

`sealed class` of the flow's expected errors, so the future GUI can decide what to show without parsing strings:

- `BaseRepositoryNotFound` — the configured base repo doesn't exist on disk.
- `WorktreeAlreadyExists` — there's already a directory at the destination path.
- `SecretFileNotFound` — secret files are missing in the base repo.
- `GitCommandFailed` — a Git command returned a non-zero exit code.
- `SecretCopyFailed` — copying secrets failed after the worktree had already been created.
- `NoTerminalAvailable` — no terminal emulator was found available (empty auto-detect, or `FIXED` points to one that isn't installed).
- `TerminalLaunchFailed` — the terminal process didn't start.

## Ports

Use cases depend on interfaces, not implementations. This is what allows testing them with doubles.

### `ShellCommandExecutor`

```kotlin
interface ShellCommandExecutor {
    suspend fun execute(command: List<String>, workingDirectory: String? = null): CommandResult
}
```

The command is passed as **argv** (a list of strings), never as a string for a shell to interpret. Reason: to avoid differences between macOS's default shell (zsh) and Arch Linux (bash), and to avoid command escaping/injection issues.

`CommandResult` holds `command`, `exitCode`, `stdout`, `stderr` without interpreting their meaning — each use case decides what counts as success via `isSuccess` (`exitCode == 0`).

### `FileManager`

```kotlin
interface FileManager {
    fun exists(path: String): Boolean
    fun copy(source: String, destination: String)
}
```

Abstracts the filesystem so that copying secrets is unit-testable without touching disk.

### `TerminalLauncher`

```kotlin
interface TerminalLauncher {
    suspend fun launch(command: List<String>, workingDirectory: String): Result<Unit>
}
```

Different from `ShellCommandExecutor`: it only confirms the terminal process *started*, and never waits for it to close (a terminal stays open for the whole session; waiting on its `waitFor()` would block the coroutine indefinitely).

### `TerminalEmulatorDetector`

```kotlin
interface TerminalEmulatorDetector {
    suspend fun detectAvailable(): List<TerminalEmulator>
}
```

Detects which emulators are installed on the current system, in priority order (OS-native first). `OpenWorktreeTerminalUseCase` uses it both for `AUTO_DETECT` (picks the first one available) and for `FIXED` (checks that the configured emulator is among the ones available).

## Use cases

All return `Result<T>` (using `runCatching`), with typed errors as `WorktreeError`.

### `CreateWorktreeUseCase`

Orchestrates, in this order:

1. **Upfront validations** (base repo exists, destination free, secrets present) — done *before* touching Git so as not to leave a half-done worktree that couldn't later be completed.
2. `git worktree add <path> -b <branch>`.
3. Copying the secret files into the new directory.

If copying secrets fails **after** the worktree was created, it's rolled back with `git worktree remove --force` (best-effort) before propagating `SecretCopyFailed`.

### `RemoveWorktreeUseCase`

Runs `git worktree remove [--force] <path>` and, optionally, `git branch -d <branch>` to delete the local branch (safe delete, not `-D`).

!!! note
    Both commands always run from the base repository — Git doesn't allow removing a worktree from inside itself.

### `ListWorktreesUseCase`

Runs `git worktree list --porcelain` (a stable format meant to be parsed, unlike the default human-readable output) and parses the result with `parseWorktreeList`. Marks `isMain = true` on the worktree whose path matches `config.baseRepositoryPath`.

### `OpenWorktreeTerminalUseCase`

Opens a terminal at a worktree's path and runs `config.agentLaunchConfig.resolvedCommand()` there. Resolves the emulator based on `config.terminalPreference.mode`:

- `AUTO_DETECT` — first emulator returned by `TerminalEmulatorDetector.detectAvailable()`.
- `FIXED` — the configured emulator, if it's among the detected ones (otherwise, `NoTerminalAvailable`).
- `CUSTOM` — uses `customCommandTemplate` directly, without going through the detector.

The final argv is built by `TerminalCommandBuilder` (a pure object in `domain/usecase/`), with one branch per `TerminalEmulator` plus placeholder substitution for `CUSTOM`. Failures starting the process are mapped to `WorktreeError.TerminalLaunchFailed`.
