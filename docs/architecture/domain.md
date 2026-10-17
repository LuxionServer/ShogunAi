# Domain layer

Lives in `core/src/main/kotlin/app/luxion/shogunai/domain`. Doesn't depend on any concrete infrastructure implementation.

## Models

### `ProjectConfig`

Configuration of a repository that worktrees are created against. It's what makes the flow reusable across projects, instead of hardcoding paths or file names.

| Field | Description |
|---|---|
| `baseRepositoryPath` | Absolute path of the main repository, from which Git is run. |
| `worktreesRoot` | Parent folder where worktrees are created. |
| `secretFiles` | "Local files": exact paths, relative to the base repository or absolute, copied into each new worktree on this machine only (e.g. `local.properties`). What the repository itself declares lives in `.shogunai.json` (see `RepositoryProvisioning`). |
| `agentLaunchConfig` | Agent command included in the launch command copied for a worktree (`AgentLaunchConfig`, see below). |
| `simulator` | `SimulatorSettings` for this machine: the iOS simulator's device type and runtime (`null` means automatic). Only used when the repository declares the iOS simulator provider; never stored in the repository. |
| `emulator` | `EmulatorSettings` for this machine: the AVD to use (`null` means automatic) and `noWindow`. Only used when the repository declares the emulator provider; never stored in the repository. |
| `mainBranch` | Optional branch that worktrees are compared against to show their status (e.g. `develop`). `null` means it's detected from the repository (see `ResolveMainBranchUseCase`). |

Path helpers:

- `worktreePathFor(taskId)` — `<worktreesRoot>/<taskId>`.
- `worktreePathForBranch(branch)` — directory for an existing branch: strips a known `BranchType` prefix (`feature/`, `fix/`) and replaces the remaining `/` with `-`, so `feature/TASK-123` lands in the same folder as a new-branch worktree for `TASK-123`.
- `baseSecretPath(fileName)` — the secret file inside the base repository.

`Project` pairs a `ProjectConfig` with an `id` and a display `name`; it's what the project catalog stores.

### `AgentLaunchConfig`

```kotlin
@Serializable
data class AgentLaunchConfig(
    val agentCommand: String = "claude",
    val useHeadroom: Boolean = true,
) {
    fun resolvedCommand(): String = if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
```

A flat `data class` with default values, so it serializes with `kotlinx.serialization` without a polymorphic `SerializersModule`, and projects saved before a field existed still load.

### `BranchType`

Enum `FEATURE` / `FIX`, each with a `prefix` (`"feature"` / `"fix"`) prepended to the task ID when naming the branch: `feature/TASK-123`.

### `Worktree` / `BranchOption`

- `Worktree` — an active worktree as reported by `git worktree list`: `path`, `branch` (`null` if `detached`), `head`, `isMain`, `isBare`, `isLocked` (a `locked` line, e.g. a worktree an agent session is using) and `isPrunable` (a `prunable` line: its folder no longer exists).
- `BranchOption` — a local branch offered when creating a worktree from an existing branch: `name` and `isCheckedOut` (already checked out in the base repository or another worktree).

### `WorktreeStatus` / `MainBranch` / `Divergence`

- `MainBranch` — the branch worktrees are compared against: its `name` (`develop`) and the `ref` used in Git commands (`origin/develop`, or `develop` when there's no remote-tracking branch).
- `Divergence` — commits `ahead` and `behind` with respect to another branch.
- `WorktreeStatus` — the state of one worktree: `uncommittedChanges`, `upstream` and `mainBranch` divergences (`null` when they don't apply) and `lastCommitAt`. `isMerged` is derived: the branch has nothing the main branch lacks (`ahead == 0`) and the main branch has moved past it (`behind > 0`), so a freshly created branch isn't reported as merged.

### `RepositoryProvisioning`

What the repository declares that each new worktree needs, read from files at the root of the base repository and versioned with it, so it's shared by everyone who clones it and doesn't depend on the machine:

| Field | Source |
|---|---|
| `copyPatterns` | The `copy` list of `.shogunai.json`. |
| `worktreeIncludePatterns` | The lines of `.worktreeinclude` (Claude Code's file), skipping blank lines and `#` comments. |
| `providers` | The `providers` object of `.shogunai.json`: the providers the repository turns on, each keyed by its id with an object of options. `gradle` (`GradleProvider`, optional `daemonMaxHeap`, `idleTimeoutMinutes` and `kotlinDaemonMaxHeap`) makes the [Gradle daemons](#gradle-daemons) visible for the repository and, with settings, bounds the daemons of worktree builds; `emulator` (`EmulatorProvider`, optional `avd` the repository prefers) gives each worktree its own [Android emulator](#android-emulator); `ios-simulator` (`IosSimulatorProvider`, optional `device` and `runtime` preferences) gives each worktree its own [iOS simulator](#ios-simulator); `xcode` (`XcodeProvider`, optional `compilationCache`) shows and cleans up the [Xcode DerivedData](#xcode-deriveddata) and can turn on compilation caching. Unknown ids are ignored. |
| `environment` | The `env` object and `envFile` of `.shogunai.json`, as an `EnvironmentDeclaration` (see [Worktree environment](#worktree-environment)). |

`allPatterns` is their union without duplicates. Patterns use Git's ignore-pattern syntax, relative to the repository root: `local.properties` matches at any depth, `/local.properties` only at the root, `.idea/runConfigurations/` a whole folder, `.env*` a glob.

```json
{ "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"], "providers": { "gradle": {} } }
```

Unknown fields of `.shogunai.json` are ignored, so later versions can add fields without breaking older ones.

### Worktree environment

Variables each worktree gets, declared in `.shogunai.json` (`WorktreeEnvironment.kt`):

```json
{
  "env": { "PORT": "${port}", "API_URL": "http://localhost:${port:api}", "APP_SUFFIX": "-${worktree}" },
  "envFile": ".env.shogunai"
}
```

- `EnvironmentDeclaration` — the `variables` in file order and the optional `envFile`, a path relative to the worktree root that must stay inside it. `portKeys` lists the ports the declaration needs.
- `EnvironmentVariable` — a valid variable name (letters, digits and `_`, not starting with a digit) and its template, parsed when the file is read into `TemplatePart`s: `Literal`, `WorktreeName` (`${worktree}`, the worktree's folder name) and `Port(key)`. `${port}` is keyed by the variable's own name, so each variable gets its own port; `${port:<key>}` is keyed `port:<key>`, shared by every variable that uses it (`:` can't appear in a variable name, so the keys never collide). `$$` is a literal `$`; any other `${…}` throws `InvalidEnvironmentTemplate`, which reading turns into `InvalidRepositoryConfig`.
- `WorktreeEnvironment` — a resolved environment: the rendered `variables` and the `ports` reserved for the worktree.

### Android emulator

The emulator provider (`Emulator.kt`) only acts for repositories that declare `"providers": { "emulator": {} }`; for any other repository nothing looks for the SDK, runs `adb` or adds variables.

- `EmulatorSettings` — the machine settings stored in `ProjectConfig.emulator`.
- `chooseAvd(requested, settings, preferred, available)` — the AVD to start: the one requested for this start, else the machine setting, else the repository's preferred AVD if this machine has it, else the first available one; `null` when there's none.
- Each worktree reserves the console port key `emulator` among the even ports 5554–5584 (the range `adb` scans), with the next port (the emulator's adb port) also free, and gets `ANDROID_SERIAL=emulator-<port>` (`emulatorSerial`). Android's Gradle plugin reads `ANDROID_SERIAL` in `installDebug`, `uninstall*` and `connectedAndroidTest` to use only that device.
- `SdkLocation` (SDK root, `emulator` and `adb` executables), `EmulatorState` (`STOPPED`, `BOOTING`, `RUNNING`) and `WorktreeEmulator` (serial and state) describe what the [Android SDK port](#androidsdk) reports.

### iOS simulator

The iOS simulator provider (`IosSimulator.kt`) only acts on macOS for repositories that declare `"providers": { "ios-simulator": {} }`.

- `SimulatorSettings` — the machine settings stored in `ProjectConfig.simulator`.
- `SimulatorRuntime` / `SimulatorDeviceType` — what Xcode has installed, as `simctl` reports it (identifier, display name, version, platform, and the device types a runtime supports).
- `chooseSimulator(requested, settings, preferred, runtimes)` — the runtime by display name (`iOS 18.5`, case-insensitive) from the first of this operation, the machine setting and the repository's preference that is installed, else the newest iOS runtime by version; then the device type by name among the runtime's iPhone and iPad types in the same order, else its newest iPhone: the highest model number in the name, and among those the shortest name (the base model, or the Pro when there's no base), whatever order Apple lists them in. `null` without an iOS runtime.
- Each worktree keeps the resource `ios-simulator`, the UDID of a simulator created for it and named `ShogunAi · <repository folder> · <worktree folder>` (`worktreeSimulatorName`), and gets `IOS_SIMULATOR_UDID=<UDID>` in its environment. The `ShogunAi · ` prefix is how orphaned simulators are told apart from the user's own.

### Herdr

`Herdr.kt` holds what the [Herdr port](#herdrclient) reports: `HerdrWorktreeOpening` (workspace id, its root pane id and whether Herdr already had the worktree open), plus `HerdrOpening` (`OPENED`, `FOCUSED`), the outcome of opening a worktree in Herdr.

### Gradle daemons

What the Gradle provider observes on the machine (`GradleDaemon.kt`):

- `DaemonProcess` — a running daemon: `pid`, `kind` (`DaemonKind.GRADLE` or `KOTLIN`), `gradleVersion` (Gradle daemons only), `residentMemoryBytes`, `lastBuildDirectory` (the directory of the last build a Gradle daemon received) and `state`.
- `DaemonState` — `Busy`, `Idle(since)`, `Unknown` (a Gradle daemon whose state can't be read) or `Untracked` (Kotlin daemons, which don't record one).
- `GradleDaemon` — a `DaemonProcess` with its `owner` in a repository: `DaemonOwner.Worktree(path)`, `RemovedWorktree` (its last build directory no longer exists) or `OtherProject`; `null` for Kotlin daemons, daemons without a known build directory, or outside a repository.
- `DaemonStopReport` — the outcome of a stop: `stopped`, `kept` (each with a `DaemonKeptReason`: `BUSY`, `STATE_UNKNOWN` or `BUILD_RUNNING`), `notDaemons` (requested pids that aren't daemons) and `failures`.

### Xcode DerivedData

What the Xcode provider finds in Xcode's DerivedData location (`DerivedData.kt`), only on macOS for repositories that declare `"providers": { "xcode": {} }`:

- `DerivedDataFolder` — one project's folder: `path`, `workspacePath` (the `.xcodeproj`, `.xcworkspace` or package recorded in its `info.plist`), `sizeBytes` and `lastAccessed`.
- `DerivedDataOwner` — `Worktree(path)` (the worktree of the repository containing the workspace, nested worktrees resolved), `OtherProject` (the workspace exists elsewhere), `Orphaned` (the workspace no longer exists) or `UnavailableVolume` (the workspace is on a volume that isn't mounted, so it can't be told apart from a live project).
- `DerivedDataListing` — every folder with its owner (`AttributedDerivedData`) and the size of the shared compilation cache when asked for; `orphans` and `ofWorktrees()` filter it.
- `DerivedDataDeletion` — the outcome of deleting orphans: `deleted`, `failures` (`DerivedDataDeletionFailure`: path and detail) and `bytesFreed`.

### `ThemeMode` / `AppLanguage`

User preferences: `LIGHT` / `DARK` / `SYSTEM` and `ENGLISH` / `SPANISH`. They're stored in `settings.json` (see [Infrastructure layer](infrastructure.md)).

### `WorktreeError`

`sealed class` of the flow's expected errors, so the UI decides what to show (and translates it) from structured data instead of parsing strings:

- `BaseRepositoryNotFound` — the configured base repo doesn't exist on disk.
- `WorktreeAlreadyExists` — there's already a directory at the destination path.
- `InvalidTaskId` — the task id isn't a valid Git ref segment once normalized.
- `SecretFileNotFound` — local files are missing in the base repo.
- `RepositoryNotFound` — the directory isn't inside a Git repository with a working tree (used by the CLI).
- `NotAWorktree` — the path given to provision is the main worktree or isn't one of the repository's worktrees.
- `InvalidRepositoryConfig` — `.shogunai.json` exists but isn't valid JSON, isn't an object, its `copy` isn't a list of strings, its `providers` (or a known provider in it) isn't an object, or its `env` or `envFile` is invalid; keeps the file path and the detail.
- `NoFreePort` — every port from 20000 to 29999 is reserved or busy.
- `EnvironmentFileTracked` — the declared `envFile` is a file Git tracks in the worktree, so it isn't overwritten.
- `GitCommandFailed` — a Git command returned a non-zero exit code; keeps the command, exit code and Git's error output.
- `SecretCopyFailed` — copying local or repository files failed after the worktree had already been created.
- `ClipboardWriteFailed` — the launch command couldn't be copied to the clipboard.
- `GitLfsNotFound` — the repository uses Git LFS but `git-lfs` isn't installed.
- `BranchNotFound` — the requested existing branch doesn't exist locally.
- `BranchAlreadyCheckedOut` — the branch is already checked out in another worktree or in the base repository.
- `HerdrNotInstalled` — the `herdr` command wasn't found.
- `HerdrCommandFailed` — a `herdr` command failed; keeps the command and Herdr's message (e.g. no Herdr server running).

## Ports

Use cases depend on interfaces, not implementations. This is what allows testing them with doubles.

### `ShellCommandExecutor`

```kotlin
interface ShellCommandExecutor {
    suspend fun execute(command: List<String>, workingDirectory: String? = null): CommandResult
}
```

The command is passed as **argv** (a list of strings), never as a string for a shell to interpret. Reason: to avoid differences between macOS's default shell (zsh) and Linux's (bash), and to avoid command escaping/injection issues.

`CommandResult` holds `command`, `exitCode`, `stdout`, `stderr` without interpreting their meaning — each use case decides what counts as success via `isSuccess` (`exitCode == 0`). A process that can't even start (e.g. the executable isn't in `PATH`) throws `CommandExecutionException` instead.

### `FileManager`

```kotlin
interface FileManager {
    fun exists(path: String): Boolean
    fun copy(source: String, destination: String)
    fun directorySize(path: String, excluding: Set<String> = emptySet()): Long
    fun isDirectory(path: String): Boolean
    fun modificationStamps(path: String): List<Pair<String, Long>>
    fun readProperties(path: String): Map<String, String>
    fun writeText(path: String, text: String)
    fun readText(path: String): String?
}
```

Abstracts the filesystem so that copying secrets and measuring worktrees is unit-testable without touching disk. `copy` creates the destination's missing parent folders. `directorySize` adds up the regular files under `path`, skipping the folders in `excluding`. `modificationStamps` returns every file and folder under `path` (or `path` itself if it's a file) with its last-modified time, and nothing if it doesn't exist. `readProperties` reads a Java properties file (such as `gradle.properties`), empty if it's missing or unreadable. `writeText` replaces a file atomically, creating its folders; `readText` returns its content, or `null` if it's missing or unreadable.

### `ClipboardWriter`

```kotlin
interface ClipboardWriter {
    fun write(text: String): Result<Unit>
}
```

Writes the launch command of a worktree to the system clipboard.

### `RepositoryProvisioningSource`

```kotlin
interface RepositoryProvisioningSource {
    fun read(repositoryPath: String): RepositoryProvisioning
    fun addCopyPatterns(repositoryPath: String, patterns: List<String>)
}
```

Reads the repository's provisioning (throwing `InvalidRepositoryConfig` for an unreadable `.shogunai.json`) and appends patterns to `copy`, which is how the configuration screen moves local files into the repository.

### `EnvironmentStateStore` / `PortProbe`

```kotlin
interface EnvironmentStateStore {
    fun readPorts(adminDirectory: String): Map<String, Int>
    fun writePorts(adminDirectory: String, ports: Map<String, Int>)
    fun readResources(adminDirectory: String): Map<String, String>
    fun writeResources(adminDirectory: String, resources: Map<String, String>)
    fun reservedPortsOnMachine(commonDirectory: String): Set<Int>
    fun recordedResourcesOnMachine(commonDirectory: String, key: String): Set<String>
    fun <T> withLock(block: () -> T): T
}

interface PortProbe {
    fun isFree(port: Int): Boolean
}
```

`EnvironmentStateStore` keeps each worktree's reserved ports and provider resources (such as a simulator's UDID) in Git's administrative directory for that worktree (`.git/worktrees/<id>/` for a linked one, `.git/` for the main one), lists every reservation on the machine (this repository's worktrees plus every worktree of any repository ShogunAi has stored reservations for), and runs a block while holding the machine's reservation lock. `PortProbe` tells whether a port is free on the machine.

### `AndroidSdk`

```kotlin
interface AndroidSdk {
    fun locate(repositoryPath: String): SdkLocation
    suspend fun listAvds(sdk: SdkLocation): List<String>
    suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState>
    fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean)
    suspend fun stopEmulator(sdk: SdkLocation, serial: String)
}
```

Everything the emulator provider needs from the Android SDK, used only for repositories that declare the provider: finding the SDK (`AndroidSdkNotFound` with the places checked), listing AVDs, the state of every running emulator in one call, starting an emulator detached on a console port, and stopping one. Tool errors are `AndroidToolFailed`.

### `AppleSimulators` / `KmpTestWiring`

```kotlin
interface AppleSimulators {
    fun isSupported(): Boolean
    suspend fun runtimes(): List<SimulatorRuntime>
    suspend fun devices(): List<SimulatorDevice>
    suspend fun create(name: String, deviceTypeIdentifier: String, runtimeIdentifier: String): String
    suspend fun boot(udid: String)
    suspend fun show(udid: String)
    suspend fun shutdown(udid: String)
    suspend fun delete(udid: String)
}

interface KmpTestWiring {
    fun isWiredInRepository(repositoryPath: String): Boolean
    fun usesKotlinMultiplatform(repositoryPath: String): Boolean
    fun isInitScriptInstalled(): Boolean
    fun initScriptPath(): String
    fun installInitScript()
    fun uninstallInitScript()
}
```

`AppleSimulators` is everything the iOS simulator provider needs from Xcode's simulators, used only on macOS for repositories that declare the provider: installed runtimes (with the device types each supports), existing devices with their state, and creating, booting, showing in Xcode's simulator viewer (Simulator or Device Hub), shutting down and deleting one. Tool errors are `AppleToolFailed`. `KmpTestWiring` tells whether the repository's Gradle build scripts apply the Kotlin Multiplatform plugin, whether they pass `IOS_SIMULATOR_UDID` to KMP's simulator tests, and installs or removes the machine-wide Gradle init script that does it for every project.

### `HerdrConfigSource`

```kotlin
interface HerdrConfigSource {
    fun worktreesRoot(): String
}
```

Where [Herdr](https://herdr.dev) creates its worktrees, so they can be told apart from other locations. It never fails: without a readable configuration it returns Herdr's default folder.

### `HerdrClient`

```kotlin
interface HerdrClient {
    suspend fun openWorktree(repository: String, path: String, label: String): HerdrWorktreeOpening
    suspend fun splitPane(paneId: String, cwd: String, environment: List<Pair<String, String>>): String
    suspend fun closePane(paneId: String)
    suspend fun runInPane(paneId: String, command: String)
    suspend fun openWorktreeWorkspaces(repository: String): Map<String, String>
    suspend fun closeWorkspace(workspaceId: String)
}
```

The few [Herdr](https://herdr.dev) operations ShogunAi uses to open a worktree there and to close it again: `openWorktreeWorkspaces` returns, for the worktrees of a repository that Herdr has open, their path and workspace id; `closeWorkspace` closes a workspace and the processes in its panes. The rest: open an existing worktree as a focused workspace grouped under its repository (or focus it if it's already open), split a pane into a focused one whose shell starts in a directory with extra variables, close a pane, and submit a command in a pane. Pane ids aren't stored, since Herdr renumbers them when panes move. Errors are `HerdrNotInstalled` and `HerdrCommandFailed`.

### `GradleDaemonSource`

```kotlin
interface GradleDaemonSource {
    suspend fun list(): List<DaemonProcess>
}
```

Lists the Gradle and Kotlin daemons running on the machine with their state, or fails with `ProcessListingFailed` when processes can't be listed.

### `DerivedDataSource`

```kotlin
interface DerivedDataSource {
    fun isSupported(): Boolean
    suspend fun folders(): List<DerivedDataFolder>
    suspend fun compilationCacheBytes(): Long?
    fun projectExists(workspacePath: String): Boolean
    fun isOnUnavailableVolume(workspacePath: String): Boolean
    suspend fun delete(folder: DerivedDataFolder)
}
```

Everything the Xcode provider needs from Xcode's DerivedData: whether the host is macOS, the project folders in the current location (never Xcode's shared caches, which have no `info.plist`), the size of the shared compilation cache (`null` when it doesn't exist), whether a workspace still exists or sits on an unmounted volume, and deleting one folder.

### Repositories

- `ProjectRepository` — `list()`, `save(project)`, `delete(id)` over the project catalog.
- `ThemePreferenceRepository` / `LanguagePreferenceRepository` — `load()` / `save(...)` of each user preference.

## Use cases

The worktree use cases are built per project (they receive its `ProjectConfig`) and return `Result<T>` (using `runCatching`), with typed errors as `WorktreeError`. Every Git command runs from the base repository.

### `CreateWorktreeUseCase`

Creates a worktree on a **new** branch `<type>/<taskId>`. The task id is normalized first (`normalizeTaskId`: trimmed, whitespace runs replaced with `-`) and validated as a Git ref segment. Then, in this order:

1. **Upfront validations** (base repo exists, destination free, local files present) and **resolving the repository's files** — done *before* touching Git so as not to leave a half-done worktree.
2. `git worktree add <path> -b <branch>`.
3. Copying the local files and then the repository's files into the new directory, at the same relative paths.
4. Preparing its environment through the `WorktreeEnvironmentPreparer` it receives: `PrepareWorktreeEnvironmentUseCase` resolves it with reservation (`ResolveWorktreeEnvironmentUseCase`) and, when `envFile` is declared, `WriteEnvironmentFileUseCase` writes it in dotenv format, refusing a file Git tracks (`git ls-files --error-unmatch`, `EnvironmentFileTracked`) and reporting write errors as `EnvironmentFileWriteFailed`. The preparer defaults to `WorktreeEnvironmentPreparer.NONE`, which tests that don't exercise the environment rely on; the app and the CLI always pass the real one.

Resolving the repository's files reads `RepositoryProvisioning` and, if it declares any pattern, runs `git ls-files --others --ignored -z -x <pattern>…`: every file not tracked by Git that matches a pattern (tracked files already come with the checkout). Without patterns no Git command runs. An unreadable `.shogunai.json` (`InvalidRepositoryConfig`) or a failing `git ls-files` (`GitCommandFailed`) stops creation before anything is created. Local files are checked strictly (`SecretFileNotFound`), patterns leniently: a pattern with no match copies nothing.

If copying or preparing the environment fails **after** the worktree was created, it's rolled back with `git worktree remove --force` (best-effort) before propagating the error. If `git worktree add` fails because `git-lfs` is missing, the half-created worktree and its new branch are removed and `GitLfsNotFound` is returned.

### `CreateWorktreeFromBranchUseCase`

Same flow for a branch that **already exists** locally: checks the branch with `git branch --list`, runs `git worktree add <path> <branch>` (without `-b`) in the directory given by `worktreePathForBranch`, and copies the local and repository files the same way. A branch already checked out elsewhere fails with `BranchAlreadyCheckedOut`.

### `ListLocalBranchesUseCase`

Lists local branches (`git for-each-ref --format=%(refname:short) refs/heads`) and flags those already checked out, using `git worktree list --porcelain`. The UI shows the flagged ones disabled.

### `ListWorktreesUseCase`

Runs `git worktree list --porcelain` (a stable format meant to be parsed, unlike the default human-readable output) and parses the result with `parseWorktreeList`. Marks `isMain = true` on the worktree whose path matches `config.baseRepositoryPath`.

### `ResolveMainBranchUseCase`

Resolves the `MainBranch` once per list load, from the base repository and without network access: `config.mainBranch` if set, otherwise the `origin` remote's default branch (`git symbolic-ref refs/remotes/origin/HEAD`), falling back to a local `main` and then `master`. A named branch is compared through `origin/<name>` when that ref exists (`git rev-parse --verify`), because merges usually reach the remote first, and through the local branch otherwise. Returns `null` when nothing can be resolved.

### `ReadWorktreeStatusUseCase`

Reads a `WorktreeStatus`, running each command in the worktree's own folder:

- `git --no-optional-locks status --porcelain` — uncommitted changes. Optional locks are disabled so `git status` never rewrites the index; otherwise reading statuses would change the metadata that auto refresh watches. It's the only required command: if it fails (e.g. the folder is gone), the whole status fails.
- `git rev-list --left-right --count @{upstream}...HEAD` — upstream divergence, skipped on a detached HEAD and `null` if there's no upstream.
- `git rev-list --left-right --count <main ref>...HEAD` — main branch divergence, skipped when there's no main branch or the worktree is on it.
- `git log -1 --format=%ct` — last commit date, `null` if there are no commits.

Squash and rebase merges aren't detected as merged, and remote-tracking branches are only as fresh as the user's last fetch.

### `CleanUpWorktreesUseCase`

`selectCleanupCandidates(worktrees, statuses)` picks the worktrees to clean up: not main, not locked, and either prunable or merged into the main branch with no uncommitted changes. Worktrees without a loaded status are never picked.

`CleanUpWorktreesUseCase` takes those candidates, the `MainBranch` and whether to delete branches, and returns a `CleanupReport` (`removed`, `keptBranches`, `failures`, `herdrFailures`). Each existing worktree is removed with `git worktree remove` (never `--force`), and missing ones with a single `git worktree prune`. A failing step is recorded and the rest continue. Then the branches of every removed worktree, pruned ones included, are deleted only if `git merge-base --is-ancestor <branch> <main ref>` still succeeds, and then with `git branch -D`: `-d` checks against the base repository's `HEAD`, which in a git-flow repository may not contain a branch merged into `develop`.

### Worktree overview

- `classifyOrigin(worktree, config, herdrWorktreesRoot)` returns the `WorktreeOrigin` of a non-main worktree: `CLAUDE_CODE` if its path is inside a `.claude/worktrees/` folder (checked first, since that folder can be inside the worktrees root), `HERDR` if it's inside Herdr's worktrees folder (when one is given, read through [`HerdrConfigSource`](#herdrconfigsource)), `SHOGUNAI` if it's inside `config.worktreesRoot`, `OTHER` otherwise. Paths are compared with `/` separators and without trailing slashes, so Windows paths work too.
- `isInactive(worktree, status, agents, now)` is true for a non-main worktree with no uncommitted changes, no running agents and a last commit older than `INACTIVITY_THRESHOLD` (14 days).
- `DetectRunningAgentsUseCase` returns the `RunningAgent`s per worktree path. It runs `lsof -d cwd -F pcn` once to get every process's working directory, assigns each process to the worktree with the longest path containing it (so a process in `.claude/worktrees/<name>` isn't attributed to the main worktree), and runs `ps -o pid=,comm=` for those processes. Their executable's file name is matched against the known agent CLIs (`claude`, `codex`, `gemini`, `cursor-agent`, `opencode`, `aider`, `copilot`) and the project's own agent command. The `lsof` command name isn't used because Claude Code renames its process to its version. If `lsof` or `ps` fail (e.g. on Windows), it fails with `WorktreeError.ProcessListingFailed`.
- `MeasureWorktreeSizeUseCase` returns a worktree's size with `FileManager.directorySize`, on the IO dispatcher, excluding every other listed worktree nested inside it. It returns `null` for a missing folder.
- `ReadGitMetadataFingerprintUseCase` returns a fingerprint of the main repository's Git metadata: the sorted (path relative to `.git`, last-modified time) pairs of `HEAD`, `index`, `packed-refs`, `FETCH_HEAD` and everything under `refs` and `worktrees`, read on the IO dispatcher. Commits, checkouts, locks, fetches and worktrees added or removed by any tool change it; `objects` isn't included. It's empty when `<baseRepositoryPath>/.git` isn't a directory.

### `RemoveWorktreeUseCase`

Runs `git worktree remove [--force] <path>` and, optionally, `git branch -d <branch>` to delete the local branch (safe delete, not `-D`). It returns a `WorktreeRemoval` whose `herdrFailure` is the error from closing the worktree's Herdr workspace, when that failed (see [`CloseHerdrWorkspacesUseCase`](#closeherdrworkspacesusecase)).

!!! note
    Git doesn't allow removing a worktree from inside itself, which is another reason every command runs from the base repository.

### `CloseHerdrWorkspacesUseCase`

Closes the Herdr workspace of removed worktrees, in two steps around the removal. `openWorkspaces(paths)` makes one `worktree list` call for the base repository, before anything is removed (afterwards Git no longer lists the worktree), and returns the workspace id of each given path that Herdr has open, matching paths with `normalizePath`. Any failure (Herdr not installed or not running, an unexpected response) gives an empty map: if ShogunAi can't see Herdr there's nothing to close and nothing to report. `close(workspaces, removedPaths)` closes only the workspaces of the paths that were actually removed (pruned missing worktrees included) and returns a `CleanupFailure` for each close that failed instead of throwing. `RemoveWorktreeUseCase` and `CleanUpWorktreesUseCase` take it as an optional parameter and `null` leaves their behaviour unchanged; a Git failure closes nothing.

### `CopyWorktreeLaunchCommandUseCase`

Copies `cd "<worktree path>" && <agent command>` to the clipboard, with the agent command from `AgentLaunchConfig.resolvedCommand()`, so the user pastes it in the terminal of their choice.

### `OpenWorktreeInHerdrUseCase`

Opens a worktree in Herdr with its environment and the project's agent. It asks Herdr to open the worktree for the project's base repository, labelled with its folder name; if Herdr already had it open, that workspace is now focused and it returns `FOCUSED` without touching its panes. Otherwise it resolves the worktree's environment (the same as `shogun env`, provider variables such as `ANDROID_SERIAL` included, reserving ports if needed); when it isn't empty, it splits the root pane into one whose shell starts in the worktree with those variables and closes the root pane, so the workspace keeps a single pane. Then it runs `AgentLaunchConfig.resolvedCommand()` in that pane and returns `OPENED`.

### `ResolveRepositoryUseCase`

Finds the base repository (main worktree) that contains a directory, for front ends that start from a directory instead of a project, such as the CLI. It runs `git rev-parse --path-format=absolute --git-common-dir` there: the common `.git` folder is shared by every worktree, so its parent is the main worktree whether the directory is the main worktree, a subfolder or a linked worktree. Anything else (not a repository, a bare repository) is `RepositoryNotFound`.

### `DeleteMergedBranchUseCase`

Deletes a local branch with `git branch -d`, which Git refuses when the branch isn't merged, and returns whether it was deleted instead of failing. Used by the CLI's `WorktreeRemove` hook, where a kept branch is expected rather than an error.

### `ProvisionWorktreeUseCase`

Applies the same files as creation (local files and the repository's provisioning, see `CreateWorktreeUseCase`) to an **existing** linked worktree, e.g. one created by another tool. The path must be one of the repository's worktrees (from `ListWorktreesUseCase`) and not the main one (`NotAWorktree`). Files are copied over existing ones, the environment is prepared the same way (reserving ports for a worktree another tool created), and it returns the copied relative paths. Unlike creation, a failed copy or environment doesn't remove anything: the worktree isn't ShogunAi's to delete. Creation and provisioning share the same copy function.

### `RepairWorktreesUseCase`

Re-links the base repository and its worktrees after they were moved on disk. Git stores absolute paths in both directions (`<worktree>/.git` points to the base repository and `.git/worktrees/<name>/gitdir` points back), so a move breaks them. It runs `git worktree repair` from the (new) base repository; worktrees that no longer exist at their old path but do exist at the same relative location under the new `worktreesRoot` are passed as arguments, so Git fixes both directions. The UI runs it after saving a project whose base repository path or worktrees root changed.

### `ResolveWorktreeEnvironmentUseCase`

Resolves a worktree's environment from the base repository's declaration and the providers it declares. Each needed port is a `PortRequest` (a key, candidate ports, ports that must also be free, and the error when none is left): declared `${port}` keys ask for 20000–29999 (`declaredPortRequests`); providers add theirs through `EnvironmentContribution`s, given by the injected `ProviderContributions` factory (`DEFAULT` knows the emulator; the app and the CLI pass one that also builds the iOS simulator's, which needs the `AppleSimulators` port and the project's machine settings), together with the variables they derive from their ports. With nothing declared and no contributing provider it returns an empty `WorktreeEnvironment` without running any command. Otherwise it finds the worktree's administrative and common Git directories (`git rev-parse --path-format=absolute --git-dir --git-common-dir` in the worktree) and, while holding the machine's lock:

1. keeps the worktree's stored ports whose keys are still requested, releasing the rest;
2. reserves each missing key at the first candidate that, together with its extra ports, no worktree on the machine has reserved and `PortProbe` reports free (otherwise the request's error, e.g. `NoFreePort`);
3. writes the reservations only if they changed.

Contributions can also ask for resources (`ResourceRequest`: a key, and how to check, create and discard one). With reservation, a stored resource that still `exists` is kept; otherwise one is created outside the lock (creating a simulator runs `xcrun` for about a second) and recorded under it, and if another process recorded one in the meantime, the new one is discarded and the recorded one used. Without reservation resources are only read. Provider variables come first and declared `env` variables override a provider variable with the same name. A reserved port is never checked again, because the worktree's own server is expected to listen on it. With `reserve = false` (used to show ports without side effects) it only reads the stored ports, without locking or writing, and returns no variables until every port is reserved. `formatEnvironment` renders the variables as `sh` (`export NAME='value'`, single quotes with `'\''` escaping, safe for `eval`), `dotenv` (`NAME=value`, double-quoted and escaped unless the value is plain) or `json`.

### iOS simulator use cases

The provider's contribution comes from `MachineProviderContributions`, which the app and the CLI inject into `ResolveWorktreeEnvironmentUseCase`: the emulator's, plus, on macOS for repositories that declare `ios-simulator`, a resource request whose `create` picks the device type and runtime with `chooseSimulator` (this operation, the project's `SimulatorSettings`, the repository's preferences; `NoIosRuntime` if none) and runs `simctl create` with `worktreeSimulatorName`, whose `exists` checks the device list, and whose `discard` deletes it. `requesting(settings)` returns the same factory with this operation's device type and runtime.

- `StartWorktreeSimulatorUseCase(config, worktreePath, requested)` resolves the environment through a creating resolver (so the simulator exists), boots it unless it's already booted, and polls `devices()` every second until it's booted; after 120 s it fails with `SimulatorBootTimeout` and leaves it booting.
- `StopWorktreeSimulatorUseCase` reads the recorded UDID without creating and shuts the simulator down if it's running; with none recorded there's nothing to stop.
- `ReadSimulatorStatesUseCase` maps each worktree with a recorded, existing simulator to its UDID, name and state, from one `devices()` call.
- `DeleteSimulatorsUseCase` deletes the given simulators that still exist (shutting them down first), and `orphans(config)` lists the simulators named with the `ShogunAi · ` prefix whose UDID no worktree on the machine has recorded (`recordedOnMachine`). Simulators without the prefix are never touched.

All of them fail with `IosSimulatorProviderNotDeclared` or `IosSimulatorsNotSupported` (or return nothing, for the states and orphans) when the repository doesn't declare the provider or the host isn't macOS, before running `xcrun`.

### Emulator use cases

All three fail with `EmulatorProviderNotDeclared` (or return nothing, for the states) when the repository doesn't declare the emulator provider, before touching the SDK.

- `StartWorktreeEmulatorUseCase(config, worktreePath, avd?, noWindow?)` resolves the worktree's environment (reserving its emulator port if needed), returns at once if its serial is already running, otherwise starts the AVD from `chooseAvd` (`NoAvdAvailable` if none) with the window setting from the call or the project, unless it's already booting, and polls `deviceStates` every 2 s until it's running. After 180 s it fails with `EmulatorBootTimeout` and leaves the emulator booting. The pause is injected, so tests don't wait.
- `StopWorktreeEmulatorUseCase(config, worktreePath)` reads the worktree's emulator port without reserving; with none reserved there's nothing to stop. `stopSerials` stops the given serials that are running, which the cleanup uses with serials read before removing worktrees.
- `ReadEmulatorStatesUseCase(config, worktrees)` maps each worktree with a reserved emulator port to its serial and state, from stored reservations and a single `deviceStates` call; worktrees without a reservation are left out.

### Gradle daemon use cases

Used when the repository declares the Gradle provider (and by `shogun gradle` anywhere):

- `ListGradleDaemonsUseCase` lists the daemons from `GradleDaemonSource` and, given the repository's worktrees, attributes each Gradle daemon by its last build directory: `RemovedWorktree` if that folder no longer exists, otherwise the worktree with the longest path containing it (the same rule as running agents, so nested worktrees win), otherwise `OtherProject`.
- `StopGradleDaemonsUseCase` stops the given pids, or every daemon. It lists the daemons again first, so the decision uses their current state: a Gradle daemon is stopped only if `Idle`, a Kotlin daemon only if no Gradle daemon is `Busy` or `Unknown`. Stopping runs `kill <pid>` (SIGTERM, so the JVM runs Gradle's shutdown hooks). One failure doesn't stop the rest.
- `idleDaemonsOfRemovedWorktrees` picks, from a listing taken before a cleanup, the idle Gradle daemons whose last build was in one of the removed worktrees. The cleanup passes them to `StopGradleDaemonsUseCase`, whose fresh check still keeps a daemon that became busy.
- `CheckGradleBuildCacheUseCase` tells whether `org.gradle.caching=true` is set in the repository's `gradle.properties` or in `$GRADLE_USER_HOME/gradle.properties`. With Gradle's local build cache on, a new worktree reuses task outputs built in other worktrees, because cache keys don't depend on the checkout's path.
- `gradleSettingsContribution` is the provider's environment contribution when `GradleProvider.hasSettings`: one variable, `GRADLE_OPTS`, built by `gradleOpts` from `-Dorg.gradle.jvmargs="…"`, `-Dorg.gradle.daemon.idletimeout=<ms>` and `-Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx<size>`, each only for a declared setting. System properties in `GRADLE_OPTS` override `gradle.properties`, the user's included. `gradleDaemonArguments` keeps the effective `org.gradle.jvmargs` (`effectiveGradleJvmArgs`: the user's `gradle.properties`, else the base repository's) with every `-Xmx` replaced by the declared heap, or the heap alone when there are none or they contain a double quote. The value doesn't depend on the worktree, so every worktree's daemons are compatible and reused across worktrees; a `GRADLE_OPTS` declared in `env` wins, as for any provider variable.

### Xcode use cases

Used on macOS when the repository declares the Xcode provider:

- `ListDerivedDataUseCase(worktrees, includeCompilationCache)` attributes every folder from `DerivedDataSource`: a workspace that doesn't exist is `UnavailableVolume` if its volume isn't mounted, otherwise `Orphaned`; an existing one belongs to the worktree with the longest path containing it (`owningWorktree`, the same rule as running agents and Gradle daemons), otherwise to `OtherProject`. Orphans are machine-wide: once its project is gone, a folder can't be used by anything, whichever tool created it.
- `DeleteOrphanedDerivedDataUseCase` lists the folders again, keeps those still orphaned, checks each workspace once more right before deleting it (a project restored in the meantime keeps its folder), and returns a `DerivedDataDeletion`; one failure doesn't stop the rest. A removed worktree's folder is orphaned by definition, so a cleanup only runs this use case after the removals.
- `xcodeCompilationCacheContribution` is the provider's environment contribution when `XcodeProvider.compilationCache` is set, on macOS (`MachineProviderContributions`): a resource `xcode-compilation-cache` whose `create` writes `~/.shogunai/xcode/compilation-cache.xcconfig` (`COMPILATION_CACHE_ENABLE_CACHING` and Swift and Clang prefix mapping, five `KEY = YES` lines) and whose `exists` checks the file holds that content, plus the variable `XCODE_XCCONFIG_FILE=<path>`. Being a resource, it's only written when resolving with reservation and is re-created if deleted; the file is the same for every repository on the machine. An `XCODE_XCCONFIG_FILE` declared in `env` wins.
