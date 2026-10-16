# Infrastructure layer

Lives in `desktopApp/src/main/kotlin/app/luxion/shogunai/infrastructure`. Implements the ports defined in the domain.

## `ProcessBuilderShellCommandExecutor`

Implementation of `ShellCommandExecutor` on top of `ProcessBuilder`. Robustness details:

- **Reads stdout and stderr concurrently** (two `async`s). If read sequentially, a process that fills the buffer of the unread stream would block (the classic `ProcessBuilder` deadlock).
- `waitFor()` is wrapped in `runInterruptible` so that coroutine cancellation interrupts waiting for the process.
- Blocking work is confined to `Dispatchers.IO` (injectable via constructor for tests).
- In the `finally` block, `process.destroy()` is called if the process is still alive, to avoid leaving orphan processes if the coroutine is cancelled.
- **Homebrew directories in `PATH`**: apps launched from the macOS Finder/Dock don't inherit the shell's `PATH`, so tools installed with Homebrew (e.g. `git-lfs`) wouldn't be found. `HomebrewPath.merge` prepends `/opt/homebrew/bin`, `/opt/homebrew/sbin` and `/usr/local/bin` to the child process's `PATH`, without duplicates.
- If the process can't start at all, the `IOException` is wrapped in `CommandExecutionException`.

## `NioFileManager`

Implementation of `FileManager` on top of `java.nio.file`. `copy` uses `REPLACE_EXISTING` and `COPY_ATTRIBUTES`. `directorySize` walks the tree with `Files.walkFileTree` without following symlinks, adds up regular file sizes, skips the excluded folders and ignores files it can't read. Works the same on every supported OS.

## `AwtClipboardWriter`

Implementation of `ClipboardWriter` with AWT's system clipboard (`Toolkit.getDefaultToolkit().systemClipboard`), wrapped in `runCatching`.

## Project catalog (`projects.json`)

`JsonProjectRepository` stores the projects in `~/.shogunai/projects.json` with `kotlinx.serialization` (`ignoreUnknownKeys`, so files with fields from older versions still load). Every write replaces the whole file atomically: it's written to a temporary file in the same folder and moved over the original with `ATOMIC_MOVE`, so a crash never leaves a half-written catalog.

## User preferences (`settings.json`)

`JsonSettingsFile` is the `~/.shogunai/settings.json` file, shared by `JsonThemePreferenceRepository` (`ThemePreferenceRepository` port) and `JsonLanguagePreferenceRepository` (`LanguagePreferenceRepository` port). It stores `{ "themeMode": ..., "language": ... }`.

- **Each save only rewrites its own field**: `update` reads the current contents, applies the change and writes the whole file atomically (same pattern as `JsonProjectRepository`). Changing the language doesn't overwrite the theme and vice versa.
- **Defaults**: `SYSTEM` for the theme and `ENGLISH` for the language when the file is missing, empty or unparseable. Files written by older versions (only `themeMode`) are read fine: the language takes its default.

## Test doubles

In `desktopApp/src/test/kotlin/app/luxion/shogunai/domain/`:

- `FakeShellCommandExecutor` — substitutes `ShellCommandExecutor`: answers each argv with a canned `CommandResult` and records the commands run.
- `FakeFileManager` — substitutes `FileManager` with an in-memory set of existing paths.
- `FakeClipboardWriter` — substitutes `ClipboardWriter`.
- `FakeProjectRepository` — substitutes `ProjectRepository` in ViewModel tests.

Thanks to the domain depending on interfaces, the use case tests (`*UseCaseTest`) and the ViewModel tests don't touch disk or launch real processes. The infrastructure itself is covered by `ProcessBuilderShellCommandExecutorTest`, `JsonProjectRepositoryTest` and `JsonSettingsFileTest`, which do use real processes and temporary files.
