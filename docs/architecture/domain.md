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
| `providers` | The `providers` object of `.shogunai.json`: the providers the repository turns on, each keyed by its id with an object of options. Today only `gradle` (`GradleProvider`, no options yet), which makes the [Gradle daemons](#gradle-daemons) visible for the repository. Unknown ids are ignored. |
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

### Gradle daemons

What the Gradle provider observes on the machine (`GradleDaemon.kt`):

- `DaemonProcess` — a running daemon: `pid`, `kind` (`DaemonKind.GRADLE` or `KOTLIN`), `gradleVersion` (Gradle daemons only), `residentMemoryBytes`, `lastBuildDirectory` (the directory of the last build a Gradle daemon received) and `state`.
- `DaemonState` — `Busy`, `Idle(since)`, `Unknown` (a Gradle daemon whose state can't be read) or `Untracked` (Kotlin daemons, which don't record one).
- `GradleDaemon` — a `DaemonProcess` with its `owner` in a repository: `DaemonOwner.Worktree(path)`, `RemovedWorktree` (its last build directory no longer exists) or `OtherProject`; `null` for Kotlin daemons, daemons without a known build directory, or outside a repository.
- `DaemonStopReport` — the outcome of a stop: `stopped`, `kept` (each with a `DaemonKeptReason`: `BUSY`, `STATE_UNKNOWN` or `BUILD_RUNNING`), `notDaemons` (requested pids that aren't daemons) and `failures`.

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
}
```

Abstracts the filesystem so that copying secrets and measuring worktrees is unit-testable without touching disk. `copy` creates the destination's missing parent folders. `directorySize` adds up the regular files under `path`, skipping the folders in `excluding`. `modificationStamps` returns every file and folder under `path` (or `path` itself if it's a file) with its last-modified time, and nothing if it doesn't exist. `readProperties` reads a Java properties file (such as `gradle.properties`), empty if it's missing or unreadable.

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
    fun reservedPortsOfRepository(commonDirectory: String): Set<Int>
    fun <T> withLock(commonDirectory: String, block: () -> T): T
}

interface PortProbe {
    fun isFree(port: Int): Boolean
}
```

`EnvironmentStateStore` keeps each worktree's reserved ports in Git's administrative directory for that worktree (`.git/worktrees/<id>/` for a linked one, `.git/` for the main one), lists every reservation of the repository, and runs a block while holding the repository's reservation lock. `PortProbe` tells whether a port is free on the machine.

### `GradleDaemonSource`

```kotlin
interface GradleDaemonSource {
    suspend fun list(): List<DaemonProcess>
}
```

Lists the Gradle and Kotlin daemons running on the machine with their state, or fails with `ProcessListingFailed` when processes can't be listed.

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

`CleanUpWorktreesUseCase` takes those candidates, the `MainBranch` and whether to delete branches, and returns a `CleanupReport` (`removed`, `keptBranches`, `failures`). Each existing worktree is removed with `git worktree remove` (never `--force`), and missing ones with a single `git worktree prune`. A failing step is recorded and the rest continue. Then the branches of every removed worktree, pruned ones included, are deleted only if `git merge-base --is-ancestor <branch> <main ref>` still succeeds, and then with `git branch -D`: `-d` checks against the base repository's `HEAD`, which in a git-flow repository may not contain a branch merged into `develop`.

### Worktree overview

- `classifyOrigin(worktree, config)` returns the `WorktreeOrigin` of a non-main worktree: `CLAUDE_CODE` if its path is inside a `.claude/worktrees/` folder (checked first, since that folder can be inside the worktrees root), `SHOGUNAI` if it's inside `config.worktreesRoot`, `OTHER` otherwise. Paths are compared with `/` separators and without trailing slashes, so Windows paths work too.
- `isInactive(worktree, status, agents, now)` is true for a non-main worktree with no uncommitted changes, no running agents and a last commit older than `INACTIVITY_THRESHOLD` (14 days).
- `DetectRunningAgentsUseCase` returns the `RunningAgent`s per worktree path. It runs `lsof -d cwd -F pcn` once to get every process's working directory, assigns each process to the worktree with the longest path containing it (so a process in `.claude/worktrees/<name>` isn't attributed to the main worktree), and runs `ps -o pid=,comm=` for those processes. Their executable's file name is matched against the known agent CLIs (`claude`, `codex`, `gemini`, `cursor-agent`, `opencode`, `aider`, `copilot`) and the project's own agent command. The `lsof` command name isn't used because Claude Code renames its process to its version. If `lsof` or `ps` fail (e.g. on Windows), it fails with `WorktreeError.ProcessListingFailed`.
- `MeasureWorktreeSizeUseCase` returns a worktree's size with `FileManager.directorySize`, on the IO dispatcher, excluding every other listed worktree nested inside it. It returns `null` for a missing folder.
- `ReadGitMetadataFingerprintUseCase` returns a fingerprint of the main repository's Git metadata: the sorted (path relative to `.git`, last-modified time) pairs of `HEAD`, `index`, `packed-refs`, `FETCH_HEAD` and everything under `refs` and `worktrees`, read on the IO dispatcher. Commits, checkouts, locks, fetches and worktrees added or removed by any tool change it; `objects` isn't included. It's empty when `<baseRepositoryPath>/.git` isn't a directory.

### `RemoveWorktreeUseCase`

Runs `git worktree remove [--force] <path>` and, optionally, `git branch -d <branch>` to delete the local branch (safe delete, not `-D`).

!!! note
    Git doesn't allow removing a worktree from inside itself, which is another reason every command runs from the base repository.

### `CopyWorktreeLaunchCommandUseCase`

Copies `cd "<worktree path>" && <agent command>` to the clipboard, with the agent command from `AgentLaunchConfig.resolvedCommand()`, so the user pastes it in the terminal of their choice.

### `ResolveRepositoryUseCase`

Finds the base repository (main worktree) that contains a directory, for front ends that start from a directory instead of a project, such as the CLI. It runs `git rev-parse --path-format=absolute --git-common-dir` there: the common `.git` folder is shared by every worktree, so its parent is the main worktree whether the directory is the main worktree, a subfolder or a linked worktree. Anything else (not a repository, a bare repository) is `RepositoryNotFound`.

### `DeleteMergedBranchUseCase`

Deletes a local branch with `git branch -d`, which Git refuses when the branch isn't merged, and returns whether it was deleted instead of failing. Used by the CLI's `WorktreeRemove` hook, where a kept branch is expected rather than an error.

### `ProvisionWorktreeUseCase`

Applies the same files as creation (local files and the repository's provisioning, see `CreateWorktreeUseCase`) to an **existing** linked worktree, e.g. one created by another tool. The path must be one of the repository's worktrees (from `ListWorktreesUseCase`) and not the main one (`NotAWorktree`). Files are copied over existing ones, the environment is prepared the same way (reserving ports for a worktree another tool created), and it returns the copied relative paths. Unlike creation, a failed copy or environment doesn't remove anything: the worktree isn't ShogunAi's to delete. Creation and provisioning share the same copy function.

### `RepairWorktreesUseCase`

Re-links the base repository and its worktrees after they were moved on disk. Git stores absolute paths in both directions (`<worktree>/.git` points to the base repository and `.git/worktrees/<name>/gitdir` points back), so a move breaks them. It runs `git worktree repair` from the (new) base repository; worktrees that no longer exist at their old path but do exist at the same relative location under the new `worktreesRoot` are passed as arguments, so Git fixes both directions. The UI runs it after saving a project whose base repository path or worktrees root changed.

### `ResolveWorktreeEnvironmentUseCase`

Resolves a worktree's environment from the base repository's declaration. An empty declaration returns an empty `WorktreeEnvironment` without running any command. Otherwise it finds the worktree's administrative and common Git directories (`git rev-parse --path-format=absolute --git-dir --git-common-dir` in the worktree) and, while holding the repository's lock:

1. keeps the worktree's stored ports whose keys the declaration still uses, releasing the rest;
2. reserves each missing key at the lowest port from 20000 to 29999 that no worktree of the repository has reserved and `PortProbe` reports free (`NoFreePort` if none is);
3. writes the reservations only if they changed.

A reserved port is never checked again, because the worktree's own server is expected to listen on it. With `reserve = false` (used to show ports without side effects) it only reads the stored ports, without locking or writing, and returns no variables until every port is reserved. `formatEnvironment` renders the variables as `sh` (`export NAME='value'`, single quotes with `'\''` escaping, safe for `eval`), `dotenv` (`NAME=value`, double-quoted and escaped unless the value is plain) or `json`.

### Gradle daemon use cases

Used when the repository declares the Gradle provider (and by `shogun gradle` anywhere):

- `ListGradleDaemonsUseCase` lists the daemons from `GradleDaemonSource` and, given the repository's worktrees, attributes each Gradle daemon by its last build directory: `RemovedWorktree` if that folder no longer exists, otherwise the worktree with the longest path containing it (the same rule as running agents, so nested worktrees win), otherwise `OtherProject`.
- `StopGradleDaemonsUseCase` stops the given pids, or every daemon. It lists the daemons again first, so the decision uses their current state: a Gradle daemon is stopped only if `Idle`, a Kotlin daemon only if no Gradle daemon is `Busy` or `Unknown`. Stopping runs `kill <pid>` (SIGTERM, so the JVM runs Gradle's shutdown hooks). One failure doesn't stop the rest.
- `idleDaemonsOfRemovedWorktrees` picks, from a listing taken before a cleanup, the idle Gradle daemons whose last build was in one of the removed worktrees. The cleanup passes them to `StopGradleDaemonsUseCase`, whose fresh check still keeps a daemon that became busy.
- `CheckGradleBuildCacheUseCase` tells whether `org.gradle.caching=true` is set in the repository's `gradle.properties` or in `$GRADLE_USER_HOME/gradle.properties`. With Gradle's local build cache on, a new worktree reuses task outputs built in other worktrees, because cache keys don't depend on the checkout's path.
