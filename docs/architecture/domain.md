# Domain layer

Lives in `core/src/main/kotlin/app/luxion/shogunai/domain`. Doesn't depend on any concrete infrastructure implementation.

## Models

### `ProjectConfig`

Configuration of a repository that worktrees are created against. It's what makes the flow reusable across projects, instead of hardcoding paths or file names.

| Field | Description |
|---|---|
| `baseRepositoryPath` | Absolute path of the main repository, from which Git is run. |
| `worktreesRoot` | Parent folder where worktrees are created. |
| `secretFiles` | "Local files": paths of files or folders, relative to the base repository or absolute, or patterns (an entry with `*`, `?` or `[`, see `isLocalPattern`), provisioned into each new worktree on this machine only (e.g. `local.properties`). What the repository itself declares lives in `.shogunai.json` (see `RepositoryProvisioning`). |
| `linkedSecretFiles` | The entries of `secretFiles` that are linked instead of copied; `isLinked(entry)` ignores a trailing separator. Empty in projects saved before it existed, so their entries keep being copied. |
| `agentLaunchConfig` | The project's default agent (`AgentLaunchConfig`, see below), used by every launch unless the user picks another one. |
| `additionalAgents` | The project's other agents, in order. `agents` is the default followed by these, and `agentNamed(displayName)` finds one by name, falling back to the default. Keeping the default in its own field means an older version, which ignores unknown keys, still launches it. |
| `simulator` | `SimulatorSettings` for this machine: the iOS simulator's device type and runtime (`null` means automatic). Only used when the repository declares the iOS simulator provider; never stored in the repository. |
| `emulator` | `EmulatorSettings` for this machine: the AVD to use (`null` means automatic) and `noWindow`. Only used when the repository declares the emulator provider; never stored in the repository. |
| `mainBranch` | Optional branch that worktrees are compared against to show their status (e.g. `develop`). `null` means it's detected from the repository (see `ResolveMainBranchUseCase`). |
| `editorOverride` | `EditorId` to open this project's worktrees with on this machine instead of the default IDE chosen in Preferences (`null` means the default). |
| `fetchBeforeCreate` | Whether the desktop app's "New worktree" dialog fetches `origin` before creating, as last chosen for this project. |

Path helpers:

- `worktreePathFor(taskId)` — `<worktreesRoot>/<taskId>`, with any `/` in the task id replaced by `-`, so a task id like `team/TASK-1` maps to one folder (`team-TASK-1`).
- `worktreePathForBranch(branch)` — directory for an existing branch: strips a known `BranchType` prefix (`feature/`, `fix/`) and replaces the remaining `/` with `-`, so `feature/TASK-123` lands in the same folder as a new-branch worktree for `TASK-123`.
- `baseSecretPath(fileName)` — the secret file inside the base repository.

`Project` pairs a `ProjectConfig` with an `id` and a display `name`; it's what the project catalog stores.

### `AgentLaunchConfig`

```kotlin
@Serializable
data class AgentLaunchConfig(
    val name: String? = null,
    val agentCommand: String = "claude",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val useHeadroom: Boolean = false,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: executable().replaceFirstChar { it.uppercase() }

    fun executable(): String = agentCommand.trim().substringBefore(' ').substringAfterLast('/')

    fun resolvedCommand(): String = if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
```

A flat `data class` with default values, so it serializes with `kotlinx.serialization` without a polymorphic `SerializersModule`, and projects saved before a field existed still load. `agentCommand` may carry arguments (`claude --model opus`); `executable()` is its first word without folders. An agent is identified within its project by `displayName`: its `name`, or else its executable capitalized (`codex` → "Codex").

Headroom (`headroom wrap`) is off unless the agent turns it on. `useHeadroom` is always written, and so is `ProjectConfig.agentLaunchConfig`: earlier versions, where Headroom was on by default, left the field (or the whole default agent) out when it was on, so `projects.json` reads an agent stored without `useHeadroom`, or a project without `agentLaunchConfig`, as Headroom-wrapped. The multirepo agent in `~/.shogunai/multirepos.json` is written the same way, with its `useHeadroom` always present.

`WorktreeAgents` holds the per-worktree choice: a map from worktree path to agent display name, stored in `settings.json` (never in the repository). `agentFor` returns the worktree's agent, or the default; `choose` records a non-default agent and forgets the entry when the default is chosen; `prune` drops entries of the project whose worktree no longer exists or whose agent was renamed or removed.

### `BranchType`

Enum `FEATURE` / `FIX`, each with a `prefix` (`"feature"` / `"fix"`) prepended to the task ID when naming the branch: `feature/TASK-123`.

### `Worktree` / `BranchOption`

- `Worktree` — an active worktree as reported by `git worktree list`: `path`, `branch` (`null` if `detached`), `head`, `isMain`, `isBare`, `isLocked` (a `locked` line, e.g. a worktree an agent session is using) and `isPrunable` (a `prunable` line: its folder no longer exists).
- `BranchOption` — a branch offered when creating a worktree: `name`, `isCheckedOut` (already checked out in the base repository or another worktree) and `isRemote` (an `origin/` branch, named with that prefix). `withoutLocalTwins()` drops the remote branches that have a local branch with the same short name.

A `Worktree` returned by creation also carries `skippedPostCreateCommands` and `skippedVariables`, the repository's post-creation commands that weren't run and the names of the declared variables that weren't applied because the repository isn't approved (see [Repository trust](#repository-trust)).

### `WorktreeStatus` / `MainBranch` / `Divergence`

- `MainBranch` — the branch worktrees are compared against: its `name` (`develop`) and the `ref` used in Git commands (`origin/develop`, or `develop` when there's no remote-tracking branch).
- `Divergence` — commits `ahead` and `behind` with respect to another branch.
- `WorktreeStatus` — the state of one worktree: `uncommittedChanges`, `upstream` and `mainBranch` divergences (`null` when they don't apply), `lastCommitAt`, `createdAt` (when the worktree was created, read from the modification time of the `commondir` file in Git's administrative folder for it, found through the worktree's `.git` file; `null` for the main worktree or when it can't be read) and `neverCommitted`. `hasOwnCommits` is true when the main branch comparison has `ahead > 0` (or, without a main branch, when the branch isn't "not started"). `isNotStarted` is `neverCommitted`: the branch never had commits of its own (see `ReadWorktreeStatusUseCase`). `isMerged` is derived: the branch isn't "not started", has nothing the main branch lacks (`ahead == 0`) and the main branch has moved past it (`behind > 0`), so neither a freshly created branch nor one whose main branch moved on since it was created is reported as merged.

### `RepositoryProvisioning`

What the repository declares that each new worktree needs, read from files at the root of the base repository and versioned with it, so it's shared by everyone who clones it and doesn't depend on the machine:

| Field | Source |
|---|---|
| `copyPatterns` | The `copy` list of `.shogunai.json`. |
| `linkPatterns` | The `link` list of `.shogunai.json`: files symlinked to the base repository's copy instead of copied, so every worktree shares them (copied when the file system can't create links). |
| `postCreateCommands` | `hooks.postCreate` of `.shogunai.json`: commands as argv lists, run in each new or provisioned worktree once the user has approved them on this machine (see [Repository trust](#repository-trust)). A single list of strings is one command. |
| `worktreeIncludePatterns` | The lines of `.worktreeinclude` (Claude Code's file), skipping blank lines and `#` comments. |
| `providers` | The `providers` object of `.shogunai.json`: the providers the repository turns on, each keyed by its id with an object of options. `gradle` (`GradleProvider`, optional `daemonMaxHeap`, `idleTimeoutMinutes` and `kotlinDaemonMaxHeap`) makes the [Gradle daemons](#gradle-daemons) visible for the repository and, with settings, bounds the daemons of worktree builds; `emulator` (`EmulatorProvider`, optional `avd` the repository prefers) gives each worktree its own [Android emulator](#android-emulator); `ios-simulator` (`IosSimulatorProvider`, optional `device` and `runtime` preferences) gives each worktree its own [iOS simulator](#ios-simulator); `xcode` (`XcodeProvider`, optional `compilationCache` and `cocoaPods`) shows and cleans up the [Xcode DerivedData](#xcode-deriveddata), can turn on compilation caching and can clone the base repository's CocoaPods pods into new worktrees. Unknown ids are ignored. |
| `environment` | The `env` object and `envFile` of `.shogunai.json`, as an `EnvironmentDeclaration` (see [Worktree environment](#worktree-environment)). |

`allPatterns` is the union of `copyPatterns` and `worktreeIncludePatterns` without duplicates; a file that also matches a `link` pattern is linked, not copied. Patterns use Git's ignore-pattern syntax, relative to the repository root: `local.properties` matches at any depth, `/local.properties` only at the root, `.idea/runConfigurations/` a whole folder, `.env*` a glob.

```json
{ "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"], "providers": { "gradle": {} } }
```

Unknown fields of `.shogunai.json` are ignored, so later versions can add fields without breaking older ones.

What provisioning did to each file is a `ProvisionedFile` (`path` and `ProvisionMode`: `COPIED`, `LINKED`, `COPIED_INSTEAD_OF_LINKING`). `ProvisionDrift` (`path` and `ProvisionDriftState`: `MISSING`, `DIFFERS`, `NOT_LINKED`, `DELETED_IN_BASE`) is a provisioned file that is no longer as provisioning left it, as `CheckProvisionedFilesUseCase` reports it.

### Worktree environment

Variables each worktree gets, declared in `.shogunai.json` (`WorktreeEnvironment.kt`):

```json
{
  "env": { "PORT": "${port}", "API_URL": "http://localhost:${port:api}", "APP_SUFFIX": "-${worktree}" },
  "envFile": ".env.shogunai"
}
```

- `EnvironmentDeclaration` — the `variables` in file order and the optional `envFile`, a path relative to the worktree root that must stay inside it. `portKeys` lists the ports the declaration needs.
- `EnvironmentVariable` — a valid variable name (letters, digits and `_`, not starting with a digit, and not reserved), its declared template text (`source`) and that template, parsed when the file is read into `TemplatePart`s: `Literal`, `WorktreeName` (`${worktree}`, the worktree's folder name) and `Port(key)`. `${port}` is keyed by the variable's own name, so each variable gets its own port; `${port:<key>}` is keyed `port:<key>`, shared by every variable that uses it (`:` can't appear in a variable name, so the keys never collide). `$$` is a literal `$`; any other `${…}` throws `InvalidEnvironmentTemplate`, which reading turns into `InvalidRepositoryConfig`.
- `WorktreeEnvironment` — a resolved environment: the rendered `variables`, the `ports` reserved for the worktree, the names of the declared variables `awaitingApproval`, and whether the declaration is approved (`declarationApproved`).

Variable names that change how shells, runtimes, the dynamic loader, Git, package managers, compilers, build tools, network clients or coding agents behave can't be declared (`isReservedVariableName`, compared in upper case against exact names, prefixes such as `XDG_`, `SSH_`, `NODE_` except `NODE_ENV`, `ANTHROPIC_` or `CLAUDE_`, and the suffixes `_PROXY`, `_OPTS` and `_HOME`; the full list is in [Configuration › Reserved variable names](../guide/configuration.md#reserved-variable-names)). Such a name, in `env` or in the `docker-compose` provider's `portVariables`, makes `.shogunai.json` invalid (`RepositoryConfigIssue.ReservedVariableName` in the settings form), whether or not the repository is approved. A template or `envFile` with a Unicode control (Cc) or format (Cf) character, or a line or paragraph separator (`hasHiddenCharacters`), is invalid too (`InvalidVariableValue`, `EnvFileInvalidCharacters`), so every prompt that shows them shows exactly what applies. Variables that providers add, such as `GRADLE_OPTS`, aren't declared and aren't checked; since their names are reserved, `env` can no longer replace them.

### Android emulator

The emulator provider (`Emulator.kt`) only acts for repositories that declare `"providers": { "emulator": {} }`; for any other repository nothing looks for the SDK, runs `adb` or adds variables.

- `EmulatorSettings` — the machine settings stored in `ProjectConfig.emulator`.
- `chooseAvd(requested, settings, preferred, available)` — the AVD to start: the one requested for this start, else the machine setting, else the repository's preferred AVD if this machine has it, else the first available one; `null` when there's none.
- A worktree reserves the console port key `emulator` among the even ports 5554–5584 (the range `adb` scans), with the next port (the emulator's adb port) also free, when it acquires its emulator from the [emulator pool](#resource-pools), the first time it starts one, not when its environment is resolved. From then on it gets `ANDROID_SERIAL=emulator-<port>` (`emulatorSerial`); before, its environment has no `ANDROID_SERIAL`. Android's Gradle plugin reads `ANDROID_SERIAL` in `installDebug`, `uninstall*` and `connectedAndroidTest` to use only that device.
- `SdkLocation` (SDK root, `emulator` and `adb` executables), `EmulatorState` (`STOPPED`, `BOOTING`, `RUNNING`) and `WorktreeEmulator` (serial and state) describe what the [Android SDK port](#androidsdk) reports.

### iOS simulator

The iOS simulator provider (`IosSimulator.kt`) only acts on macOS for repositories that declare `"providers": { "ios-simulator": {} }`.

- `SimulatorSettings` — the machine settings stored in `ProjectConfig.simulator`.
- `SimulatorRuntime` / `SimulatorDeviceType` — what Xcode has installed, as `simctl` reports it (identifier, display name, version, platform, and the device types a runtime supports).
- `chooseSimulator(requested, settings, preferred, runtimes)` — the runtime by display name (`iOS 18.5`, case-insensitive) from the first of this operation, the machine setting and the repository's preference that is installed, else the newest iOS runtime by version; then the device type by name among the runtime's iPhone and iPad types in the same order, else its newest iPhone: the highest model number in the name, and among those the shortest name (the base model, or the Pro when there's no base), whatever order Apple lists them in. `null` without an iOS runtime.
- A worktree keeps the resource `ios-simulator`, the UDID of a simulator created for it and named `ShogunAi · <repository folder> · <worktree folder>` (`worktreeSimulatorName`), once it acquires it from the [simulator pool](#resource-pools), the first time it starts one; it then gets `IOS_SIMULATOR_UDID=<UDID>` in its environment. The `ShogunAi · ` prefix is how orphaned simulators are told apart from the user's own.

### Resource pools

Emulators, iOS simulators and Gradle daemons are resources of limited capacity that worktrees share (`ResourcePool.kt`). There is no pool registry: a pool is computed from what already exists.

- `ResourceKind` (`EMULATOR`, `IOS_SIMULATOR`, `GRADLE_DAEMON`), `CapacityProfile` (`AUTOMATIC`, `LIGHT`, `BALANCED`, `PER_WORKTREE`, `CUSTOM`), `ResourceLimits` (`emulators`, `simulators` and `gradleDaemons`, where `null` means no limit) and `ResourceSettings` (the profile and its custom limits, stored as `resources` in the app settings and never in the repository).
- `PoolHolder` is one resource in use: its kind, worktree path, Git admin directory, state, whether it is idle, when it was last used, when its lease expires (`leaseExpiresAt`), the state of its worktree's agent (`agentState`, `null` without a live session) and its `releaseRank`. `PoolUsage` is a pool: its `limit`, its `effectiveLimit` (an emulator limit never exceeds the 16 console port pairs) and its holders. `PoolRelease` is what acquiring or trimming released.
- A worktree holds an emulator while it has the `emulator` port reserved, a simulator while it has an `ios-simulator` UDID recorded, and a Gradle daemon is held by the worktree of its last build. Kotlin daemons count against no pool.
- The profile table: Automatic gives `clamp(min((GB − 8) / 8, cores / 4), 1, 4)` emulators and simulators and `clamp(GB / 8, 2, 8)` Gradle daemons (Light when memory can't be read); Light is 1, 1 and 2; Balanced is 2, 2 and 4; One per worktree has no limit; Custom is a positive number or no limit per kind.
- Each worktree's `shogunai-env.json` keeps a `lastUsed` object (key to epoch milliseconds) next to its ports and resources. A resource without a record is the least recently used.
- The same file keeps `leases` (key to the epoch milliseconds of the last renewal) and `releases` (key to a `ReleaseRecord`: when it was released, the worktree it was released for and the previous port or UDID). A lease expires `ResourceSettings.leaseDuration` (`leaseMinutes`, 20 by default, a machine setting) after its last renewal; a missing lease counts as expired. The release record is cleared when the worktree acquires that kind again, and recent records of other worktrees quarantine their ports.
- `releaseRank` decides who can be released, and in what order: `null` for a worktree whose agent is working, a valid lease or a booting device; 1 for a stopped device with no agent session; 2 for a stopped device whose agent is done or idle; 3 for a running or booted device; 4 when the agent is blocked. Least recently used breaks ties.

### Desktop turn

The desktop is one machine-wide resource that agents take turns to drive with computer use (`DesktopTurn.kt`). It isn't a `ResourceKind`: it has no profile, limit or release order.

- `DesktopTurn` is the holder: its worktree's Git admin directory and path, `acquiredAt` and `expiresAt` (epoch milliseconds). A turn whose `expiresAt` has passed counts as free. `DesktopTurnState` is the holder (or `null`) and the live `WaitTicket`s in line; `DesktopTurnAcquisition` adds whether it was `renewed` and `waitedSeconds`; `DesktopTurnRelease` is `Released` or `NobodyHolds`.
- `DesktopTurnLedger` holds the `DesktopTurnStore`, the machine lock, the wait queue and the process check shared by the three use cases. `AcquireDesktopTurnUseCase` gives the turn for `ResourceSettings.desktopTurnDuration` (`desktopTurnMinutes`, 10 by default) when it is free or expired and nobody else is ahead in the `desktop` queue, renews it for its holder, and otherwise fails with `DesktopTurnTaken` or, given a `DesktopWait`, waits in line through the same `WaitInLine` loop as device acquisitions (`DesktopTurnWaitTimedOut` at the deadline). Only acquiring again renews a turn: an agent's working state doesn't.
- `ReleaseDesktopTurnUseCase` releases the holder's turn (`DesktopTurnHeldByOther` for another worktree), `ifHeldBy` a worktree for the `Stop` and `SessionEnd` hooks, `force` for the desktop app, and `onWorktreeRemoved`, called by the worktree removal before the folder goes, which also drops that worktree's tickets. `ReadDesktopTurnUseCase` returns the `DesktopTurnState`.

### Herdr

`Herdr.kt` holds what the [Herdr port](#herdrclient) reports: `HerdrWorktreeOpening` (workspace id, its root pane id and whether Herdr already had the worktree open), `HerdrAgent` (an agent pane: the agent's `name`, its `AgentState` or `null` when Herdr reports `unknown`, the directory it works in and its pane id), `HerdrPane` (a pane: its id, workspace, directory, the `agent` Herdr detects in it and its raw `agentStatus`, `unknown` when there's none; `hasLiveAgent` is any other status, `idle` and `done` included), plus `HerdrOpening`, the outcome of opening a worktree in Herdr: its `HerdrOutcome` (`OPENED`, `STARTED_AGENT`, `FOCUSED`), the agent's pane id and the agent's name. `AGENT_START_SECONDS` (10) is how long an open waits for Herdr to detect a typed agent.

### Orca

`Orca.kt` holds what the [Orca port](#orcaclient) reports: `OrcaStatus` (whether Orca's runtime is reachable and its version), `OrcaRepo` (a registered repository: id, path and its external-worktree visibility), `OrcaWorktree` (a workspace's path, whether Orca created it, its `OrcaAgent`s, each an agent name and its `AgentState`: Orca's `working`, `waiting` and `done` become `WORKING`, `BLOCKED` and `DONE`, and, for a folder workspace, its `folderId`) and `OrcaTerminal` (a terminal's handle, worktree path, empty for a folder workspace, and title), `OrcaWorkspace`, the target of the terminal operations: `Worktree(path)`, selected as `path:<path>`, or `Folder(id)`, a folder workspace that only Orca's app can create and that no path selects, as `id:folder:<id>` (`OrcaWorktree.workspace` gives a row's target), plus `OrcaOpening`, the outcome of opening a worktree in Orca: its `OrcaOutcome` (`STARTED_AGENT`, `FOCUSED`), the terminal handle, the agent and the confirmed session name. `ORCA_START_SECONDS` (30) is how long an open waits for Orca to start and `ORCA_MINIMUM_VERSION` (1.4.243) the oldest Orca ShogunAi accepts.

### Gradle daemons

What the Gradle provider observes on the machine (`GradleDaemon.kt`):

- `DaemonProcess` — a running daemon: `pid`, `kind` (`DaemonKind.GRADLE` or `KOTLIN`), `gradleVersion` (Gradle daemons only), `residentMemoryBytes`, `lastBuildDirectory` (the directory of the last build a Gradle daemon received) and `state`.
- `DaemonState` — `Busy`, `Idle(since)`, `Unknown` (a Gradle daemon whose state can't be read) or `Untracked` (Kotlin daemons, which don't record one).
- `GradleDaemon` — a `DaemonProcess` with its `owner` in a repository: `DaemonOwner.Worktree(path)`, `RemovedWorktree` (its last build directory no longer exists) or `OtherProject`; `null` for Kotlin daemons, daemons without a known build directory, or outside a repository.
- `DaemonStopReport` — the outcome of a stop: `stopped`, `kept` (each with a `DaemonKeptReason`: `BUSY`, `STATE_UNKNOWN` or `BUILD_RUNNING`), `notDaemons` (requested pids that aren't daemons) and `failures`.

### Xcode DerivedData

What the Xcode provider finds in Xcode's DerivedData location (`DerivedData.kt`), only on macOS for repositories that declare `"providers": { "xcode": {} }`:

- `DerivedDataFolder` — one project's folder: `path`, `workspacePath` (the `.xcodeproj`, `.xcworkspace` or package recorded in its `info.plist`), `sizeBytes` (`null` until measured), `lastAccessed` and `packagesBytes`, the part of the size that is Swift packages (`SourcePackages`).
- `DerivedDataOwner` — `Worktree(path)` (the worktree of the repository containing the workspace, nested worktrees resolved), `OtherProject` (the workspace exists elsewhere), `Orphaned` (the workspace no longer exists) or `UnavailableVolume` (the workspace is on a volume that isn't mounted, so it can't be told apart from a live project).
- `DerivedDataListing` — every folder with its owner (`AttributedDerivedData`), the size of the shared compilation cache when asked for and `packageCaches` (`PackageCacheSizes`: the SwiftPM and CocoaPods shared caches, from the `PackageCacheSource` port); `orphans` and `ofWorktrees()` filter it.
- `DerivedDataDeletion` — the outcome of deleting orphans: `deleted`, `failures` (`DerivedDataDeletionFailure`: path and detail) and `bytesFreed`.

### `WorktreeOrigin` / `RunningAgent`

What the worktree overview shows about each worktree (`WorktreeOverview.kt`): `WorktreeOrigin` (`SHOGUNAI`, `CLAUDE_CODE`, `HERDR`, `ORCA`, `OTHER`), the tool that created it as told by its location (`classifyOrigin`, see [Worktree overview](#worktree-overview)), and `RunningAgent`, an agent CLI running in it: its `name` and, when Herdr reports it, its `state` (`AgentState`: `WORKING`, `BLOCKED`, `DONE`, `IDLE`), the `stateSource` that reported it (`AgentStateSource`: `HERDR`, `ORCA`, `CLAUDE_CODE`) and, when Herdr reports it, its `herdrPaneId`. `AgentSessionRecord` (`AgentSession.kt`) is what Claude Code's hooks leave per session: `sessionId`, `agent`, `state`, `updatedAt`, `transcriptPath` and `pid`. `waitsForUser` is true for a blocked or done agent.

### Tools

`Tools.kt` describes what the desktop app can launch a worktree in: `HostOs` (`MACOS`, `WINDOWS`, `LINUX`), `TerminalId` (`TERMINAL`, `ITERM2`, `WINDOWS_TERMINAL`, which uses PowerShell, `TMUX`, `ZELLIJ`), `EditorId` (`VS_CODE`, `INTELLIJ_IDEA`, `ANDROID_STUDIO`, `XCODE`, the last only on macOS), `DetectedTools` (the terminals and IDEs found on the machine) and `TerminalLaunch` (the worktree path, its variables, the agent command and the full launch line a terminal runs).

`ShellQuoting` quotes values for the shell they end up in: `posix` (single quotes, `'\''` for an embedded one), `posixCommand` (an argv list as a line, quoting only words that need it), `powershell` (single quotes, doubling every kind of single quote) and `windows` (double quotes).

### Updates

`Update.kt` models the update check: `AppVersion` (`X.Y.Z`, compared numerically; `parse` accepts an optional `v` and rejects anything else; `DEVELOPMENT` is `0.0.0`), `HostArchitecture` (`ARM64`, `X64`), `PublishedRelease` (version, release page, `ReleaseAsset`s and `ReleaseNotes`), `InstallChannel` (`HOMEBREW`, `DMG`, `SCOOP`, `WINGET`, `MSI`, `DEB`, `UNKNOWN`) and `UpdateAction`, what the user should do for a channel: run a command (`brew upgrade --cask shogunai`, `scoop update shogunai`, `winget upgrade LuxionServer.ShogunAi`), download an asset picked from the release by extension and architecture (with `sudo apt install ./<asset>` for a `.deb`), or open the release page. `UpdateCheck` is the outcome: `NotDue`, `UpToDate` or `Available` with the release, channel, action and `notes`, the `VersionNotes` (version, release page, notes) of every published version newer than the running one, newest first. `UpdatePreferences` holds `checkAutomatically` and `skippedVersion`.

`ReleaseNotes.kt` holds a release's notes per language (`english`, `spanish`, either may be `null`). `parse(body)` reads a GitHub release body: with the `shogunai:notes:en` / `shogunai:notes:es` markers the workflow writes (see [Release process](../process/release.md#release-body-format)) it takes each language between its markers; without them, for releases before that format, it takes GitHub's generated notes as English, dropping each change's `by @user in <link>` suffix, the "What's Changed" heading, the "New Contributors" section and the "Full Changelog" line; an empty result is `NONE`. `forLanguage(language)` returns that language's notes, falling back to English.

`RecentWorktree` (`projectId`, `path`) is an entry of the recent worktrees the menu bar icon lists.

### `ThemeMode` / `AppLanguage`

User preferences: `LIGHT` / `DARK` / `SYSTEM` and `ENGLISH` / `SPANISH`. They're stored in `settings.json` (see [Infrastructure layer](infrastructure.md)).

### `WorktreeError`

`sealed class` of the flow's expected errors, so the UI decides what to show (and translates it) from structured data instead of parsing strings:

- `BaseRepositoryNotFound` — the configured base repo doesn't exist on disk.
- `WorktreeAlreadyExists` — there's already a directory at the destination path.
- `InvalidTaskId` — the task id isn't a valid Git ref segment once normalized.
- `SecretFileNotFound` — local files are missing in the base repo.
- `RepositoryNotFound` — the directory isn't inside a Git repository with a working tree (used by the CLI).
- `BareRepositoryNotSupported` — the directory is a bare repository, or inside one.
- `RepositoryHasNoCommits` — the repository has no commit to start a branch from.
- `LocalFileUnreadable` — `projects.json` or `settings.json` can't be parsed; keeps the path and the reason. The file is kept aside before it's written again (see [Infrastructure layer](infrastructure.md)).
- `UpdateCheckFailed` — the published releases couldn't be read from GitHub, or there were none.
- `NotAWorktree` — the path given to provision is the main worktree or isn't one of the repository's worktrees.
- `InvalidRepositoryConfig` — `.shogunai.json` exists but isn't valid JSON, isn't an object, its `copy` or `link` isn't a list of strings, its `hooks.postCreate` isn't a command or a list of commands, its `providers` (or a known provider in it) isn't an object, a provider option has the wrong type, or its `env` or `envFile` is invalid (a reserved variable name included); keeps the file path and the detail.
- `RepositoryConfigChanged` — `.shogunai.json` changed on disk since the settings form read it, so it isn't overwritten.
- `NoFreePort` — every port from 20000 to 29999 is reserved or busy.
- `EnvironmentFileWriteFailed` — the declared `envFile` couldn't be written; keeps the path and the cause.
- `StartPointNotFound` — the start point given to `shogun new --from` doesn't name a commit.
- `ProcessListingFailed` — `lsof` or `ps` failed while detecting running agents (e.g. on Windows).
- `NoEmulatorPort` — every emulator console port from 5554 to 5584 is reserved or busy.
- `DesktopTurnTaken` — another worktree holds a valid desktop turn, or others wait for it first; keeps the holder (`null` when free) and the number waiting.
- `DesktopTurnWaitTimedOut` — the desktop wasn't freed within the wait's bound; keeps how long it waited and the holder.
- `DesktopTurnHeldByOther` — a worktree tried to release a turn another one holds.
- `ResourcePoolFull` — a pool is at its limit and none of its resources is releasable; keeps the kind, the limit and every holder with its worktree, state and, while protected, its lease expiry or that its agent is working.
- `AndroidSdkNotFound` — the Android SDK wasn't found; keeps the places checked.
- `NoAvdAvailable` — the machine has no Android Virtual Device.
- `EmulatorBootTimeout` — the emulator didn't finish booting in time; it's left booting.
- `EmulatorExitedEarly` — the emulator process exited while booting; keeps the last lines of its log.
- `AndroidToolFailed` — `emulator` or `adb` failed; keeps the command, exit code and output.
- `EmulatorProviderNotDeclared` — an emulator command ran in a repository that doesn't declare the emulator provider.
- `NoIosRuntime` — Xcode has no iOS simulator runtime installed.
- `SimulatorBootTimeout` — the simulator didn't finish booting in time; it's left booting.
- `IosSimulatorProviderNotDeclared` — a simulator command ran in a repository that doesn't declare the iOS simulator provider.
- `IosSimulatorsNotSupported` — the host isn't macOS with Xcode.
- `AppleToolFailed` — `xcrun simctl` or another Xcode tool failed; keeps the command, exit code and output.
- `EnvironmentFileTracked` — the declared `envFile` is a file Git tracks in the worktree, so it isn't overwritten.
- `EnvironmentFileOutsideWorktree` — the declared `envFile` resolves, after following symlinks, outside the worktree.
- `InvalidRef` — a branch name or start point starts with `-`, so Git could read it as an option.
- `GitCommandFailed` — a Git command returned a non-zero exit code; keeps the command, exit code and Git's error output.
- `SecretCopyFailed` — copying or linking local or repository files failed after the worktree had already been created, including a file that would land outside the worktree and a failure to write the provisioning record.
- `LinkedFolderOccupied` — a local folder in link mode can't be linked because the worktree already has a real folder there holding files ShogunAi didn't copy; nothing in it is deleted.
- `PostCreateCommandFailed` — a post-creation command couldn't start or exited with an error; keeps the worktree, the command, its exit code and its error output. The worktree is kept.
- `ClipboardWriteFailed` — the launch command couldn't be copied to the clipboard.
- `GitLfsNotFound` — the repository uses Git LFS but `git-lfs` isn't installed.
- `BranchNotFound` — the requested existing branch doesn't exist locally.
- `BranchAlreadyCheckedOut` — the branch is already checked out in another worktree or in the base repository.
- `HerdrNotInstalled` — the `herdr` command wasn't found.
- `HerdrCommandFailed` — a `herdr` command failed; keeps the command and Herdr's message (e.g. no Herdr server running).
- `OrcaNotInstalled`, `OrcaNotStarted` (Orca didn't accept commands 30 seconds after `orca open`), `OrcaCommandFailed` (keeps the command and Orca's `error.message`, or names the minimum version for an older Orca) and `OrcaAgentNotStarted` (terminal handle and command) — opening a worktree in Orca failed. `OrcaMultirepoFolderNotOpen` (the folder) — Orca has no workspace for a multirepo folder that isn't a Git repository; its message gives the two ways to add it in Orca's app.
- `TerminalLaunchFailed`, `TerminalAutomationRefused` (macOS refused to let ShogunAi control Terminal or iTerm2) and `TerminalUnsupported` (not available on this system, or tmux older than 3.0) — opening a worktree in a terminal failed.
- `EditorLaunchFailed` and `FolderOpenFailed` — opening a worktree in an IDE or in the file manager failed; keep the tool's message.
- `WorktreeTargetNotFound`, `AmbiguousWorktreeTarget` and `CannotRemoveMainWorktree` — `shogun remove` was given a task id, path or branch that matches no worktree, several worktrees, or the main one.
- `AgentRunningInWorktree` — an agent runs in the worktree to remove; keeps the agents' names.

## Ports

Use cases depend on interfaces, not implementations. This is what allows testing them with doubles.

### `ShellCommandExecutor`

```kotlin
interface ShellCommandExecutor {
    suspend fun execute(command: List<String>, workingDirectory: String? = null): CommandResult
    suspend fun executeWithin(command: List<String>, timeout: Duration, workingDirectory: String? = null): CommandResult
}
```

The command is passed as **argv** (a list of strings), never as a string for a shell to interpret. Reason: to avoid differences between macOS's default shell (zsh) and Linux's (bash), and to avoid command escaping/injection issues.

`CommandResult` holds `command`, `exitCode`, `stdout`, `stderr` without interpreting their meaning — each use case decides what counts as success via `isSuccess` (`exitCode == 0`). A process that can't even start (e.g. the executable isn't in `PATH`) throws `CommandExecutionException` instead.

`executeWithin` bounds a command's run: one that doesn't finish in time is stopped and throws `CommandExecutionException.TimedOut`. Read-only commands pass a limit from `CommandTimeouts` (`GIT_READ`, 30 s, for Git reads; `LISTING`, 10 s, for device, process and tool listings; `NETWORK`, 60 s, for `git fetch`); commands that may legitimately run long (creating a worktree, post-creation commands, builds) use `execute`, with no limit. Its default implementation ignores the limit, so test doubles don't have to implement it. A cancelled coroutine always stops the command, and no command ever waits for input.

### `FileManager`

```kotlin
interface FileManager {
    fun exists(path: String): Boolean
    fun isInside(root: String, path: String): Boolean
    fun copy(source: String, destination: String)
    fun createSymbolicLink(target: String, link: String)
    fun isSymbolicLinkTo(link: String, target: String): Boolean
    fun hasSameContent(first: String, second: String): Boolean
    fun directorySize(path: String, excluding: Set<String> = emptySet()): Long
    fun isDirectory(path: String): Boolean
    fun listTree(directory: String): DirectoryListing
    fun isSymbolicLink(path: String): Boolean
    fun deleteFile(path: String)
    fun modificationStamps(path: String): List<Pair<String, Long>>
    fun readProperties(path: String): Map<String, String>
    fun writeText(path: String, text: String)
    fun readText(path: String): String?
    fun lastModifiedMillis(path: String): Long?
}
```

Abstracts the filesystem so that copying secrets and measuring worktrees is unit-testable without touching disk. `isInside` tells whether `path`, once its nearest existing ancestor is resolved through symlinks, is inside `root`; provisioning checks it before every copy, link and environment file write, so a hostile symlink in a branch can't make ShogunAi write outside the worktree. `copy` creates the destination's missing parent folders. `createSymbolicLink` replaces whatever is at `link` with a symlink, or throws `SymbolicLinksNotSupported` where links can't be created (e.g. Windows without the privilege); `isSymbolicLinkTo` and `hasSameContent` are what the provisioning check compares. `listTree` returns a `DirectoryListing` of a folder: its regular `files` and the symbolic links it skips (`skippedLinks`), both as relative, `/`-separated paths, hidden files included, without following links, and nothing if the folder is missing. `isSymbolicLink` tells a link apart without following it, and `deleteFile` removes one file or link, doing nothing if it's missing. `directorySize` adds up the regular files under `path`, skipping the folders in `excluding`. `modificationStamps` returns every file and folder under `path` (or `path` itself if it's a file) with its last-modified time, and nothing if it doesn't exist. `readProperties` reads a Java properties file (such as `gradle.properties`), empty if it's missing or unreadable. `writeText` replaces a file atomically, creating its folders; `readText` returns its content, or `null` if it's missing or unreadable. `lastModifiedMillis` returns a file's last-modified time, or `null` if it's missing or unreadable.

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
    fun fingerprint(repositoryPath: String): String
    fun addPatterns(
        repositoryPath: String,
        copyPatterns: List<String>,
        linkPatterns: List<String> = emptyList(),
        expectedFingerprint: String? = null,
    )
    fun preview(repositoryPath: String, edit: RepositoryConfigEdit): String
    fun write(repositoryPath: String, edit: RepositoryConfigEdit, expectedFingerprint: String)
}
```

Reads the repository's provisioning (throwing `InvalidRepositoryConfig` for an unreadable `.shogunai.json`), appends patterns to `copy` and `link` in one write (how the configuration screen moves local files into the repository and links the mobile secrets it suggests), and previews and writes the providers, the environment and the pending patterns edited in the settings form (`RepositoryConfigEdit`, whose `addCopyPatterns` and `addLinkPatterns` are appended without duplicates).

### `ProvisioningRecordStore`

```kotlin
interface ProvisioningRecordStore {
    fun read(worktreePath: String): Map<String, List<String>>
    fun write(worktreePath: String, localFolders: Map<String, List<String>>)
}
```

Remembers, per worktree, the files provisioning copied from each local folder (folder entry → files relative to it). `NioProvisioningRecordStore` keeps it in `shogunai-provisioning.json` inside the worktree's Git admin folder (found through the worktree's `.git` file), so it's never in the working tree and goes away with the worktree. A missing or unreadable record reads as empty; an empty record removes the file. `ProvisioningRecordStore.NONE` keeps nothing, for callers that don't need it. `fingerprint` is a hash of the file's content; a write given the fingerprint read earlier fails with `RepositoryConfigChanged` when the file changed meanwhile.

### `EnvironmentStateStore` / `PortProbe`

```kotlin
interface EnvironmentStateStore {
    fun readPorts(adminDirectory: String): Map<String, Int>
    fun writePorts(adminDirectory: String, ports: Map<String, Int>)
    fun readResources(adminDirectory: String): Map<String, String>
    fun writeResources(adminDirectory: String, resources: Map<String, String>)
    fun readLastUsed(adminDirectory: String): Map<String, Long>
    fun writeLastUsed(adminDirectory: String, lastUsed: Map<String, Long>)
    fun readLeases(adminDirectory: String): Map<String, Long>
    fun writeLeases(adminDirectory: String, leases: Map<String, Long>)
    fun readReleases(adminDirectory: String): Map<String, ReleaseRecord>
    fun writeReleases(adminDirectory: String, releases: Map<String, ReleaseRecord>)
    fun reservationsOnMachine(commonDirectory: String): List<WorktreeReservations>
    fun release(adminDirectory: String, key: String)
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
    fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean): EmulatorProcess
    suspend fun stopEmulator(sdk: SdkLocation, serial: String)
}
```

Everything the emulator provider needs from the Android SDK, used only for repositories that declare the provider: finding the SDK (`AndroidSdkNotFound` with the places checked), listing AVDs, the state of every running emulator in one call, starting an emulator detached on a console port, and stopping one. The started `EmulatorProcess` tells whether the process is still alive and gives the last lines of its log, so a boot wait can fail as soon as the emulator exits. Tool errors are `AndroidToolFailed`.

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
    suspend fun listAgents(): List<HerdrAgent>
    suspend fun focusAgent(paneId: String)
}
```

The few [Herdr](https://herdr.dev) operations ShogunAi uses to open a worktree there and to close it again: `openWorktreeWorkspaces` returns, for the worktrees of a repository that Herdr has open, their path and workspace id; `closeWorkspace` closes a workspace and the processes in its panes. The rest: open an existing worktree as a focused workspace grouped under its repository (or focus it if it's already open), split a pane into a focused one whose shell starts in a directory with extra variables, close a pane, and submit a command in a pane. `listAgents` returns every agent pane Herdr tracks with its state, `listPanes` returns the panes of every workspace, or of one, with their directory and agent status, and `focusAgent` brings one agent's pane to the front. Pane ids aren't stored, since Herdr renumbers them when panes move; the ones from `listAgents` are only used right away. Errors are `HerdrNotInstalled` and `HerdrCommandFailed`; an agent that Herdr doesn't detect after it's typed is `HerdrAgentNotStarted` (pane and command), raised by the open use cases.

### `OrcaClient`

```kotlin
interface OrcaClient {
    fun isInstalled(): Boolean
    suspend fun status(): OrcaStatus
    suspend fun start()
    suspend fun bringToFront()
    suspend fun repos(): List<OrcaRepo>
    suspend fun addRepo(path: String): OrcaRepo
    suspend fun showExternalWorktrees(repoId: String)
    suspend fun worktrees(repoId: String): List<OrcaWorktree>
    suspend fun agentsByWorktree(): List<OrcaWorktree>
    suspend fun terminals(workspace: OrcaWorkspace): List<OrcaTerminal>
    suspend fun terminalAgent(handle: String): String?
    suspend fun createTerminal(workspace: OrcaWorkspace, title: String, command: String, focus: Boolean): OrcaTerminal
    suspend fun switchTo(handle: String)
    suspend fun closeTerminals(workspace: OrcaWorkspace)
}
```

The [Orca](https://onorca.dev) operations ShogunAi uses, in Orca's own vocabulary (a repository with worktrees and terminals, not Herdr's workspaces and panes): whether Orca is installed and its runtime reachable, starting the desktop app or bringing its window to the front, registering a repository and showing its non-Orca worktrees, listing a repository's visible worktrees, every workspace's agents in one call (folder workspaces included), a workspace's terminals and the agent Orca identifies in one, creating a focused terminal that runs a command, switching to a terminal and closing every terminal of a workspace. The terminal operations take an `OrcaWorkspace`, so the same calls serve a worktree and a multirepo's folder workspace. There's no generic runner port yet: it's extracted when the runner becomes a choice. Errors are the `Orca*` cases of `WorktreeError`.

### `OrcaWorkspaceStore`

Remembers the folder where Orca last created a worktree (`workspacesRoot`, `remember`), so Orca's worktrees can be recognized while Orca isn't running. `NONE` remembers nothing.

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
    suspend fun size(folder: DerivedDataFolder): Long

    suspend fun packagesSize(folder: DerivedDataFolder): Long
    suspend fun compilationCacheBytes(): Long?
    fun projectExists(workspacePath: String): Boolean
    fun isOnUnavailableVolume(workspacePath: String): Boolean
    suspend fun delete(folder: DerivedDataFolder)
}
```

Everything the Xcode provider needs from Xcode's DerivedData: whether the host is macOS, the project folders in the current location without their sizes (never Xcode's shared caches, which have no `info.plist`), the size of one folder measured on demand and cancellable, the size of the shared compilation cache (`null` when it doesn't exist), whether a workspace still exists or sits on an unmounted volume, and deleting one folder.

### `TerminalLauncher` / `EditorLauncher` / `FolderLauncher` / `ToolDetector`

```kotlin
interface TerminalLauncher {
    val terminal: TerminalId
    suspend fun open(launch: TerminalLaunch): Result<Unit>
}

interface EditorLauncher {
    val editor: EditorId
    suspend fun open(path: String): Result<Unit>
}

interface FolderLauncher {
    suspend fun open(path: String): Result<Unit>
}

interface ToolDetector {
    suspend fun detect(): DetectedTools
}
```

One `TerminalLauncher` per supported terminal opens a new window or tab in the worktree and runs the launch line there; one `EditorLauncher` per IDE opens the worktree's folder; `FolderLauncher` shows it in the system's file manager; `ToolDetector` finds which terminals and IDEs the machine has. Failures are the typed `TerminalLaunchFailed`, `TerminalAutomationRefused`, `TerminalUnsupported`, `EditorLaunchFailed` and `FolderOpenFailed`.

### `ReleaseSource`

```kotlin
interface ReleaseSource {
    suspend fun published(): Result<List<PublishedRelease>>
}
```

The recent published releases of ShogunAi, without drafts or pre-releases, or `UpdateCheckFailed`.

### `RepositoryTrust` / `RepositoryApproval`

```kotlin
data class RepositoryApprovalSubject(
    val commands: List<List<String>>,
    val variables: List<Pair<String, String>>,
    val envFile: String?,
)

interface RepositoryTrust {
    fun isApproved(repository: String, subject: RepositoryApprovalSubject): Boolean
    fun approve(repository: String, subject: RepositoryApprovalSubject)
    fun revoke(repository: String)
}

fun interface RepositoryApproval {
    suspend fun decide(repository: String, subject: RepositoryApprovalSubject): ApprovalDecision
}
```

The subject is what `.shogunai.json` asks ShogunAi to run or apply: the post-creation commands, each declared variable with its template text, and `envFile` (`RepositoryProvisioning.approvalSubject`). `RepositoryTrust` remembers, per repository and on this machine, the exact subject the user approved; any difference isn't approved, and an empty subject always is. `RepositoryApproval` asks the user when it isn't, and answers `APPROVE` or `SKIP`: the desktop app shows the subject in the dialog that started the creation, the CLI asks on a terminal, and hooks and `--json` use `RepositoryApproval.SKIP`. `RepositoryTrust.NONE` approves only empty subjects, which is the default for use cases built without a trust store, so forgetting to wire it fails closed.

### `AgentSessionStore`

Keeps the `AgentSessionRecord`s of the sessions of a worktree: `write`, `remove` and `list`, by worktree path. `FileAgentSessionStore` is the implementation (see [infrastructure](infrastructure.md#fileagentsessionstore)); the CLI's `hook agent-state` writes and `DetectRunningAgentsUseCase` reads and deletes dead records.

### `ClaudeSessionRegistry`

Lists the Claude Code sessions running on the machine as `ClaudeSession` (`pid`, `cwd`, `name`, optional `startedAt`). It is Claude Code's own registry, read only and separate from `AgentSessionStore`, which is ShogunAi's hook-fed state. `FileClaudeSessionRegistry(directory)` reads the `*.json` files of `<Claude config dir>/sessions` (`ClaudeCodeHooks.claudeSessionsDirectory`, from `CLAUDE_CONFIG_DIR` or the user home, built at the composition roots) leniently: unknown fields are ignored, files without `pid` or `cwd` or that can't be read or parsed are skipped, a missing directory has no sessions, and it never writes. Liveness is left to the consumers.

Session names live next to `AgentLaunchConfig`: `sessionName(parts)` lowercases, turns every run of characters outside `[a-z0-9]` into one hyphen, trims hyphens, joins the parts with `-` and caps the result at 64 characters; `groupMemberSessionName`, `worktreeSessionName` (the project folder's name, plus the worktree's folder unless it is the main worktree) and `multirepoSessionName` build the names `open --herdr` uses. `namedClaudeCommand(command, name)` appends ` -n <name>` only to a single command that runs `claude` (by name or path), optionally after `NAME=value` assignments, `env` or `headroom wrap`, and without its own `-n`/`--name`; it returns `null` otherwise. `uniqueSessionName` picks the first of `name`, `name-2`, `name-3`… not taken.

### Multirepo workspace repository

`MultirepoDeclaration` has an optional `workspace` (`WorkspaceSetting`: `enabled`, `alias`, `default`; in JSON `false` or `{ "alias", "default" }`). `Multirepo` carries `folderIsRepository`, set by `ResolveMultirepoUseCase.build` from the folder's `.git` entry. `workspaceRepository` is the folder as a repository when that is true and the declaration doesn't turn it off (or lists the folder in `repos` with the path `.`), `workspaceDetected` says no declaration states it, and `repositories`, `aliases` and `defaultRepos` are the effective lists with it, so create, status, clean and the desktop catalog treat it as one more repository; `declaredRepositories` is the `repos` map alone. `validate` rejects an invalid alias or one that collides with a `repos` key. `ConfigureMultirepoUseCase.setWorkspace`/`clearWorkspace` write only that field, and `setDefaults` maps the workspace alias to its `default` flag. A workspace repository without a project gets `<worktreesRoot>/<alias>` as worktrees folder in `multirepoMembers`. `RegisterProjectUseCase` takes the multirepo whose workspace is the folder (`withWorkspaceAt`) and registers that project with the proposed folder.

`AgentSearch.multirepoFolder(folder, command)` is an exact search: `DetectRunningAgentsUseCase` attributes to it only a working directory equal to the folder, never a subfolder or a member repository.

### Multirepo approval and local agent

A declaration can come from a cloned repository, so `ResolveMultirepoUseCase` only applies a multirepo that is registered in `MultirepoSource.knownFolders()` and whose `approvalSubject` is approved there (`Multirepo.approved`). The subject is a text with the normalized folder, each declared alias with its resolved path (sorted by alias), the resolved worktrees root and the declared workspace setting: what decides where ShogunAi creates branches and worktrees. `defaultRepos` and the declaration's `agent` aren't part of it. The upward walk of `invoke` only matches registered folders, `declaring` and `approved()` skip unapproved multirepos, and `known()` returns every registered one with its state for the desktop app. `nearestDeclaration` finds the nearest declaration registered or not, for `shogun multirepo trust`/`show` and the CLI's note about an ignored declaration. `validate` rejects aliases, repository paths and `worktreesRoot` with control or Unicode formatting characters, so the review can't be spoofed with escape sequences.

The multirepo agent is a local setting (`MultirepoSource.agent`/`setAgent`, `Multirepo.localAgent`); `Multirepo.agent` falls back to `claude` without Headroom and never reads `MultirepoDeclaration.agent`, which only survives so older files parse and `ignoresDeclaredAgent` can report it. `ConfigureMultirepoUseCase` refuses to write an unapproved multirepo (`WorktreeError.MultirepoNeedsReview`), writes the declaration without `agent`, and approves the subject it wrote, so ShogunAi's own edits keep it approved while an edit made elsewhere doesn't. `trust` registers and approves exactly the declaration that was reviewed (it refuses if the file changed in between) and `revoke` forgets it.

### Repositories

- `ProjectRepository` — `list()`, `save(project)`, `delete(id)` over the project catalog.
- `ThemePreferenceRepository` / `LanguagePreferenceRepository` — `load()` / `save(...)` of each user preference.
- `ShellPreferenceRepository` — the desktop shell's state: last destination, window bounds, sidebar and collapsed groups, the last project used to create, the palette's recent items, the grouping and sorting of each view, the inactivity threshold, the recent projects and worktrees, and the tray options.
- `ToolPreferenceRepository` — the chosen terminal and default IDE.
- `UpdatePreferenceRepository` — `load()`, `setCheckAutomatically(enabled)` and `skip(version)`.

## Use cases

The worktree use cases are built per project (they receive its `ProjectConfig`) and return `Result<T>` (using `runCatching`), with typed errors as `WorktreeError`. Every Git command runs from the base repository.

### `CreateWorktreeUseCase`

Creates a worktree on a **new** branch `<type>/<taskId>`. The task id is normalized first (`normalizeTaskId`: trimmed, whitespace runs replaced with `-`) and validated as a Git ref segment; a start point that starts with `-` is `InvalidRef`. Then, in this order:

1. **Upfront validations** (base repo exists, destination free, local files present) and **resolving the repository's files** — done *before* touching Git so as not to leave a half-done worktree.
2. `git worktree add <path> -b <branch> [<startPoint>]`. The caller resolves the optional start point to a commit id with `ResolveStartPointUseCase` (`git rev-parse --verify --quiet <ref>^{commit}` in its own directory, failing with `StartPointNotFound`), so `HEAD` means the worktree the command runs in even though Git runs in the base repository. The desktop app passes the ref chosen in its "Start from" selector, resolved the same way; without one, the branch starts at the base repository's HEAD. When the caller also passes a `baseBranch`, it's recorded right after as `branch.<branch>.gh-merge-base` (see [Base branches](#base-branches)); if that write fails, the worktree is rolled back like any later failure.
3. Copying the local files and the repository's `copy` files into the new directory, at the same relative paths, and symlinking the `link` files to the base repository's copies (copying them where links aren't supported). Every destination must stay inside the worktree once symlinks are resolved (`FileManager.isInside`).
4. Settling the repository's approval (see [Repository trust](#repository-trust)): approved already, or asked through `RepositoryApproval`, recording an `APPROVE` before going on.
5. Preparing its environment through the `WorktreeEnvironmentPreparer` it receives: `PrepareWorktreeEnvironmentUseCase` resolves it with reservation (`ResolveWorktreeEnvironmentUseCase`) and, when `envFile` is declared and the declaration is approved, `WriteEnvironmentFileUseCase` writes it in dotenv format, refusing a file Git tracks (`git ls-files --error-unmatch`, `EnvironmentFileTracked`) and reporting write errors as `EnvironmentFileWriteFailed`. The preparer defaults to `WorktreeEnvironmentPreparer.NONE`, which tests that don't exercise the environment rely on; the app and the CLI always pass the real one.
6. Running the repository's post-creation commands in the worktree, only when approved. When the repository isn't approved and the user doesn't approve it now, its commands and the names of its declared variables are returned in the worktree's `skippedPostCreateCommands` and `skippedVariables`. A failing command is `PostCreateCommandFailed`, and the worktree is kept, since everything before it succeeded.

Resolving the repository's files reads `RepositoryProvisioning` and, if it declares any pattern, runs `git ls-files --others --ignored -z -x <pattern>…`: every file not tracked by Git that matches a pattern (tracked files already come with the checkout). Without patterns no Git command runs. An unreadable `.shogunai.json` (`InvalidRepositoryConfig`) or a failing `git ls-files` (`GitCommandFailed`) stops creation before anything is created. Local files are checked strictly (`SecretFileNotFound`), patterns leniently: a pattern with no match copies nothing. A local entry that is a folder in copy mode is expanded with `FileManager.listTree` into the files under it each time the files are resolved, so creation, provisioning and the drift check see plain files and a file added to the folder later is picked up; the symbolic links it skips are carried as `skippedLinks`. A folder that already exists in the worktree is merged (same-path files overwritten, the rest kept); a missing folder is `SecretFileNotFound` like a missing file. A local entry in link mode is linked, whatever the repository declares for it, and one in copy mode is copied; a linked folder becomes one symbolic link to the base folder, which replaces a real folder only when every file in it is recorded as copied from that folder (otherwise `LinkedFolderOccupied`), and a copied folder whose destination is a link to the base folder has that link removed first. A local pattern is expanded with the same `git ls-files` call as `copy`, so it only selects untracked files, and isn't checked for existence.

After copying, the `ProvisioningRecordStore` record of each copied folder is replaced by the files just copied. Before copying, a recorded file that no longer exists in its base folder is deleted from the worktree when it's a regular file there, and returned as removed; files the record doesn't list are never touched. The drift check reports such files as `DELETED_IN_BASE` instead.

If copying or preparing the environment fails **after** the worktree was created, it's rolled back with `git worktree remove --force` and the branch the creation made is deleted with `git branch -D -- <branch>` (both best-effort) before propagating the error, so a retry finds nothing in its way. If `git worktree add` fails because `git-lfs` is missing, the half-created worktree and its new branch are removed the same way and `GitLfsNotFound` is returned.

### `CreateWorktreeFromBranchUseCase`

Same flow for a branch that **already exists**, locally or only on `origin`. The name is looked up exactly with `git show-ref --verify --quiet`: a local branch (`refs/heads/<name>`) runs `git worktree add <path> <branch>` (without `-b`); otherwise `origin/<name>`, or a name given with the `origin/` prefix, creates a local branch that tracks it with `git worktree add --track -b <name> <path> origin/<name>`, and a rollback deletes that new branch. Either way the folder is the one `worktreePathForBranch` gives the local name, and files, environment and post-creation commands follow as in `CreateWorktreeUseCase`. A branch already checked out elsewhere fails with `BranchAlreadyCheckedOut`, an unknown one with `BranchNotFound`, and a name starting with `-` with `InvalidRef`.

### `ListBranchesUseCase`

Lists local branches and `origin/` branches (`git for-each-ref --format=%(refname) refs/heads refs/remotes/origin/`, skipping `origin/HEAD`) and flags the local ones already checked out, using `git worktree list --porcelain`. The UI shows the flagged ones disabled, and in "Existing branch" hides remote branches that have a local twin (`withoutLocalTwins`).

### `FetchRemoteUseCase`

Runs `git fetch --prune origin` in the base repository with the network timeout, Git's credential prompts off (`GIT_TERMINAL_PROMPT=0`, so missing credentials fail instead of waiting), and reports a failure as `GitCommandFailed`. `hasOrigin()` tells whether the repository has an `origin` remote, which is when the desktop app offers the fetch. `shogun new --fetch` and the "Fetch from origin first" option use it before resolving the start point or the branch.

### `ListWorktreesUseCase`

Runs `git worktree list --porcelain` (a stable format meant to be parsed, unlike the default human-readable output) and parses the result with `parseWorktreeList`. Marks `isMain = true` on the worktree whose path is the same directory as `config.baseRepositoryPath` (`sameDirectory`: symlinks resolved, trailing slashes and separators ignored, case-insensitive on macOS and Windows). When it is given a `FileManager` (the desktop app does), a base repository that no longer exists fails with `BaseRepositoryNotFound` without running Git.

### `ResolveMainBranchUseCase`

Resolves the `MainBranch` once per list load, from the base repository and without network access: `config.mainBranch` if set, otherwise the `origin` remote's default branch (`git symbolic-ref refs/remotes/origin/HEAD`), falling back to a local `main` and then `master`. A named branch is compared through `origin/<name>` when that ref exists (`git rev-parse --verify`), because merges usually reach the remote first, and through the local branch otherwise. Returns `null` when nothing can be resolved.

### `ReadWorktreeStatusUseCase`

Reads a `WorktreeStatus`, running each command in the worktree's own folder:

- `git --no-optional-locks status --porcelain` — uncommitted changes. Optional locks are disabled so `git status` never rewrites the index; otherwise reading statuses would change the metadata that auto refresh watches. It's the only required command: if it fails (e.g. the folder is gone), the whole status fails.
- `git rev-list --left-right --count @{upstream}...HEAD` — upstream divergence, skipped on a detached HEAD and `null` if there's no upstream.
- `git rev-list --left-right --count <main ref>...HEAD` — main branch divergence, skipped when there's no main branch or the worktree is on it.
- `git log -1 --format=%ct` — last commit date, `null` if there are no commits.
- `git reflog show --format=%H refs/heads/<branch>` — only when the branch would otherwise read as merged and has no upstream: a reflog with a single entry (its creation) that is still the branch's tip means the branch never had commits of its own, so it reads as "not started" (`neverCommitted`) instead of merged.

Every command runs with the Git read timeout.

Squash and rebase merges aren't detected as merged, and remote-tracking branches are only as fresh as the user's last fetch.

### `CleanUpWorktreesUseCase`

`selectCleanup(worktrees, statuses, runningAgents)` picks the worktrees to clean up: not main, not locked, and either prunable or merged into the main branch with no uncommitted changes. Worktrees without a loaded status are never picked, and a qualifying worktree where an agent is running is moved to `keptForRunningAgent` with its agents, so neither the desktop app nor `shogun clean` removes it. `selectCleanupCandidates` returns only the candidates.

`CleanUpWorktreesUseCase` takes those candidates, the `MainBranch` and whether to delete branches, and returns a `CleanupReport` (`removed`, `keptBranches`, `failures`, `herdrFailures`, `orcaFailures`). Each existing worktree is removed with `git worktree remove` (never `--force`), and missing ones with a single `git worktree prune`. A failing step is recorded and the rest continue. Then the branches of every removed worktree, pruned ones included, are deleted only if `git merge-base --is-ancestor <branch> <main ref>` still succeeds, and then with `git branch -D`: `-d` checks against the base repository's `HEAD`, which in a git-flow repository may not contain a branch merged into `develop`.

### Worktree overview

- `classifyOrigin(worktree, config, herdrWorktreesRoot, orca)` returns the `WorktreeOrigin` of a non-main worktree: `CLAUDE_CODE` if its path is inside a `.claude/worktrees/` folder (checked first, since that folder can be inside the worktrees root), `HERDR` if it's inside Herdr's worktrees folder (when one is given, read through [`HerdrConfigSource`](#herdrconfigsource)), `ORCA` when the given `OrcaOrigins` says Orca created it, `SHOGUNAI` if it's inside `config.worktreesRoot`, `OTHER` otherwise. Paths are compared with `/` separators and without trailing slashes, so Windows paths work too.
- `activityDate(worktree, status)` returns an `ActivityDate(at, kind)`: the last commit's date (`COMMIT`) when the worktree is the main one or its branch has commits of its own, and otherwise the worktree's creation date (`CREATED`), falling back to the last commit when it can't be read. The row, the "Last activity" order and `isInactive` all use it.
- `isInactive(worktree, status, agents, now, inactiveAfter)` is true for a non-main worktree with commits of its own, no uncommitted changes, no running agents (an idle one counts) and an activity date older than `inactiveAfter`, a `Duration` that the app takes from its `inactiveAfterDays` setting (3 days by default, `DEFAULT_INACTIVITY_THRESHOLD`).
- `DetectRunningAgentsUseCase` returns the `RunningAgent`s per worktree path, from two sources read concurrently. The process scan runs `lsof -d cwd -F pcn` once to get every process's working directory, assigns each process to the worktree with the longest path containing it (`owningWorktree`, so a process in `.claude/worktrees/<name>` isn't attributed to the main worktree), and runs `ps -o pid=,comm=` for those processes. Their executable's file name is matched against the known agent CLIs (`claude`, `codex`, `gemini`, `cursor-agent`, `opencode`, `aider`, `copilot`) and the executables of every agent of the project. It can search several projects at once (`AgentSearch`, a project's agent commands and worktrees): `lsof` and `ps` still run once, and a project's own agents are only recognized in that project's worktrees. The `lsof` command name isn't used because Claude Code renames its process to its version. When it's given a `HerdrClient`, it also reads `listAgents()` (5-second limit) and attributes each Herdr agent to a worktree by its directory: it enriches the process-detected agent of the same name with its Herdr state and pane, or is added when the process scan can't see it, and several panes of one agent in a worktree keep the most urgent state (blocked, done, working, idle). A Herdr failure (not installed, no server, timeout) gives no Herdr data, never fails detection, and makes the next 30 seconds skip Herdr. If `lsof` or `ps` fail (e.g. on Windows) and Herdr reports nothing, it fails with `WorktreeError.ProcessListingFailed`.
- Given an `AgentSessionStore`, it also reads the records of every worktree and gives the `claude` agent the state of its most urgent live session (blocked, done, working) with source `CLAUDE_CODE`. A record is live while its `pid` is a `claude` process found by the same `ps` call (so a reused pid doesn't revive it); a record without a pid is live while a `claude` process runs in that worktree and it is under 12 hours old. Dead records are deleted. A blocked or done record whose transcript was modified more than one second after the record reads as working. Herdr's state, applied after, wins; a Herdr agent without a state keeps the hook's.
- Given an `OrcaWorktreeReader`, it also reads Orca's agents once per detection: the reader skips Orca when it isn't installed, checks `status()` and never calls `start()`, runs `agentsByWorktree()` (one `orca worktree ps`) within 2 seconds, and after a failure, a timeout or a closed Orca skips Orca for 30 seconds. Rows are matched to worktrees by canonical path (`/tmp` and `/private/tmp` are the same), Orca's agents enrich the process-detected agent of the same name with source `ORCA`, or are added when the process scan can't see them, and the most urgent of several agents of one name wins. Orca's state wins over the hook's; a Herdr state at least as urgent is kept. After each read the reader also gives `origins()`: the canonical paths of the worktrees Orca created, or, while Orca can't be asked, the folder Orca last created worktrees in (remembered through `OrcaWorkspaceStore`) or `~/orca/workspaces`.
- Given a `ClaudeSessionRegistry`, it attaches to each worktree's `claude` agent the `ClaudeSessionRef`s (`name`, `pid`) of the named sessions whose `pid` is in the same `claude` pids and whose `cwd` is attributed to that worktree as processes are (deepest worktree, not inside a nested repository, exactly the folder for the multirepo agent), sorted by name. A registry that fails leaves the agents without sessions.
- `worktreeActivity(worktree, status, agents, now, inactiveAfter)` gives each worktree one state for the desktop app's views and the menu bar, the first that applies: `Missing`; `NeedsYou` when an agent is blocked or done (`waitsForUser`); `AgentWorking` when an agent is working or has no known state (idle agents are skipped, so they never decide the state); `Unknown` (status not read); `NeedsYou` for uncommitted or unpushed commits; `Idle` (main worktree); `New` (no commits of its own and not merged); `Merged`; `Inactive`; and `Stopped` otherwise. The first four are the *active* ones (`isActive`).
- `MeasureWorktreeSizeUseCase` returns a worktree's size with `FileManager.directorySize`, on the IO dispatcher, excluding every other listed worktree nested inside it. It returns `null` for a missing folder.
- `ReadGitMetadataFingerprintUseCase` returns a fingerprint of the main repository's Git metadata: the sorted (path relative to `.git`, last-modified time) pairs of `HEAD`, `index`, `packed-refs`, `FETCH_HEAD` and everything under `refs` and `worktrees`, read on the IO dispatcher. Commits, checkouts, locks, fetches and worktrees added or removed by any tool change it; `objects` isn't included. It's empty when `<baseRepositoryPath>/.git` isn't a directory.

### `ClaudeCodeHooks`

Installs, updates, checks and removes ShogunAi's hooks in a Claude Code `settings.json`, working on the parsed JSON tree (never on text) through `FileManager`. The desired hooks are one table (`DESIRED`: event → command); a hook is ShogunAi's when it is a `command` hook whose command starts with `shogun hook `. `previewInstall` / `previewUninstall` compute a `HooksPreview` (added, replaced and removed hooks); `install` removes ShogunAi hooks that differ and appends the missing ones as their own matcher group, `uninstall` removes them and the groups and events left empty, and neither writes when the preview is empty. `status` gives `HooksInstallState.INSTALLED`, `OUTDATED` or `MISSING` with the events that differ. A file that isn't a JSON object, or whose `hooks` isn't one, is `WorktreeError.InvalidClaudeSettings` and is never written. `userSettingsPath` honors `CLAUDE_CONFIG_DIR`; `projectSettingsPath` is `<repository>/.claude/settings.json`.

### `RemoveWorktreeUseCase`

Runs `git worktree remove [--force] <path>` and, optionally, `git branch -d -- <branch>` to delete the local branch (safe delete, not `-D`). It returns a `WorktreeRemoval` whose `herdrFailure` is the error from closing the worktree's Herdr workspace, when that failed (see [`CloseHerdrWorkspacesUseCase`](#closeherdrworkspacesusecase)).

The removal itself (`WorktreeFolderRemoval`, shared with cleanup) handles three failures of `git worktree remove`. A locked folder closes the worktree's Herdr holders and retries once, and fails with `WorktreeFolderInUse` if it's still locked. "is not a working tree" and "Filename too long" mean Git has already unregistered the worktree and left its folder behind (Git deletes the administrative directory even when deleting the folder fails), so the leftover is finished: `git worktree prune`, then `FileManager.deleteRecursively` on the folder, unless it's the base repository or has its own `.git` directory. A folder that can't be deleted becomes `WorktreeFolderInUse` with `unregistered = true`.

!!! note
    Git doesn't allow removing a worktree from inside itself, which is another reason every command runs from the base repository.

### `ReleaseWorktreeResourcesUseCase` / `resolveWorktreeTarget`

Removing one worktree also releases what its providers hold, in two steps around the removal. `plan(config, worktree, allWorktrees)` reads, while the worktree still exists, its running emulator, its booted simulator and the idle Gradle daemons whose last build was in it, each only when the repository declares that provider; `release(config, plan)` stops them after the removal succeeded. Failures are collected as messages in `ReleasedResources.failures` instead of failing: `shogun remove` reports them and the desktop app ignores them. Cleanup keeps its own batched, opt-in stops.

`resolveWorktreeTarget(target, worktrees, config, canonicalPath)` is how `shogun remove` finds its worktree: an existing path of a listed worktree, else a branch matching exactly one worktree's branch, else a task id mapped with `worktreePathFor`. No match is `WorktreeTargetNotFound`, several are `AmbiguousWorktreeTarget` with their paths, and the main worktree is `CannotRemoveMainWorktree`. `findWorktreeTarget` does the same search without refusing the main worktree, for `shogun open`.

### Resource pool use cases

- `ResolveResourceLimitsUseCase` turns the stored `ResourceSettings` into `ResourceLimits` with the profile table, using the `MachineCapacity` port (`totalMemoryBytes()` and `cores()`, implemented over the JVM's `OperatingSystemMXBean`). A missing or unreadable setting means Automatic.
- `ReadResourcePoolUseCase` builds a `PoolUsage` for emulators and simulators from every reservation on the machine (mapping each admin directory to its worktree through its `gitdir` file) and the device states, and for Gradle daemons from `ListGradleDaemonsUseCase`.
- `AcquireWorktreeResourceUseCase` is what the emulator and simulator start use cases call first. Under the reservation lock: a resource the worktree already holds only has its last use and lease updated; with room in the pool it reserves a port or creates the simulator; at the limit it releases the holder with the lowest `releaseRank` (stopping a running emulator or shutting down a booted simulator first, deleting the simulator, and recording a `ReleaseRecord` on the victim) and reserves; with no releasable holder it fails with `ResourcePoolFull`. Ports are chosen in this order: the worktree's previous port when free, a port not released within the lease duration, any free port. It returns the `PoolRelease`s it made and rewrites the declared environment file of the worktree and of each released one. `StartWorktreeEmulatorUseCase` and `StartWorktreeSimulatorUseCase` return a `DeviceStart` with the device and that result.
- Waiting for capacity: before releasing anything, the acquisition reads the pool's `DeviceWaitQueue` (dropping tickets whose process `ProcessLiveness` reports dead) and fails with `ResourcePoolFull` carrying the number of waiters when another worktree's ticket is ahead of its own (or of any, without a ticket); a worktree that holds its device is never blocked. The loop is `WaitInLine`, shared with the desktop turn and keyed by a `WaitQueueName` (`emulator`, `ios-simulator`, `desktop`). Given a `DeviceWait`, it first tries without a ticket and, on `ResourcePoolFull`, adds a `WaitTicket` and retries, sleeping until the earliest of 10 seconds, one second after the next lease expiry or the deadline, and reporting a `DeviceWaitProgress` on each try. It always removes its ticket (also when cancelled), fails with `DeviceWaitTimedOut` once the timeout passes, and returns `waitedSeconds` in `ResourceAcquisition` (`null` without a wait). The boot happens after the wait, outside its deadline.
- `ReadAgentActivityUseCase` gives the state of a worktree's agent from the live sessions in `AgentSessionStore` (working over blocked over done over idle; `null` without one), `RenewWorktreeLeasesUseCase` renews the leases of the devices a worktree holds without touching their last use or acquiring anything, and `ReadReleaseRecordUseCase` reads a worktree's release record.
- `TrimGradleDaemonsUseCase` stops idle Gradle daemons, longest idle first, through `StopGradleDaemonsUseCase` until the count is within the limit; busy daemons and daemons of unknown state are never stopped for it.

### `CloseHerdrWorkspacesUseCase`

Closes the Herdr workspace of removed worktrees, in two steps around the removal. `openWorkspaces(paths)` makes one `worktree list` call for the base repository, before anything is removed (afterwards Git no longer lists the worktree), and returns the workspace id of each given path that Herdr has open, matching paths with `normalizePath`. Any failure (Herdr not installed or not running, an unexpected response) gives an empty map: if ShogunAi can't see Herdr there's nothing to close and nothing to report. `close(workspaces, removedPaths)` closes only the workspaces of the paths that were actually removed (pruned missing worktrees included) and returns a `CleanupFailure` for each close that failed instead of throwing. `RemoveWorktreeUseCase` and `CleanUpWorktreesUseCase` take it as an optional parameter and `null` leaves their behaviour unchanged; a Git failure closes nothing.

### `CloseOrcaTerminalsUseCase`

The same two steps for Orca. `withTerminals(paths)` returns the paths with Orca terminals, looked up only when Orca is installed and its runtime reachable (it never starts Orca); any failure counts as no terminals. `close(withTerminals, removedPaths)` runs `closeTerminals` for each removed path that had terminals and returns a `CleanupFailure` per failing close, which the callers show as a warning (`WorktreeRemoval.orcaFailure`, `CleanupReport.orcaFailures`). When `git worktree remove` fails because the folder is in use, the removal also closes that worktree's Orca terminals (`closeOne`) before its single retry. It's wired into the same removal paths as Herdr's.

### `CopyWorktreeLaunchCommandUseCase`

Copies `cd <worktree path> && <agent command>` to the clipboard, so the user pastes it in the terminal of their choice. The path is quoted with `ShellQuoting.posix` (`ShellQuoting.windows` on Windows), so no character in it is interpreted by the shell it's pasted into, and the command is the given agent's `resolvedCommand()`, the project's default when none is given.

### `OpenWorktreeInTerminalUseCase`

Opens a worktree in the chosen terminal with its environment and an agent (the default when none is given). It picks the `TerminalLauncher` for that `TerminalId` (`TerminalUnsupported` when there's none), resolves the worktree's environment as `shogun env` does, and builds one launch line: for POSIX terminals `cd <path> && export NAME=<value> … && <agent command>`, every value quoted with `ShellQuoting.posix`; for Windows Terminal the PowerShell equivalent, `Set-Location -LiteralPath <path>; $env:NAME = <value>; …; <agent command>`. The launcher receives the line and the parts it's built from, since tmux sets the variables and folder itself and only types the agent command.

### `OpenWorktreeInHerdrUseCase`

Opens a worktree in Herdr with its environment and an agent (the project's default unless another one is given). It asks Herdr to open the worktree for the project's base repository, labelled with its folder name. If Herdr already had it open and one of its panes has a live agent, it focuses that agent's pane and returns `FOCUSED` without typing anything. Otherwise it resolves the worktree's environment (the same as `shogun env`, provider variables such as `ANDROID_SERIAL` included, reserving ports if needed). In an already open workspace without an agent it always splits the root pane into a new one whose shell starts in the worktree with those variables, keeps every other pane and returns `STARTED_AGENT`. In a fresh workspace, when the environment isn't empty, it splits the root pane the same way and closes the root pane, so the workspace keeps a single pane, and returns `OPENED`. Either way the agent's `resolvedCommand()` is run through `HerdrAgentLauncher`, which types it and polls `listPanes` for that workspace every 500 ms until the pane has a live agent, failing with `HerdrAgentNotStarted` after 10 seconds (the pane is left open). The launcher also does the focusing, so `OpenMultirepoAgentInHerdrUseCase` follows the same rules for the multirepo agent's plain workspace (a new pane split from the one found in the multirepo folder, without extra variables), and `OpenGroupInHerdrUseCase` runs this use case for each worktree of a group. The use cases pass the launcher a session name (`worktreeSessionName`, `groupMemberSessionName` or `multirepoSessionName`) and the folder. When the launcher has a `ClaudeSessionRegistry` and `namedClaudeCommand` accepts the command, it makes the name unique against the live sessions (a pid counts as live when `isClaudeProcessAlive`, by default a running `ProcessHandle` whose command ends in `claude` or `claude.exe`), types the named command and, once the agent is detected, polls the registry for up to 10 seconds for a live session with that name in that folder; the confirmed name goes into `HerdrOpening.sessionName`, which stays `null` on timeout without failing the open. When it focuses a live `claude` agent, it reports the live session in the folder with the expected name, or else the most recently started one. When Herdr already reports an agent in the worktree, the desktop app focuses that agent's pane with `HerdrClient.focusAgent` instead of calling this use case. `shogun open <target> --herdr` calls it too, without that agent check.

The session naming (`freeName`, `named`, `awaitSession`, `focusedSessionName`) lives in `ClaudeSessionNaming`, shared by `HerdrAgentLauncher` and Orca.

### `OpenWorktreeInOrcaUseCase`

Opens a worktree in Orca with its environment and an agent. When `status()` says Orca isn't running it calls `start()` and fails with `OrcaNotStarted` if Orca still isn't running. It finds the base repository among `repos()` by canonical path, or adds it, and calls `showExternalWorktrees` for that repository only when `worktrees(repoId)` doesn't list the worktree (checking the listing rather than the setting covers `inherit`, whose effective value the CLI doesn't expose). It then looks for a live agent: the first of `terminals(path)` whose `terminalAgent` is set gets `switchTo` and the result is `FOCUSED`, with the live Claude session's name. Otherwise it resolves the environment, names the agent command as Herdr does, wraps it in `EnvironmentCommand.posix` (`env 'NAME=value' … <command>`) or, on Windows, `EnvironmentCommand.powershell` (the assignments `OpenWorktreeInTerminalUseCase` uses), creates a focused terminal titled with the session or agent name, and polls `terminalAgent` every 500 ms for up to 10 seconds, failing with `OrcaAgentNotStarted` (the terminal is left open). After focusing or starting the agent it calls `bringToFront()`, ignoring a failure, since neither `terminal switch` nor `terminal create --focus` raises Orca's window. The confirmed session name, awaited as with Herdr, goes into `OrcaOpening.sessionName`. The desktop app's "Open in Orca" and `shogun open <target> --orca` call it. Starting Orca, registering a repository, focusing a live agent and starting one live in `OrcaAgentLauncher`, shared with the multirepo use cases below.

`OpenGroupInOrcaUseCase` fails once with `OrcaNotInstalled` when Orca isn't installed, and otherwise runs `OpenWorktreeInOrcaUseCase` for each worktree of a group, one after another, with its project's agent and `groupMemberSessionName`, returning a `GroupOrcaOpening` (alias, path and result) per worktree. `OpenMultirepoAgentInOrcaUseCase` starts Orca if needed, takes the `agentsByWorktree()` rows whose canonical path is the multirepo folder (a folder workspace, a folder added with "Open as Folder" or a Git main worktree), preferring one with agents and then Orca's order, and focuses its live agent or starts the multirepo agent there with `multirepoSessionName` and no extra variables. With no such row, a folder that is a Git repository is registered and its main worktree used; any other folder fails with `OrcaMultirepoFolderNotOpen` without touching Orca.

### Base branches

The branch a new branch starts from is recorded in the repository's Git config as `branch.<branch>.gh-merge-base`, the key `gh pr create` reads as its default base. Git keeps it in the common config shared by every worktree, moves it on `git branch -m` and removes it on `git branch -D`, so ShogunAi never cleans it up.

- `ResolveBaseBranchUseCase` turns a ref into that branch name with `git rev-parse --verify --quiet --symbolic-full-name <ref>`, run where the start point is resolved: `refs/heads/<b>` and `refs/remotes/<remote>/<b>` give `<b>`, and a tag, a commit, a detached `HEAD`, a remote's `HEAD` or an unknown ref give `null`, so nothing is recorded. The CLI calls it with the same ref as `ResolveStartPointUseCase`, the desktop app with its "Start from" ref or `HEAD`, and `CreateGroupUseCase` passes `MainBranch.name` directly. `CreateWorktreeFromBranchUseCase` records nothing.
- `ReadBaseBranchesUseCase` reads every record with one `git config --get-regexp`, returning a map from branch to base (exit code 1 without output is an empty map).
- `SetBaseBranchUseCase` validates a branch with `ResolveBaseBranchUseCase` in the base repository (`NotABranch` otherwise) and writes the same key.

### `ResolveStartPointUseCase`

Resolves the ref a new branch starts from to a commit id with `git rev-parse --verify --quiet <ref>^{commit}`, run in the caller's directory, so `HEAD` means the worktree the command runs in and not the base repository. An unknown ref fails with `StartPointNotFound` before anything is created. Used by `shogun new`, the `WorktreeCreate` hook and the desktop app's "Start from" selector, which resolves its ref in the base repository after the optional fetch.

### `ResolveRepositoryUseCase`

Finds the base repository (main worktree) that contains a directory, for front ends that start from a directory instead of a project, such as the CLI. It runs `git rev-parse --path-format=absolute --git-common-dir --is-bare-repository` there: the common Git folder is shared by every worktree, so when it's a `.git` folder its parent is the main worktree whether the directory is the main worktree, a subfolder or a linked worktree. Otherwise the main worktree is the common folder's `core.worktree` when set (a submodule), or the first entry of `git worktree list --porcelain` (a repository with a separate Git folder). A bare repository is `BareRepositoryNotSupported`; anything that isn't a repository is `RepositoryNotFound`.

### `DeleteMergedBranchUseCase`

Deletes a local branch only when it is contained in the project's main branch, resolved with `ResolveMainBranchUseCase` (`git merge-base --is-ancestor <branch> <main ref>`, then `git branch -D -- <branch>`, the same rule cleanup uses), and returns whether it was deleted instead of failing. A branch merged only into its own upstream is kept, and a name starting with `-` is `InvalidRef`. Used by the CLI's Claude Code `WorktreeRemove` and Herdr hooks, where a kept branch is expected rather than an error.

### `ProvisionWorktreeUseCase`

Applies the same files as creation (local files and the repository's provisioning, see `CreateWorktreeUseCase`) to an **existing** linked worktree, e.g. one created by another tool. The path must be one of the repository's worktrees (from `ListWorktreesUseCase`) and not the main one (`NotAWorktree`). Files are copied (or linked) over existing ones, the approval is settled and the environment prepared the same way (reserving ports for a worktree another tool created), the approved post-creation commands run, and it returns a `ProvisionResult`: each `ProvisionedFile`, the skipped commands and the skipped variables. Unlike creation, a failed copy or environment doesn't remove anything: the worktree isn't ShogunAi's to delete. Creation and provisioning share the same copy and command functions.

### `CheckProvisionedFilesUseCase`

Compares a linked worktree with what provisioning would give it now, without changing anything: each file that should be copied and is missing or differs from the base repository's (`hasSameContent`), and each file that should be linked and is missing or isn't a link to the base repository's copy, as a `ProvisionDrift`. `shogun provision --check` and the desktop app's worktree detail use it.

### Repository trust

`.shogunai.json` is committed, so whoever controls a branch controls what it declares. Its post-creation commands only run, and its declared variables and environment file only apply, once the user approved that exact subject on this machine (`RepositoryTrust`, keyed by the repository's real path). Creation and provisioning check the store first and ask `RepositoryApproval` only when the subject isn't approved, once for commands and variables, before preparing the environment; `APPROVE` records the approval, `SKIP` returns the commands and variable names as skipped. A changed subject (after a pull, say) asks again.

Declared variables are gated in one place, `ResolveWorktreeEnvironmentUseCase`, which receives the trust store: while the subject isn't approved it resolves as if nothing were declared (no declared ports, variables or precedence over providers) and reports the declared names in `awaitingApproval`, with `declarationApproved = false`, which `WriteEnvironmentFileUseCase` honors by writing nothing. Every consumer (the agent's `SessionStart` hook, `shogun env`, terminal and Herdr launches, the desktop detail, the device use cases) goes through it, so none can deliver them unapproved. Saving the environment from the desktop app's settings approves the saved subject when the repository was approved before the save or declares no post-creation commands, and the settings can approve or revoke it explicitly.

### `RepairWorktreesUseCase`

Re-links the base repository and its worktrees after they were moved on disk. Git stores absolute paths in both directions (`<worktree>/.git` points to the base repository and `.git/worktrees/<name>/gitdir` points back), so a move breaks them. It runs `git worktree repair` from the (new) base repository; worktrees that no longer exist at their old path but do exist at the same relative location under the new `worktreesRoot` are passed as arguments, so Git fixes both directions. The UI runs it after saving a project whose base repository path or worktrees root changed.

### `ResolveWorktreeEnvironmentUseCase`

Resolves a worktree's environment from the base repository's declaration, when approved (see [Repository trust](#repository-trust)), and the providers it declares. Each needed port is a `PortRequest` (a key, candidate ports, ports that must also be free, and the error when none is left): declared `${port}` keys ask for 20000–29999 (`declaredPortRequests`); providers add theirs through `EnvironmentContribution`s, given by the injected `ProviderContributions` factory (`DEFAULT` knows the emulator; the app and the CLI pass one that also builds the iOS simulator's, which needs the `AppleSimulators` port and the project's machine settings), together with the variables they derive from their ports. With nothing declared and no contributing provider it returns an empty `WorktreeEnvironment` without running any command. Otherwise it finds the worktree's administrative and common Git directories (`git rev-parse --path-format=absolute --git-dir --git-common-dir` in the worktree) and, while holding the machine's lock:

1. keeps the worktree's stored ports whose keys are still requested, releasing the rest;
2. reserves each missing key at the first candidate that, together with its extra ports, no worktree on the machine has reserved and `PortProbe` reports free (otherwise the request's error, e.g. `NoFreePort`);
3. writes the reservations only if they changed.

Contributions can also ask for resources (`ResourceRequest`: a key, and how to check, create and discard one). With reservation, a stored resource that still `exists` is kept; otherwise one is created outside the lock (creating a simulator runs `xcrun` for about a second) and recorded under it, and if another process recorded one in the meantime, the new one is discarded and the recorded one used. Without reservation resources are only read. Provider variables come first and declared `env` variables override a provider variable with the same name. A reserved port is never checked again, because the worktree's own server is expected to listen on it. With `reserve = false` (used to show ports without side effects) it only reads the stored ports, without locking or writing, and returns no variables until every port is reserved. `formatEnvironment` renders the variables as `sh` (`export NAME='value'`, quoted with `ShellQuoting.posix`, safe for `eval`), `dotenv` (`NAME=value`, double-quoted unless the value is plain, with `\`, `"`, newlines, `$` and backticks escaped, so sourcing the file never runs a command) or `json`.

Finding a worktree's Git directories is the only part that runs a process; when it's given a `WorktreeEnvironmentCache` (the desktop app does), it remembers them per worktree path until the cache is invalidated, which the app does whenever a project reloads its worktree list. The CLI uses no cache.

### `PrepareWorktreeEnvironmentUseCase` / `WriteEnvironmentFileUseCase`

`PrepareWorktreeEnvironmentUseCase` is the `WorktreeEnvironmentPreparer` that creation and provisioning run once the files are copied: it resolves the worktree's environment with reservation and, when the declaration has an `envFile`, calls `WriteEnvironmentFileUseCase`. That one writes the variables in dotenv format at the declared path, refusing a path that resolves outside the worktree (`EnvironmentFileOutsideWorktree`) or a file Git tracks (`git ls-files --error-unmatch`, `EnvironmentFileTracked`), and reporting write errors as `EnvironmentFileWriteFailed`. `WorktreeEnvironmentPreparer.NONE` does nothing, for tests that don't exercise the environment.

### iOS simulator use cases

The provider's contribution comes from `MachineProviderContributions`, which the app and the CLI inject into `ResolveWorktreeEnvironmentUseCase`: the emulator's, plus, on macOS for repositories that declare `ios-simulator`, a resource request whose `create` picks the device type and runtime with `chooseSimulator` (this operation, the project's `SimulatorSettings`, the repository's preferences; `NoIosRuntime` if none) and runs `simctl create` with `worktreeSimulatorName`, whose `exists` checks the device list, and whose `discard` deletes it. `requesting(settings)` returns the same factory with this operation's device type and runtime.

- `StartWorktreeSimulatorUseCase(config, worktreePath, requested)` resolves the environment through a creating resolver (so the simulator exists), boots it unless it's already booted, and polls `devices()` every second until it's booted; after 120 s it fails with `SimulatorBootTimeout` and leaves it booting.
- `StopWorktreeSimulatorUseCase` reads the recorded UDID without creating and shuts the simulator down if it's running; with none recorded there's nothing to stop.
- `ReadSimulatorStatesUseCase` maps each worktree with a recorded, existing simulator to its UDID, name and state, from one `devices()` call.
- `DeleteSimulatorsUseCase` deletes the given simulators that still exist (shutting them down first), and `orphans(config)` lists the simulators named with the `ShogunAi · ` prefix whose UDID no worktree on the machine has recorded (`recordedOnMachine`). Simulators without the prefix are never touched.

All of them fail with `IosSimulatorProviderNotDeclared` or `IosSimulatorsNotSupported` (or return nothing, for the states and orphans) when the repository doesn't declare the provider or the host isn't macOS, before running `xcrun`.

### Emulator use cases

All three fail with `EmulatorProviderNotDeclared` (or return nothing, for the states) when the repository doesn't declare the emulator provider, before touching the SDK.

- `StartWorktreeEmulatorUseCase(config, worktreePath, avd?, noWindow?)` resolves the worktree's environment (reserving its emulator port if needed), returns at once if its serial is already running, otherwise starts the AVD from `chooseAvd` (`NoAvdAvailable` if none) with the window setting from the call or the project, unless it's already booting, and polls `deviceStates` every 2 s until it's running. If the emulator process it started exits meanwhile, it fails at once with `EmulatorExitedEarly` and the end of the emulator's log; after 180 s it fails with `EmulatorBootTimeout` and leaves the emulator booting. The pause is injected, so tests don't wait.
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

- `ListDerivedDataUseCase(worktrees, includeCompilationCache, measure)` attributes every folder from `DerivedDataSource`: a workspace that doesn't exist is `UnavailableVolume` if its volume isn't mounted, otherwise `Orphaned`; an existing one belongs to the worktree with the longest path containing it (`owningWorktree`, the same rule as running agents and Gradle daemons), otherwise to `OtherProject`. `measure` says which folders get their size: `NONE`, `ORPHANS` or `ALL`. Orphans are machine-wide: once its project is gone, a folder can't be used by anything, whichever tool created it.
- `DeleteOrphanedDerivedDataUseCase` lists the folders again, keeps those still orphaned, checks each workspace once more right before deleting it (a project restored in the meantime keeps its folder), measures each orphan that has no size yet so `bytesFreed` is right, and returns a `DerivedDataDeletion`; one failure doesn't stop the rest. A removed worktree's folder is orphaned by definition, so a cleanup only runs this use case after the removals.
- `PrepareCocoaPodsUseCase` (a `CocoaPodsPreparer`) runs from `PrepareWorktreeEnvironmentUseCase` after the environment file, so every creation and provisioning flow gets it, and returns a `PodsPreparation` (`cloned`, `installNeeded` with a `PodsInstallReason`, `error`) carried in `PreparedWorktree` to `Worktree.pods` and `ProvisionResult.pods`. With `XcodeProvider.cocoaPods` on macOS, it lists the tracked `Podfile.lock` files with `git ls-files`, and for each folder without `Pods` clones the base's `Pods` with `cp -c -R` when its `Manifest.lock` has the same content; it never throws, so pods can't roll a worktree back.
- `xcodeCompilationCacheContribution` is the provider's environment contribution when `XcodeProvider.compilationCache` is set, on macOS (`MachineProviderContributions`): a resource `xcode-compilation-cache` whose `create` writes `~/.shogunai/xcode/compilation-cache.xcconfig` (`COMPILATION_CACHE_ENABLE_CACHING` and Swift and Clang prefix mapping, five `KEY = YES` lines) and whose `exists` checks the file holds that content, plus the variable `XCODE_XCCONFIG_FILE=<path>`. Being a resource, it's only written when resolving with reservation and is re-created if deleted; the file is the same for every repository on the machine. An `XCODE_XCCONFIG_FILE` declared in `env` wins.

### Update check use cases

- `CheckForUpdateUseCase(mode)` reads the preferences and, in `AUTOMATIC` mode, returns `NotDue` when the automatic check is off or the running version is a development build (`0.0.0`). Otherwise it asks `ReleaseSource` for the published releases and takes the highest version as the latest (an empty list is `UpdateCheckFailed`): a version not newer than the running one, or, in automatic mode, not newer than the skipped version, is `UpToDate`; a newer one is `Available` with the install channel, its `UpdateAction` and the notes of every version newer than the running one, newest first. A failed read is returned as the failure; the desktop app ignores it in automatic mode and shows it in manual mode.
- `DetectInstallChannelUseCase` tells how the running app was installed, and is only called when a newer version exists: on macOS `HOMEBREW` when a `shogunai` folder exists in `/opt/homebrew/Caskroom` or `/usr/local/Caskroom`, else `DMG`; on Windows `SCOOP` when the app runs from `\scoop\apps\shogunai\`, else `WINGET` when `winget list --id LuxionServer.ShogunAi --exact` lists it (10-second limit), else `MSI`; on Linux `DEB` when the app lives under `/opt/shogunai`, else `UNKNOWN`.
