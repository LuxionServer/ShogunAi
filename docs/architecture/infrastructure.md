# Infrastructure layer

Lives in `core/src/main/kotlin/app/luxion/shogunai/infrastructure`, except `AwtClipboardWriter`, which needs the desktop toolkit and stays in `desktopApp` under the same package. Implements the ports defined in the domain.

## `ProcessBuilderShellCommandExecutor`

Implementation of `ShellCommandExecutor` on top of `ProcessBuilder`. Robustness details:

- **Reads stdout and stderr concurrently** (two `async`s). If read sequentially, a process that fills the buffer of the unread stream would block (the classic `ProcessBuilder` deadlock).
- **Never waits for input**: standard input comes from the null device (`/dev/null`, `NUL` on Windows), so a command that asks a question gets end of input instead of hanging. Git commands also get `GIT_TERMINAL_PROMPT=0`, so missing credentials fail at once.
- `waitFor()` is wrapped in `runInterruptible` so that coroutine cancellation interrupts waiting for the process; `executeWithin` wraps it in `withTimeoutOrNull` and throws `CommandExecutionException.TimedOut` when the limit passes.
- Blocking work is confined to `Dispatchers.IO` (injectable via constructor for tests).
- In the `finally` block, a process that is still alive (cancelled or timed out) is killed with `destroyForcibly()` together with its descendants, so no orphan processes are left and the stream readers are released.
- **Git in English**: Git commands run with `LC_ALL=C` (`GitLocale`), so the error classification can match Git's messages.
- **Long paths on Windows**: on Windows every Git command runs as `git -c core.longpaths=true …` (`GitLongPaths`), so Git can create, read and delete files whose full path is longer than 260 characters, such as deep build outputs. The `CommandResult` keeps the command as the caller built it, and the user's Git configuration isn't touched.
- **Homebrew directories in `PATH`**: apps launched from the macOS Finder/Dock don't inherit the shell's `PATH`, so tools installed with Homebrew (e.g. `git-lfs`) wouldn't be found. `HomebrewPath.merge` prepends `/opt/homebrew/bin`, `/opt/homebrew/sbin` and `/usr/local/bin` to the child process's `PATH`, without duplicates.
- **Arguments intact on Windows**: the JDK joins argv into one command line and wraps arguments with spaces in quotes without escaping the quotes they contain, so a value like `GRADLE_OPTS=-Dx="a b" -Dy=1` would reach the program split in several. On Windows `WindowsArguments` quotes each argument following the `CommandLineToArgvW` rules (embedded quotes escaped, backslashes before quotes and at the end doubled) before the JDK sees it; the JDK leaves an argument that is already quoted alone. The executable itself and commands whose executable is a `.cmd` or `.bat` file are left unchanged, because `cmd.exe` doesn't treat `\"` as an escape. `LocalAndroidSdk` uses the same function to start the emulator.
- If the process can't start at all, the `IOException` is wrapped in `CommandExecutionException`.

## `NioFileManager`

Implementation of `FileManager` on top of `java.nio.file`. `isInside` resolves the root with `toRealPath()` and walks up from the path to its nearest existing ancestor (without following a final symlink), resolves that one too, and requires the root as a prefix. `createSymbolicLink` deletes whatever is at the link and creates the symlink, mapping `UnsupportedOperationException` and a file system refusal to `SymbolicLinksNotSupported`; `isSymbolicLinkTo` checks `Files.isSymbolicLink` and `Files.isSameFile`; `hasSameContent` uses `Files.mismatch`. `copy` creates the destination's missing parent folders and uses `REPLACE_EXISTING` and `COPY_ATTRIBUTES`. `directorySize` walks the tree with `Files.walkFileTree` without following symlinks, adds up regular file sizes, skips the excluded folders and ignores files it can't read. `modificationStamps` walks the same way and records each entry's last-modified time. `readProperties` loads the file with `java.util.Properties`. `writeText` creates the missing parent folders and writes atomically (temporary file in the same folder, then `ATOMIC_MOVE`); `readText` returns `null` for anything that isn't a readable regular file; `isDirectory` wraps `Files.isDirectory`. Works the same on every supported OS.

## `NioEnvironmentStateStore` / `SocketPortProbe`

- **State**: each worktree's reservations live in `shogunai-env.json` inside its Git administrative directory, as `{ "ports": { "PORT": 20001, "port:api": 20002 }, "resources": { "ios-simulator": "<UDID>" } }`; files without `resources` load as before. Git ignores files it doesn't know there and deletes the whole directory on `git worktree remove` and `prune`, so reservations go away with the worktree, whoever created it. Other fields of the object are kept for later providers; a file left without fields is deleted; an unreadable file counts as no reservations. Writes are atomic (temporary file and move).
- **Machine listing**: the current repository's files (the common directory's, for the main worktree, plus every `worktrees/*/shogunai-env.json`) and every administrative directory in the machine index `~/.shogunai/reservations.json` (`{ "worktrees": [ … ] }`). Writing a worktree's ports adds its directory to the index, or removes it when it has none left; listing drops index entries whose state file is gone (the worktree was removed or pruned), so the index heals itself and ports of removed worktrees in any repository become free.
- **Lock**: `withLock` takes an exclusive `FileChannel` lock on `~/.shogunai/reservations.lock`, so parallel creations, even from several processes (several Claude Code sessions) and different repositories, can't reserve the same port. A process-wide lock guards the file lock, and a nested call on the same thread runs directly.
- **Waiting queue**: `NioDeviceWaitQueue` keeps the worktrees waiting for an emulator or simulator in `~/.shogunai/device-queue.json` (`{ "queues": { "emulator": [ … ], "ios-simulator": [ … ], "desktop": [ … ] } }`), each ticket with its worktree, process id and start time and when it started waiting. It is read and written only inside `withLock`, so being first and taking capacity are one decision; a missing or unreadable file is an empty queue. `NioDesktopTurnStore` keeps the desktop turn's holder in `~/.shogunai/desktop-turn.json`, written atomically and deleted when nobody holds it; a missing or corrupt file means a free desktop. `ProcessHandleLiveness` says whether a ticket's process still runs, comparing its start time so a reused process id doesn't keep a dead waiter in line.
- **Free port**: `SocketPortProbe` binds a `ServerSocket` with `reuseAddress = false` on the wildcard address and on the loopback address; the port is free only if both succeed.

## `SimctlAppleSimulators` / `FileKmpTestWiring`

- **Simulators**: `xcrun simctl list -j runtimes` (available runtimes with their `supportedDeviceTypes`) and `list -j devices` (`Booted`, `Shutdown`, anything else counts as booting), `create <name> <device type> <runtime>` (prints the UDID), `boot`, `shutdown`, `delete`, all through the command executor. `show` runs `open -b com.apple.iphonesimulator --args -CurrentDeviceUDID <udid>` and, if the Simulator app isn't installed (Xcode 27 replaced it with Device Hub), `open -b com.apple.dt.Devices`, so the device gets a window; `simctl boot` alone boots it headless. "Unable to boot/shutdown device in current state: Booted/Shutdown" counts as success. `isSupported()` is true only on macOS.
- **KMP wiring**: the repository's `*.gradle.kts` and `*.gradle` files, up to five levels deep and skipping `build`, `.gradle`, `.git`, `.kotlin`, `.idea` and `node_modules`, are searched for `IOS_SIMULATOR_UDID`, and, with the same walk, for `multiplatform` (ignoring case) to tell a Kotlin Multiplatform repository, which covers `kotlin("multiplatform")`, the plugin id and version catalog aliases such as `libs.plugins.kotlinMultiplatform`. The init script is `$GRADLE_USER_HOME/init.d/shogunai-ios-simulator.init.gradle.kts`, in Kotlin DSL because Groovy scripts fail to compile on recent JDKs; since the Kotlin plugin isn't on its classpath it matches `KotlinNativeSimulatorTest` tasks by class name and sets `device` through reflection, and it does nothing when `IOS_SIMULATOR_UDID` isn't set. Uninstalling it also removes `init.d` if it's left empty. `REPOSITORY_SNIPPET` is the Kotlin DSL fragment shown to users.

## `LocalAndroidSdk`

Implementation of `AndroidSdk` on the SDK's own tools:

- **Locating**: the first of `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` in the repository's `local.properties`, and the platform default (`~/Library/Android/sdk`, `~/Android/Sdk`, `%LOCALAPPDATA%\Android\Sdk`) that contains `emulator/emulator`; executables get `.exe` on Windows.
- **AVDs**: `emulator -list-avds`, skipping the emulator's informational lines.
- **States**: one `adb devices`, keeping `emulator-*` serials; an `offline` one is booting, a `device` one is running once `adb -s <serial> shell getprop sys.boot_completed` returns `1`. Serials whose boot was seen completed are remembered and not queried again while they stay listed as `device`; a serial that leaves the list or is stopped is forgotten.
- **Start**: `emulator -avd <avd> -port <port> -read-only -no-boot-anim [-no-window]` through `ProcessBuilder`, not the command executor, because it must keep running after the caller (the CLI) exits; output goes to `~/.shogunai/logs/emulator-<port>.log`. `-read-only` is what lets one AVD run once per worktree; changes to the emulator's data are discarded when it exits. It returns an `EmulatorProcess` over the started process and its log.
- **Stop**: `adb -s <serial> emu kill`.

## `SharedDeviceStatesAndroidSdk`

An `AndroidSdk` decorator the desktop app wraps around its single `LocalAndroidSdk`; the CLI uses `LocalAndroidSdk` directly. It delegates everything except `deviceStates`, which it serves from one shared read per `adb` path: a caller that arrives while a read runs waits for it (a `Mutex`), and one that arrives less than 2 seconds after the last read gets that result. Every project checked in the same cycle, the machine view and the boot wait thus share one `adb devices`. `stopEmulator` drops the cached result, so the next read after a stop is fresh. The clock is injected for tests.

## `HerdrConfigFile`

Implementation of `HerdrConfigSource`. It reads Herdr's `config.toml` from the same place Herdr does: `HERDR_CONFIG_PATH`, else `$XDG_CONFIG_HOME/herdr/`, else `%APPDATA%\herdr\` on Windows, else `~/.config/herdr/`. A small line reader takes the `directory` key of the `[worktrees]` table (basic or literal string, trailing comments allowed) and expands a leading `~`; it's not a full TOML parser, since that's the only key ShogunAi needs. A missing, unreadable or unparseable file, or no such key, gives Herdr's default, `~/.herdr/worktrees`.

## `CliHerdrClient`

Implementation of `HerdrClient` on the `herdr` command, through `ShellCommandExecutor` with argument lists: `worktree open --cwd <repository> --path <worktree> --label … --focus`, `pane split <pane> --direction right --cwd … --env NAME=value… --focus`, `pane close <pane>`, `pane run <pane> <command>`, `worktree list --cwd <repository>` (path and `open_workspace_id` of each entry under `result.worktrees`, skipping those Herdr doesn't have open), `workspace close <workspace>`, `agent list` (each entry's `agent`, `agent_status`, `foreground_cwd` or else `cwd`, and `pane_id`; `idle`, `working`, `blocked` and `done` map to `AgentState` and anything else to no state) and `agent focus <pane>`. It reads only the fields it needs from Herdr's JSON (`result.workspace.workspace_id`, `result.root_pane.pane_id`, `result.already_open`, `result.pane.pane_id`), so other fields can change. The binary is `~/.local/bin/herdr`, `~/.cargo/bin/herdr`, `/opt/homebrew/bin/herdr` or `/usr/local/bin/herdr` when they exist, in that order (where Herdr's install script, `cargo install` and Homebrew put it, which a Finder-launched app's `PATH` lacks), else `herdr` from the `PATH` (`herdr.exe` on Windows). A command that can't start is `HerdrNotInstalled`; a non-zero exit or an `error` object is `HerdrCommandFailed` with Herdr's `error.message`, taken from the JSON on standard output or standard error (where Herdr writes its errors), or the raw standard error when it isn't JSON. Herdr doesn't start a server for these commands, so with none running the message asks to run `herdr`.

## `FileAgentSessionStore`

Implementation of `AgentSessionStore`: one JSON file per session, `<git-dir>/shogunai/agent-sessions/<session id>.json`, where `<git-dir>` is the worktree's own Git directory: `.git` in the main worktree, or the directory the `gitdir:` line of a linked worktree's `.git` file points to. Git ignores unknown files there, the folder goes away with `git worktree remove` and nothing is copied to new worktrees. A write goes to a temporary file that is then moved over the target atomically, so a reader never sees half a record. Records that can't be parsed are skipped; a directory that isn't a worktree is silently ignored, except a multirepo folder (one holding `.shogunai-multirepo.json`) that has no `.git`: its records go to `~/.shogunai/agent-sessions/<24 hex characters of the SHA-256 of the canonical folder>/`, so the `agent-state` hook works there too and two folders never share records. Session ids are reduced to `[A-Za-z0-9_.-]` in the file name.

## `ProcessGradleDaemonSource`

Implementation of `GradleDaemonSource` that doesn't depend on a Gradle binary or on Gradle's internal registry format:

- **Processes**: runs `ps -A -ww -o pid=,rss=,command=` and keeps the processes whose arguments contain `org.gradle.launcher.daemon.bootstrap.GradleDaemon` (the next argument is the Gradle version) or `org.jetbrains.kotlin.daemon.KotlinCompileDaemon`. `rss` is converted from KiB to bytes. A failing `ps` (e.g. on Windows) throws `ProcessListingFailed`.
- **State**: each Gradle daemon writes `$GRADLE_USER_HOME/daemon/<version>/daemon-<pid>.out.log` (`GRADLE_USER_HOME` defaults to `~/.gradle`). The last 1 MiB of that log is scanned backwards for the latest `Marking the daemon as busy|idle` (with its timestamp, for `Idle(since)`; any other state counts as busy) and the latest `Received command: Build{…, currentDir=<path>}`, which gives the last build directory: the directory the Gradle client was started from, so `./gradlew -p <worktree>` run from elsewhere is attributed to where it was run. Both lines are logged at INFO level on every build. A missing or unreadable log, or no marker in that window, gives `Unknown`, which never allows stopping.
- Gradle's own `--status` and `--stop` aren't used: they only see the daemons of the Gradle version that runs them, and `--stop` also stops busy ones.

## `MacDerivedDataSource`

Implementation of `DerivedDataSource` for macOS (`isSupported` checks `os.name`):

- **Location**: `defaults read com.apple.dt.Xcode <key>` run as argv, first `IDEDerivedDataPathOverride`, then `IDECustomDerivedDataLocation` (keys confirmed in Xcode 27's `IDEFoundation`; a leading `~` is expanded); a failed `defaults` (key missing) falls back to `~/Library/Developer/Xcode/DerivedData`. A location that doesn't exist has no folders.
- **Folders**: every direct child with a regular `info.plist` that has a `WorkspacePath`, parsed with the JDK's XML parser with external entities and DTDs off. A binary plist (`bplist00`) or an unreadable file is skipped, and so are Xcode's shared caches, which have none. `LastAccessedDate` gives `lastAccessed`; `folders()` doesn't measure them: `size(folder)` walks one folder on `Dispatchers.IO` and stops as soon as its coroutine is cancelled, and `packagesSize(folder)` walks its `SourcePackages` the same way (zero when missing). `folders()` also runs on `Dispatchers.IO`.
- **Compilation cache**: `IDECustomCompilationCacheLocation` when set, else `<location>/CompilationCache.noindex`.
- **Package caches**: `MacPackageCacheSource` measures `~/Library/Caches/org.swift.swiftpm` and `~/Library/Caches/CocoaPods` with `FileManager.directorySize` on `Dispatchers.IO`, `null` for a missing folder or off macOS.
- **Volumes**: a path under `/Volumes/<name>` is on an unavailable volume when that mount point doesn't exist.
- **Deletion**: refuses any path that isn't a direct child of the current location or has no `info.plist`, then deletes the folder recursively.

## `RepositoryProvisioningFiles`

Implementation of `RepositoryProvisioningSource` on `java.nio.file` and `kotlinx.serialization`, over `<repo>/.shogunai.json` and `<repo>/.worktreeinclude`.

- **Reading**: a missing or blank `.shogunai.json` means no patterns. The file is parsed as a generic JSON object, so unknown fields are ignored; invalid JSON, a non-object root, a `copy` that isn't a list of strings, or a `providers` (or a known provider in it) that isn't an object throw `InvalidRepositoryConfig`. Unknown provider ids are skipped. `env` must be an object of strings whose templates parse (`parseEnvironmentVariable`), and `envFile` a string with a relative path that doesn't leave the worktree once normalized. Provider options are checked by type: Gradle memory sizes match `[0-9]+[kKmMgG]`, `idleTimeoutMinutes` is a positive JSON number (not a string), the emulator's and simulator's names are non-empty strings and Xcode's `compilationCache` and `cocoaPods` are booleans. The user-facing summary of these rules is in [Configuration](../guide/configuration.md#repository-configuration-shogunaijson).
- **Links and commands**: `link` follows the same rules as `copy`. `hooks` must be an object, and its `postCreate` a command (a list of strings) or a list of commands; anything else is `InvalidRepositoryConfig`. Variable names in `env` and the `docker-compose` provider's `portVariables` must not be reserved, compared without regard to case, and templates and `envFile` must not contain control or invisible format characters (`RepositoryConfigRules`, `hasHiddenCharacters`). Each variable keeps its declared template text for the approval subject.
- **Writing**: `addCopyPatterns` reads the object, replaces only `copy` (existing patterns first, new ones appended without duplicates); `write` applies a `RepositoryConfigEdit` (providers, `env`, `envFile` and patterns to add) and `preview` returns the text it would write. Both write it back pretty-printed, atomically (temporary file in the same folder + `ATOMIC_MOVE`), so other fields survive, and refuse to rewrite a file they can't parse. Given the fingerprint (a SHA-256 of the content) read when the settings opened, a write first checks the file still has it, and fails with `RepositoryConfigChanged` otherwise.

Pattern matching is left to Git (`git ls-files --others --ignored -z -x <pattern>…`, see the domain's `CreateWorktreeUseCase`), so it follows the gitignore rules exactly, including their limits: a file can't be re-included with `!` if its parent folder is excluded (`.idea/` + `!.idea/workspace.xml` still lists `workspace.xml`; use `.idea/*` instead). Try a pattern from the base repository with the same command:

```bash
git ls-files --others --ignored -x '/local.properties' -x '.env*' -x '.idea/runConfigurations/'
```

## Terminal, IDE and folder launchers

Adapters of the `TerminalLauncher`, `EditorLauncher`, `FolderLauncher` and `ToolDetector` ports, all through `ShellCommandExecutor` with argv lists. A failing tool command becomes the port's typed error with the tool's error output.

- **`MacTerminalLauncher`** (Terminal and iTerm2): `osascript` with a fixed script passed as `-e` lines and the launch line as the script's only argument (`on run argv … do script (item 1 of argv)` for Terminal; a new window with the default profile and `write text (item 1 of argv)` for iTerm2), so nothing is interpolated into AppleScript. Error `-1743` (automation not allowed) is `TerminalAutomationRefused`.
- **`WindowsTerminalLauncher`**: `wt.exe -w 0 new-tab -d <path> powershell.exe -NoExit -Command <line>`, escaping `;`, which `wt` reads as a command separator.
- **`TmuxTerminalLauncher`**: requires tmux 3.0 or later (`tmux -V`, else `TerminalUnsupported`), picks the most recently attached session, runs `tmux new-window -P -F '#{window_id}' -t =<session>: -c <path>` with `-e NAME=value` per variable, then `send-keys -l <agent command>` and `Enter`.
- **`ZellijTerminalLauncher`**: on the last session from `zellij list-sessions --short`, `zellij --session <session> action new-tab --cwd <path>`, then `write-chars <line>` and `write 13`.
- **`CommandEditorLauncher`**: on macOS `open -b <bundle id> <path>` for each of the IDE's bundle ids, falling back to its launcher; elsewhere the launcher (`code`, `idea`, `studio`, `.cmd` on Windows) found by `ExecutableLocator`, detached through `nohup` on Linux. Xcode has no launcher, so it exists only on macOS, and `XcodeWorkspaceFinder` (`FileSystemXcodeWorkspaceFinder`) gives the path it opens: the shallowest `.xcworkspace` in the worktree, else the shallowest `.xcodeproj` (up to four levels down, skipping hidden, `Pods`, `build`, `DerivedData`, `node_modules` and `Carthage` folders), else the worktree's folder.
- **`SystemFolderLauncher`**: `open`, `explorer.exe` (whose exit code 1 isn't a failure) or `xdg-open`.
- **`LocalToolDetector`**: macOS apps by bundle id (`mdfind "kMDItemCFBundleIdentifier == '<id>'"`) or by bundle name in `/Applications`, `/System/Applications` and `~/Applications`; executables through `ExecutableLocator`, which looks in `PATH`, the Homebrew, `~/.local/bin` and `~/.cargo/bin` folders and, for IDEs, JetBrains Toolbox's scripts folder. tmux and Zellij count only while they have a session (`tmux list-sessions`, `zellij list-sessions --short`). On Windows, IDEs are resolved by `EditorExecutableResolver`, shared by the detector and `CommandEditorLauncher` so opening runs what detection found: `PATH` with every `PATHEXT` extension, the official installer folders (`%ProgramFiles%`, `%ProgramFiles(x86)%`, `%LOCALAPPDATA%\Programs`, highest version first), Toolbox's scripts and apps folders and, last, the `InstallLocation` of the registry's uninstall entries (read with `reg query`, once per detection). Bundle ids, bundle names and executables live in `ToolCatalog`.
- **`NioLauncherFiles`** and **`PowerShellUserPath`** (ports `LauncherFiles` and `UserPathEntries`): what the `CommandLineTool` use case needs to expose the app as `shogun`. The first creates the symbolic link or `shogun.cmd`; the second reads and edits the Windows user `PATH` through `powershell` (argv, `[Environment]::SetEnvironmentVariable(..., 'User')`, which also notifies running programs). `ExecutableLocator` tries the `PATHEXT` extensions on Windows, so `shogun.cmd` and `shogun.exe` are found.

## `GitHubReleaseSource`

Implementation of `ReleaseSource` with `java.net.http.HttpClient` (no extra dependency): `GET https://api.github.com/repos/LuxionServer/ShogunAi/releases?per_page=30` with `Accept: application/vnd.github+json`, `User-Agent: ShogunAi/<version>` and a 10-second timeout, no token. One request brings the 30 most recent releases with their bodies, so the notes of every newer version come with it. For each release it skips drafts, pre-releases and tags that aren't a version, and reads `tag_name` (parsed by `AppVersion`), `html_url`, each asset's `name` and `browser_download_url` leniently, and `body` through `ReleaseNotes.parse`; another status or an answer that isn't a list of releases is `UpdateCheckFailed`.

## `JsonRepositoryTrust`

Implementation of `RepositoryTrust` in `~/.shogunai/trust.json`: `{ "approvals": { "<repository real path>": { "sha256": …, "commands": [[…]], "env": { … }, "envFile": … } } }`. The key is the repository's real path (`toRealPath`), so a moved repository asks again. For a subject without variables nor `envFile` the hash is a SHA-256 of the commands' canonical JSON, exactly as earlier versions stored it, so their approvals still hold; otherwise it is a SHA-256 of `{"commands": …, "env": [[name, template], …], "envFile": …}` with `env` sorted by name, so reordering `env` keys doesn't ask again while reordering commands does. The commands, variables and file are kept for display; older files without `env` or `envFile` are read with defaults, and older versions ignore the new fields. Writes are atomic, with owner-only permissions (`rw-------`) where the file system supports them; an unreadable file counts as nothing approved.

## `JsonMultirepoSource`

Reads and writes `.shogunai-multirepo.json` in a multirepo folder, and keeps the machine's registry in `~/.shogunai/multirepos.json`: `{ "multirepos": ["<declaration path>", …], "local": { "<declaration path>": { "approved": "<sha-256>", "agent": { … } } } }`. `approved` is the SHA-256 of the domain's approval subject and `agent` the multirepo agent set on this machine. Every registry access goes through `LocalFileLock`, writes are atomic with owner-only permissions (`rw-------`) where supported, and `forget` drops the `local` entry with the path. `multirepos` and each `local` entry are decoded on their own, so a malformed entry is skipped without hiding the rest. An earlier version reads `multirepos` and ignores `local`; if it rewrites the file, `local` is lost and those multirepos ask for review again and open `claude`.

## `AwtClipboardWriter`

In `desktopApp`. Implementation of `ClipboardWriter` with AWT's system clipboard (`Toolkit.getDefaultToolkit().systemClipboard`), wrapped in `runCatching`.

## Project catalog (`projects.json`)

`JsonProjectRepository` stores the projects in `~/.shogunai/projects.json` with `kotlinx.serialization` (`ignoreUnknownKeys`, so files with fields from older versions still load). Every write replaces the whole file atomically: it's written to a temporary file in the same folder and moved over the original with `ATOMIC_MOVE`, so a crash never leaves a half-written catalog.

- **Unreadable file**: a file that can't be parsed is reported as `LocalFileUnreadable` instead of being treated as empty. Before the next write it's moved aside as `projects.json.unreadable-<yyyyMMdd-HHmmss>`, so a corrupted catalog is never silently overwritten.
- **Concurrent writes**: every read and read-modify-write runs under `LocalFileLock`, a lock on a sibling `projects.json.lock` file (`FileChannel.lock()`) behind a per-file thread lock, shared by the desktop app and the CLI, so a write from one doesn't lose the other's.

## User preferences (`settings.json`)

`JsonSettingsFile` is the `~/.shogunai/settings.json` file, shared by `JsonThemePreferenceRepository` (`ThemePreferenceRepository` port), `JsonLanguagePreferenceRepository` (`LanguagePreferenceRepository` port) and `JsonShellPreferenceRepository` (`ShellPreferenceRepository` port). `JsonToolPreferenceRepository` (the terminal and default IDE) and `JsonUpdatePreferenceRepository` (the automatic check and the skipped version) share it too. Besides the theme and language it stores the shell's state (`lastDestination`, `windowBounds`, `sidebarCollapsed`, `collapsedProjects`, `lastCreateProject`, `recentPaletteItems`, `grouping`), the tools (`terminal`, `editor`), the tray (`recentProjects`, `recentWorktrees`, `showTrayIcon`, `keepRunningInTray`), the update check (`checkForUpdates`, `skippedVersion`) and each worktree's chosen agent (`worktreeAgents`). Every field has a default, so a file written by an older version loads as is.

- **Each save only rewrites its own field**: `update` reads the current contents, applies the change and writes the whole file atomically (same pattern as `JsonProjectRepository`). Changing the language doesn't overwrite the theme and vice versa.
- **Defaults**: `SYSTEM` for the theme and `ENGLISH` for the language when the file is missing or empty. Files written by older versions (only `themeMode`) are read fine: the language takes its default.
- **Unreadable file and concurrent writes**: as with the catalog, an unparseable file reads as defaults but is reported in `lastReadProblem` (the desktop app shows it once), moved aside as `settings.json.unreadable-<timestamp>` before the next write, and every read and update runs under `LocalFileLock`.

## Machine files

Every file ShogunAi stores on the machine, and what each is for, is listed in [Configuration › Machine files](../guide/configuration.md#machine-files-shogunai): the catalog and preferences above, the port reservation index and lock (`NioEnvironmentStateStore`), the approved post-creation commands (`JsonRepositoryTrust`), the emulator logs (`LocalAndroidSdk`) and the Xcode compilation cache settings.

## Test doubles

In `core/src/testFixtures/kotlin/app/luxion/shogunai/domain/`, `core`'s test fixtures, used by the tests of `core`, `cli` and `desktopApp` (`testImplementation(testFixtures(project(":core")))`):

- `FakeShellCommandExecutor` — substitutes `ShellCommandExecutor`: answers each argv with a canned `CommandResult` and records the commands run.
- `FakeFileManager` — substitutes `FileManager` with an in-memory set of existing paths and properties files.
- `FakeEnvironmentStateStore` / `FakePortProbe` — substitute the reservation store (in-memory reservations per administrative directory, counting writes and locks) and the port probe (a set of busy ports).
- `FakeDesktopTurnStore` — keeps the desktop turn in memory.
- `FakeDeviceWaitQueue` / `FakeProcessLiveness` — substitute the waiting queue (in memory per queue) and the process check (a set of dead processes).
- `FakeAppleSimulators` / `FakeKmpTestWiring` — substitute the simulators (runtimes, devices that boot after a number of polls, records of creations, boots, windows shown (or a failure to show them), shutdowns and deletions, a macOS switch) and the KMP wiring.
- `FakeHerdrConfigSource` — returns a fixed Herdr worktrees folder.
- `FakeHerdrClient` — records opened worktrees, split and closed panes, closed workspaces, commands run and focused agents, reports the worktrees it's given (or opens again) as already open or as open workspaces and the agents it's given, lists the panes it's given plus the ones it creates, marks a pane's agent live when a command runs in it (right away, after some listings, never with `startsAgents = false`, or gone again with `quitAgent`), and can fail opening, running, listing worktrees or agents, or closing a given workspace with a given error.
- `FakeAndroidSdk` — substitutes `AndroidSdk` with AVDs, emulator states that move from booting to running after a number of polls, a missing-SDK switch, and records of starts and stops.
- `FakeGradleDaemonSource` — substitutes `GradleDaemonSource` with a mutable list of daemons (or a failure) and counts the listings; `gradleDaemon(...)` and `kotlinDaemon(...)` build them.
- `FakeDerivedDataSource` — substitutes `DerivedDataSource` with folders, sizes by path (recording which folders were measured), existing projects, unmounted volumes, a compilation cache size, a macOS switch and failing deletions, and records the deletions; `derivedData(...)` builds a folder.
- `FakeClipboardWriter` — substitutes `ClipboardWriter`.
- `FakeProjectRepository` — substitutes `ProjectRepository` in ViewModel tests.
- `FakeRepositoryProvisioningSource` — substitutes `RepositoryProvisioningSource` with provisioning per repository path, unreadable repositories and failing writes, and records the patterns added.
- `FakeRepositoryTrust` / `FakeRepositoryApproval` — an in-memory trust store (optionally approving everything, for tests about something else) and an approval that always answers the decision it's given.
- `FakeTerminalLauncher`, `FakeEditorLauncher`, `FakeFolderLauncher`, `FakeToolDetector`, `FakeToolPreferenceRepository` — record what was opened, can fail, and report fixed detected tools and preferences.
- `FakeReleaseSource` / `FakeUpdatePreferenceRepository` — a fixed list of published releases (or failure) and in-memory update preferences.
- `FakeShellPreferenceRepository` — in-memory shell state for the desktop app's tests.

Thanks to the domain depending on interfaces, the use case tests (`*UseCaseTest`) and the ViewModel tests don't touch disk or launch real processes. The infrastructure itself is covered by `ProcessBuilderShellCommandExecutorTest`, `JsonProjectRepositoryTest`, `JsonSettingsFileTest`, `RepositoryProvisioningFilesTest`, `NioEnvironmentStateStoreTest` (temporary directories and a real listening socket), `NioDeviceWaitQueueTest` (a temporary directory and real processes), `NioDesktopTurnStoreTest`, `LocalAndroidSdkTest` (SDK lookup order, real `adb devices` output, the built start command), `SimctlAppleSimulatorsTest` (real `simctl` JSON), `FileKmpTestWiringTest` (temporary repositories and Gradle user home), `ProcessGradleDaemonSourceTest` (on real `ps` lines and daemon log excerpts), `NioFileManagerTest`, `MacDerivedDataSourceTest`, `HerdrConfigFileTest`, `CliHerdrClientTest`, `SharedDeviceStatesAndroidSdkTest`, `JsonRepositoryTrustTest`, `JsonMultirepoSourceTest`, `GitHubReleaseSourceTest` (a local HTTP server), `TerminalLaunchersTest`, `EditorLaunchersTest`, `LocalToolDetectorTest` and `TmuxIntegrationTest` (a real tmux server, skipped without tmux), which do use real processes and temporary files.
