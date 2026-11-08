# Design

## Context

`coverage-kover` set `core`'s branch rule at its measured figure and left the rest to this change. On `develop` at 24b64c40, Kover 0.9.11 on macOS counts 7,344 branches in `core`'s production code, 1,519 of them missed (79.3%), spread over 167 source files. About 285 sit on class headers: the code kotlinx.serialization generates for `@Serializable` classes (the deserializing constructor checks which fields were present, `write$Self` decides whether to write a field equal to its default) and the bitmasks of constructors with default arguments. The other 1,234 are in written code: `?.` and `?:` chains, `if`s on a command's exit code or output, `takeIf`, `when` arms, short-circuited `&&` and `||`.

Kover's IntelliJ agent already leaves out data class `equals`, `hashCode` and `toString`, the `$serializer` classes and exhaustive `when`'s hidden `else`, but it can only exclude whole classes or annotated functions, not a synthetic method inside a class: excluding the generated serialization code would mean excluding every `@Serializable` class, including the logic some of them hold (`ResourceSettings.quotaMode`, for example).

## Goals / Non-Goals

**Goals:**

- Every branch of `core` that some input can reach is covered, and the rule fails on any new missed branch.
- Every new test asserts the behavior its branch decides (a value, an error, a command sent through a port), not just that the code ran.

**Non-Goals:**

- Branches of `cli` and the desktop app: other changes (`coverage-cli-branches`, `coverage-desktop`) own them.
- Refactoring beyond removing what is unreachable.
- Changing how coverage is measured or verified in CI.

## Decisions

### No new exclusions

The generated serialization branches stay counted and are covered by tests (all but eighteen that no input reaches, see "The final rule counts missed branches"), because the only exclusion Kover offers would hide written code too, and because those branches decide real behavior: a settings file written by an older version lacks newer fields and must read as their defaults; a file missing a required field must be rejected as unreadable. Rejected: excluding `@Serializable` classes, or moving their logic out so they could be excluded, which bends the production code around the tool.

### Serialization branches are covered through the file formats

Each `@Serializable` class is exercised through the adapter that reads and writes it (`JsonSettingsFile`, `JsonMultirepoSource`, `NioEnvironmentStateStore`…) where one exists, with three kinds of input: a document with every optional field absent (defaults), one with every field set to a non-default value (round trip), and, for classes with required fields, one missing a required field (rejected with the adapter's typed error). Classes used only in memory or only through a model's own JSON helpers are tested with `Json` directly in their model test. Writing is covered by encoding a default instance and instances with some fields changed, with `encodeDefaults` both off and on: the generated serializer is the class's own contract, and some adapters write defaults while others don't, so each class's test exercises both regardless of which adapter uses it. Constructors with default arguments are called with complementary subsets of arguments, so each default is both taken and overridden.

### Unreachable branches are removed, not tested around

When a branch can't be taken by any input (a `?.` on a value the type flow already proves non-null, an `?:` after a call that never returns `null`, a `takeIf` whose condition is always true at its only call site, a default argument every caller passes), the production code is simplified so the branch disappears, keeping behavior identical. Each removal is recorded below with its reason. Rejected: reflection or test-only seams to force impossible states; a seam is only added, as `coverage-kover` did, for a real failure a test can't otherwise provoke (an I/O error, a lock that can't be taken), with today's behavior as its default.

### Tests live next to the existing ones

New cases go into the existing test class of each production class when there is one, using the fakes in `core`'s test fixtures; a fake gains a configurable answer when a branch needs one it can't give today. Adapters over the file system use temporary folders; adapters over external tools (`orca`, `herdr`, `xcrun simctl`, `adb`, `docker`, `gradle`) use `FakeShellCommandExecutor` answers, never the real tool. Code that depends on the operating system is covered through its `osName` parameter.

### The floor rises as it goes

The work goes package by package (models, then use cases, then infrastructure). After each group, `core`'s rule in `core/build.gradle.kts` is raised to the whole percentage reached, so a partial state is never left with slack.

### The final rule counts missed branches

Eighteen branches can't be covered by any input. kotlinx.serialization gives every `@Serializable` class a deserializing constructor that checks the required fields it didn't see; when every field of the class has a default, that check is `(seen & 0) != 0`, which is never true. Eighteen classes in `core` have only optional fields: `AgentLaunchConfig`, `EmulatorSettings`, `SimulatorSettings`, `MultirepoDeclaration`, `ResourceSettings`, `ResourceLimits`, `SidebarOrder` and, in the adapters, `JsonSettingsFile`'s `Settings`, `GroupingSettings` and `SortingSettings`, `JsonRepositoryTrust.Store`, `FileClaudeSessionRegistry.SessionFile`, `NioDeviceWaitQueue.QueueFile`, `JsonDerivedDataSizeStore.Store`, `JsonOrcaWorkspaceStore.Store`, `JsonComposeProjectRegistry.Store`, and `JsonMultirepoSource`'s `Registry` and `LocalSettings`. Every other branch of those classes is covered.

So `core`'s branch rule is a maximum of 18 missed branches (`AggregationType.MISSED_COUNT`) instead of a percentage. Any new branch without a test fails the verification, as a rule at 100% would. A change that adds another all-optional `@Serializable` class raises the maximum by one and says so in its design. Rejected: making a field required in each class (it changes the file formats); a floor of 99% (it would leave room for about 50 untested branches); excluding those classes (it would stop measuring their `write$Self` branches, which are covered).

### Production changes

Each production change, the branch it removes and why no input reaches it. None changes behavior unless it says so.

**Safe-call chains through a call that never returns `null`.** In `a?.f()?.g()` the compiler checks the result of `f()` for `null` again, although `f()` returns a non-null type, so that check can never be true. Each chain is rewritten as `a?.let { it.f().g() }` (or `(a ?: return …).f()`), which checks `a` once:

- `ReleaseNotes`: `find(…)?.groupValues?.get(1)` and `this?.trim()?.takeIf { … }`.
- `ReadWorktreeStatusUseCase`'s administrative folder, `ReadResourcePoolUseCase`'s worktree path from `gitdir`, `ReleaseWorktreeResourcesUseCase`'s stopped daemons, `RemoveWorktreeUseCase`'s Orca close failure, `OrcaWorktreeSync.issueNumberOf` (which now reads the issue number from `groupValues`), `GitFailureCause`'s quoted path, `CheckInstallationUseCase.gitVersion`, `CommandLineTool`'s Java path in the Windows script and its own-link check, `ClaudeCodeHooks`' parse error line, `GradleSettingsContribution`'s effective JVM arguments, `ConfigureMultirepoUseCase`'s display name and the shared variables' first problem.
- `AppVersion.parse`: `matchEntire(…)?.destructured`.
- `chooseSimulator`'s iPhone model number: `find(…)?.groupValues?.get(1)?.toInt()`.
- `poolDisplayName`: `adminDirectory?.substringBefore(…)?.substringAfterLast(…)` and the worktree's `?.trimEnd(…)?.substringAfterLast(…)`.
- `Multirepo`: the workspace setting's `element[…]?.jsonPrimitive?.contentOrNull` and `?.booleanOrNull`, and `declaredName`'s `name?.trim()?.ifEmpty { … }`.

**A non-null `let` followed by `?:`.** In `a?.let { nonNull } ?: b` the elvis also tests the lambda's result, which is never `null`. Each is rewritten as `if (a != null) … else b` or with an early `?: return`:

- `Multirepo.parentFolder`, `ProjectConfig.worktreePathForBranch`, `OrcaComputerUse`'s Windows suffix.
- `WorktreeError`: `PostCreateCommandFailed`'s exit code, `DesktopTurnWaitTimedOut`'s holder and the desktop-taken summary.
- `ListDerivedDataUseCase`'s measured sizes, `ProjectRegistration`'s workspace folder, `RemoveWorktreeUseCase`'s Herdr close failure, `ResolveRepositoryUseCase`'s main worktree, `ResolveMultirepoUseCase`'s approved declaration above a folder and its workspace alias check, `CheckInstallationUseCase.isAtLeast`, `ConfigureMultirepoUseCase.init`'s worktrees root and `setWorkspace`'s alias, `ReleaseDesktopTurnUseCase.force`, `DetectGroupsUseCase`'s member configuration, `DetectRunningAgentsUseCase`'s Herdr and Orca merges, `GroupContext`'s default repositories and OpenSpec folder, `InstallationReportText`'s agent tool summary, `ClaudeSessionNaming.focusedSessionName`, `CommandLineTool.status`.

**`when` over a nullable enum.** A `when` with a `null ->` arm compiles to a switch whose fallback branch (the `NoWhenBranchMatchedException`) no value reaches, and Kover only filters that fallback when the subject isn't nullable. `ClaudeSession.state` and the pool holders' protection note return early on `null` and then switch over the non-null value.

**Errors whose message is always set.** `WorktreeError`, `CommandExecutionException` (and its cause) and `InvalidSharedEnvironment` are always built with a message, but `Throwable.message` and `cause` are nullable, so every `.orEmpty()` or `?.` on them carried a branch no error could take. They now override `message` (and `cause`) with non-null types, which also lets `orcaDetail()`, `herdrDetail()`, `ToolCommands`, `TourUseCases`, `WorktreeCreation` and `ConfigureMultirepoUseCase` drop those fallbacks. A `SerializationException` from `Json.parseToJsonElement` always explains itself, so `ClaudeCodeHooks` reads its message with `!!`.

**Conditions an earlier check already decides.**

- `ClaudeCodeHooks.status`: when every desired hook is added, none can be replaced, so `added.size == DESIRED.size && replaced.isEmpty()` drops the second test. Shogun hooks are always JSON objects, so they are kept as `JsonObject` instead of being cast again.
- `selectCleanup` reads a worktree's running agents through one local function instead of repeating `runningAgents?.get(path).orEmpty()` where the first call already proved them present.
- `CommandLineTool` remembers the target of its own launcher (the link's target, or the Java path in the Windows script) when it finds it, instead of reading the link or the script again to check that the target exists; the second read could only fail if the file changed in between.
- `DetectRunningAgentsUseCase`: agents found by scanning processes never carry a state, so `agent.state == null` is dropped when applying a hook state; the agent names of a worktree are always known for an attributed process (`getValue` instead of `.orEmpty()`); the Herdr merge always passes the process agent, so its parameter isn't nullable.

- `AcquireWorktreeResourceUseCase` only handles emulators and simulators, whose pool holders always have an administrative folder and a reference (the serial or the UDID), so it reads them with `!!` instead of `checkNotNull`, `?.let` or `!= null` tests that no holder could fail.
- `OrcaWorktreeSync` only builds an update for activities with a board column, and every one of them has a comment, so it reads the comment with `!!`.
- `ReadAgentActivityUseCase` compares the transcript check with `== true` instead of `?: false` after a `let` that never returns `null`.
- `ResolveRepositoryUseCase` reads the first line of Git's answer with `first()`: `lines()` always returns at least one line.
- `ResolveWorktreeEnvironmentUseCase` records a Compose project only when the Compose provider is declared, and that provider always contributes `COMPOSE_PROJECT_NAME` (or a declared or shared variable replaces it), so it reads the name with `last { … }` instead of `lastOrNull { … } ?: return`.
- `ResolveMultirepoUseCase` checks the declared name with an `if` instead of `declaredName?.takeUnless(…)?.let { … }`.
- `SharedEnvironment` looks for the worktree of a branch without testing `isBare`: a bare entry has no branch, so the branch comparison already leaves it out.
- `copySecretsOrRollback` rolls back on any `WorktreeError` from `applyProvisionedFiles`, which only throws `SecretCopyFailed` or `LinkedFolderOccupied`; the type test before the rollback could never let another error through.
- `TmuxTerminalLauncher` picks the most recently attached session and then reads its name, instead of `maxByOrNull { … }?.second ?: throw`, whose elvis tested a name that is never `null`.
- `ProcessMavenDaemonSource` maps the last state line with an `if` instead of `?.let { … } ?: DaemonState.Busy`.
- Code that `develop` brought in while this change was open had the same shapes: `WorktreeError.WorktreeLocked`'s message and `lockReason` (from `fix/locked-worktree-removal`), `WorktreeStatus.hasUnpushedCommits` and the inactivity check (from `attention-model`), and `InstallationReportText`'s provider tool fix (from `doctor-provider-checks`) are rewritten the same way when merging, with tests for each outcome.

- `TrimDerivedDataUseCase` only plans orphaned or worktree folders, so it checks a candidate as a worktree folder or else as an orphan, and `isInUse` takes the worktree owner instead of casting it again. `WorktreeFolderRemoval` only closes Orca terminals of a worktree that has them, which needs the Orca use case, so it reads both through one `takeIf`. `SharedEnvironment` resolves the alias of a repository the multirepo declares (`first` instead of `firstOrNull`) and, when the declaration is invalid, it has an `env` block (`!!`).

**Reading JSON written by tools and by ShogunAi.**

- `stringOrNull()` (in `JsonText.kt`) reads a JSON primitive's text only when it is a string. It replaces `takeIf { it.isString }?.content`, whose following `?.` tested a `content` that is never `null`, in `RepositoryProvisioningFiles`, `NioEnvironmentStateStore`, `FileClaudeSessionRegistry`, `FileAgentSessionStore`, `SimctlAppleSimulators`, `CliHerdrClient` and `CliOrcaClient`.
- `firstLineOf(error)` (in `UnreadableFile.kt`) reads the first line of a decoding error's message with `!!`: kotlinx.serialization's errors and the JSON element accessors always carry one. `JsonSettingsFile` and `JsonProjectRepository` caught `IllegalArgumentException` after `SerializationException` (a subclass of it), and `JsonMultirepoSource`'s fallback to "invalid JSON" for a blank message never applied; each now has a single catch.
- `JsonComposeProjectRegistry`, `JsonRepositoryTrust`, `JsonMultirepoSource`, `JsonOrcaWorkspaceStore`, `JsonDerivedDataSizeStore`, `NioDeviceWaitQueue`, `NioDesktopTurnStore` and `FileClaudeSessionRegistry` kept their `@Serializable` file formats `private`. Each adapter writes them with one `Json` configuration (or never writes them, like Claude Code's session files), so the generated code for the other configuration was unreachable from any input. They are `internal` now and their serializers are tested directly in both modes, like the public models.
- `RepositoryProvisioningFiles`, `CliOrcaClient`, `JsonMultirepoSource`, `JsonRepositoryTrust`, `ProcessGradleDaemonSource`, `FileAgentSessionStore` and `NioEnvironmentStateStore` had the same `?.let { nonNull } ?: …` and `a?.f()?.g()` shapes as the domain; they are rewritten the same way. `InvalidEnvironmentTemplate` overrides `message` as non-null.
- `ProcessHandleLiveness` and `isLiveClaudeProcess` share a `FakeProcessHandle` test fixture; the liveness check takes the handle in an internal overload so a test can give it a process that has exited or whose start time can't be read.
- `FileClaudeSessionRegistry` no longer wraps `isRegularFile()` in `runCatching`: it doesn't throw for a path listed from a directory.
- `NioFileManager`: the reason of a read failure comes from `readFailureReason(error)`, the tree deletion visitor is the internal `TreeDeleter`, the existing ancestor of a path is found with `generateSequence(…).first { … }`, and folders are created from the absolute path's parent, which always exists. Deleting a read-only file makes it writable and retries without first testing whether `setWritable` succeeded: if it didn't, the retry fails with the same `AccessDeniedException`. `JsonMultirepoSource` reports an unreadable declaration with `readFailureReason` too, instead of `error.message ?: "unreadable"`, whose fallback no I/O error reached; the reason now names the failure (for example "permission denied") instead of repeating the path.
- `NioProvisioningRecordStore`, `NioLauncherFiles`, `HerdrConfigFile`, `GitHubReleaseSource`, `CliGitHubClient`, `EditorExecutableResolver`, `EditorLaunchers`, `GradleHeadlessAppLauncher`, `MultiplexerTerminalLaunchers`, `SystemHostEnvironment`, `SystemProcessEnvironmentReader`, `XcodeWorkspaceFinder`, `DockerComposeProjectSource`, `ProcessGradleDaemonSource`, `MacAppWindowService` and `MacDerivedDataSource` had the same `a?.f()?.g()` and `?.let { nonNull } ?: …` shapes, rewritten the same way. `NioLauncherFiles.linkTarget` wraps the whole lookup in `runCatching`, so a path the system can't represent has no target instead of failing.
- `MacDerivedDataSource` measures sizes with the internal `SizeVisitor`, returns early when a folder can't be listed, and no longer tests `nameCount == 0` (a path under the volumes root always has a name) or `takeIf { it >= 0 }` on a file size. `FileAgentSessionStore` resolves a multirepo folder's real path directly: it has just checked that a file inside it exists.
- `PmsetPowerSource` reads the first line with `first()`; `CliOrcaClient.isOlder` reads a missing version part with `getOrNull(index) ?: 0` instead of `getOrElse`, whose negative-index check no index reached.
- `JvmMachineCapacity` takes the operating system bean as a parameter (the platform's by default), so a test can give it one without memory figures.
- `ProcessBuilderShellCommandExecutor` reads output through an explicit `BufferedReader`: `bufferedReader()` and `useLines` test whether the reader is already buffered, which always has the same answer. Killing a process left alive moved to `stopIfAlive`, so the `finally` that calls it, which the compiler duplicates for the normal and the exceptional exit, holds no branch of its own.
- `GradleHeadlessAppLauncher` takes the stop grace period as a parameter (five seconds by default), so a test can stop a process that ignores the stop request without waiting.

**Defaults evaluated from the operating system.** `usesPowerShell = HostOs.current() == HostOs.WINDOWS` in the Orca use cases could only be `false` on the macOS job. `HostOs.isWindows` holds the comparison (tested for every value) and the defaults read it. `CopyWorktreeLaunchCommandUseCase`, `MachineProviderContributions`, `CliOrcaClient`, `CliHerdrClient`, `SimctlAppleSimulators`, `LocalAndroidSdk`, `HerdrConfigFile`, `MacPackageCacheSource` and `MacDerivedDataSource` read `os.name` with `System.getProperty(name, "")`. The null device of `LocalAndroidSdk` and `ProcessBuilderShellCommandExecutor` comes from `nullDevice(separator)`, tested for both separators, and `sameDirectory`'s case sensitivity comes from `isCaseInsensitiveFileSystem(osName)`, tested for every system.

**Test seams.** `ResolveMultirepoUseCase` takes the path check (`isValidPath`, `isValidMultirepoPath` by default) so a test can reject a path: on macOS and Linux every path without `NUL` parses, and `NUL` is already rejected as a control character, so the "isn't a valid path" branch is otherwise only reachable on Windows. `isLiveClaudeProcess` takes a `ProcessHandle` in an internal overload, so a test can give it a process that has exited or whose command can't be read. `DetectRunningAgentsUseCase`'s default transcript clock is the internal `fileModifiedAt`, tested on a real temporary file.

**System properties that are always set.** `HostOs.current()` and `HostArchitecture.current()` read `os.name` and `os.arch`, which the JVM always defines, with `.orEmpty()`; they use `System.getProperty(name, "")` instead.

**Checks that one platform makes redundant.** On macOS and Linux a path is absolute exactly when it starts with `/`, so of `isAbsolute || startsWith("/")` the second can't be taken there:

- `RepositoryConfigRules.isInsideWorktree` rejects a path with a root (`path.root != null`) or starting with `\`. On macOS and Linux this is the same as before. On Windows it also rejects a drive-relative path such as `C:env`, which the old check let through although it resolves outside the worktree.
- `isValidMultirepoPath` checks that the path parses before checking for unsafe characters. On macOS and Linux only `NUL` stops a path from parsing, and it is also an unsafe character, so with the old order the parse check could never fail there.

## Effect on the changes in flight

`coverage-cli-branches`, `coverage-desktop`, `herdr-e2e-windows`, `desktop-host-environment`, `doctor-provider-checks` and `packaged-smoke-test` run in parallel. Those that add code to `core` (`desktop-host-environment`, `doctor-provider-checks`) must cover all its branches once this change is in `develop`. If one of them merges first, this change brings in `develop` before its pull request and covers any branch it left; the coverage floors in the build scripts and the roadmap's coverage item are the expected merge conflicts.

## Risks / Trade-offs

- [Tests written only to touch a branch] → Each test names the behavior and asserts its outcome; reviewed like any other test.
- [The rule leaves no slack: a single untested branch fails CI] → That's the point of the rule; `koverXmlReport` points to the line, and the guideline in `AGENTS.md` already asks for `koverVerify` before a change is done.
- [Kotlin or Kover upgrades can add generated branches] → An upgrade that adds branches comes with the tests for them, like any other change; one that changes the unreachable deserializer checks adjusts the maximum and says why.
- [Simplifying production code to remove an unreachable branch can change behavior] → Only branches proven unreachable by type or by every call site are removed, and the existing tests of the class keep passing.

## Migration Plan

None: tests and a build rule. Rolling back is lowering the rule, which the guideline forbids except by reverting the change.
