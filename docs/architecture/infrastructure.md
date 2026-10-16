# Infrastructure layer

Lives in `core/src/main/kotlin/app/luxion/shogunai/infrastructure`, except `AwtClipboardWriter`, which needs the desktop toolkit and stays in `desktopApp` under the same package. Implements the ports defined in the domain.

## `ProcessBuilderShellCommandExecutor`

Implementation of `ShellCommandExecutor` on top of `ProcessBuilder`. Robustness details:

- **Reads stdout and stderr concurrently** (two `async`s). If read sequentially, a process that fills the buffer of the unread stream would block (the classic `ProcessBuilder` deadlock).
- `waitFor()` is wrapped in `runInterruptible` so that coroutine cancellation interrupts waiting for the process.
- Blocking work is confined to `Dispatchers.IO` (injectable via constructor for tests).
- In the `finally` block, `process.destroy()` is called if the process is still alive, to avoid leaving orphan processes if the coroutine is cancelled.
- **Homebrew directories in `PATH`**: apps launched from the macOS Finder/Dock don't inherit the shell's `PATH`, so tools installed with Homebrew (e.g. `git-lfs`) wouldn't be found. `HomebrewPath.merge` prepends `/opt/homebrew/bin`, `/opt/homebrew/sbin` and `/usr/local/bin` to the child process's `PATH`, without duplicates.
- If the process can't start at all, the `IOException` is wrapped in `CommandExecutionException`.

## `NioFileManager`

Implementation of `FileManager` on top of `java.nio.file`. `copy` creates the destination's missing parent folders and uses `REPLACE_EXISTING` and `COPY_ATTRIBUTES`. `directorySize` walks the tree with `Files.walkFileTree` without following symlinks, adds up regular file sizes, skips the excluded folders and ignores files it can't read. `modificationStamps` walks the same way and records each entry's last-modified time. `readProperties` loads the file with `java.util.Properties`. Works the same on every supported OS.

## `ProcessGradleDaemonSource`

Implementation of `GradleDaemonSource` that doesn't depend on a Gradle binary or on Gradle's internal registry format:

- **Processes**: runs `ps -A -ww -o pid=,rss=,command=` and keeps the processes whose arguments contain `org.gradle.launcher.daemon.bootstrap.GradleDaemon` (the next argument is the Gradle version) or `org.jetbrains.kotlin.daemon.KotlinCompileDaemon`. `rss` is converted from KiB to bytes. A failing `ps` (e.g. on Windows) throws `ProcessListingFailed`.
- **State**: each Gradle daemon writes `$GRADLE_USER_HOME/daemon/<version>/daemon-<pid>.out.log` (`GRADLE_USER_HOME` defaults to `~/.gradle`). The last 1 MiB of that log is scanned backwards for the latest `Marking the daemon as busy|idle` (with its timestamp, for `Idle(since)`; any other state counts as busy) and the latest `Received command: Build{…, currentDir=<path>}`, which gives the last build directory: the directory the Gradle client was started from, so `./gradlew -p <worktree>` run from elsewhere is attributed to where it was run. Both lines are logged at INFO level on every build. A missing or unreadable log, or no marker in that window, gives `Unknown`, which never allows stopping.
- Gradle's own `--status` and `--stop` aren't used: they only see the daemons of the Gradle version that runs them, and `--stop` also stops busy ones.

## `RepositoryProvisioningFiles`

Implementation of `RepositoryProvisioningSource` on `java.nio.file` and `kotlinx.serialization`, over `<repo>/.shogunai.json` and `<repo>/.worktreeinclude`.

- **Reading**: a missing or blank `.shogunai.json` means no patterns. The file is parsed as a generic JSON object, so unknown fields are ignored; invalid JSON, a non-object root, a `copy` that isn't a list of strings, or a `providers` (or a known provider in it) that isn't an object throw `InvalidRepositoryConfig`. Unknown provider ids are skipped.
- **Writing** (`addCopyPatterns`): reads the object, replaces only `copy` (existing patterns first, new ones appended without duplicates) and writes it back pretty-printed, atomically (temporary file in the same folder + `ATOMIC_MOVE`), so other fields survive. It refuses to rewrite a file it can't parse.

Pattern matching is left to Git (`git ls-files --others --ignored -z -x <pattern>…`, see the domain's `CreateWorktreeUseCase`), so it follows the gitignore rules exactly, including their limits: a file can't be re-included with `!` if its parent folder is excluded (`.idea/` + `!.idea/workspace.xml` still lists `workspace.xml`; use `.idea/*` instead). Try a pattern from the base repository with the same command:

```bash
git ls-files --others --ignored -x '/local.properties' -x '.env*' -x '.idea/runConfigurations/'
```

## `AwtClipboardWriter`

In `desktopApp`. Implementation of `ClipboardWriter` with AWT's system clipboard (`Toolkit.getDefaultToolkit().systemClipboard`), wrapped in `runCatching`.

## Project catalog (`projects.json`)

`JsonProjectRepository` stores the projects in `~/.shogunai/projects.json` with `kotlinx.serialization` (`ignoreUnknownKeys`, so files with fields from older versions still load). Every write replaces the whole file atomically: it's written to a temporary file in the same folder and moved over the original with `ATOMIC_MOVE`, so a crash never leaves a half-written catalog.

## User preferences (`settings.json`)

`JsonSettingsFile` is the `~/.shogunai/settings.json` file, shared by `JsonThemePreferenceRepository` (`ThemePreferenceRepository` port) and `JsonLanguagePreferenceRepository` (`LanguagePreferenceRepository` port). It stores `{ "themeMode": ..., "language": ... }`.

- **Each save only rewrites its own field**: `update` reads the current contents, applies the change and writes the whole file atomically (same pattern as `JsonProjectRepository`). Changing the language doesn't overwrite the theme and vice versa.
- **Defaults**: `SYSTEM` for the theme and `ENGLISH` for the language when the file is missing, empty or unparseable. Files written by older versions (only `themeMode`) are read fine: the language takes its default.

## Test doubles

In `core/src/testFixtures/kotlin/app/luxion/shogunai/domain/`, `core`'s test fixtures, used by `core`'s tests and by `desktopApp`'s tests (`testImplementation(testFixtures(project(":core")))`):

- `FakeShellCommandExecutor` — substitutes `ShellCommandExecutor`: answers each argv with a canned `CommandResult` and records the commands run.
- `FakeFileManager` — substitutes `FileManager` with an in-memory set of existing paths and properties files.
- `FakeGradleDaemonSource` — substitutes `GradleDaemonSource` with a mutable list of daemons (or a failure) and counts the listings; `gradleDaemon(...)` and `kotlinDaemon(...)` build them.
- `FakeClipboardWriter` — substitutes `ClipboardWriter`.
- `FakeProjectRepository` — substitutes `ProjectRepository` in ViewModel tests.
- `FakeRepositoryProvisioningSource` — substitutes `RepositoryProvisioningSource` with provisioning per repository path, unreadable repositories and failing writes, and records the patterns added.

Thanks to the domain depending on interfaces, the use case tests (`*UseCaseTest`) and the ViewModel tests don't touch disk or launch real processes. The infrastructure itself is covered by `ProcessBuilderShellCommandExecutorTest`, `JsonProjectRepositoryTest`, `JsonSettingsFileTest`, `RepositoryProvisioningFilesTest` and `ProcessGradleDaemonSourceTest` (on real `ps` lines and daemon log excerpts), which do use real processes and temporary files.
