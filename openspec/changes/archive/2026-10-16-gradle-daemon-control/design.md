# Design

## Context

See proposal.md for the motivation. The facts below come from a real machine running Gradle 9.6.1 and Kotlin 2.4.0, and they shape the approach:

- A Gradle daemon is a `java` process whose arguments contain `org.gradle.launcher.daemon.bootstrap.GradleDaemon <version>`. A Kotlin compile daemon is a `java` process whose arguments contain `org.jetbrains.kotlin.daemon.KotlinCompileDaemon`.
- Each Gradle daemon writes `$GRADLE_USER_HOME/daemon/<version>/daemon-<pid>.out.log` (default `~/.gradle`). Every build adds `Received command: Build{id=…, currentDir=<path>}`, and every state change adds `Marking the daemon as busy` or `Marking the daemon as idle`, at INFO level and with a timestamp. The log runs at DEBUG level and grows by megabytes per hour.
- Gradle's own `--status` and `--stop` only see daemons of the Gradle version that runs them, and `--stop` stops every daemon of that version, busy or not. The binary registry (`registry.bin`) is an internal format.
- ShogunAi doesn't start builds. It can only observe processes and act on them.
- Running-agent detection already uses `ps`/`lsof` through `ShellCommandExecutor`, treats a failed listing as "unavailable", and attributes a working directory with `owningWorktree`, which handles nested worktrees.

## Goals / Non-Goals

**Goals:**
- Read daemon state without depending on a Gradle binary or on Gradle's internal formats.
- Never interrupt a running build.
- Keep the provider declaration generic so later providers (emulator, Android Studio) plug into the same `providers` object.

**Non-Goals:**
- Changing how builds run: per-worktree `GRADLE_USER_HOME`, JVM arguments or idle timeouts. They need variables that reach the agent's shell, which is the planned "per-worktree environment" change.
- Copying `.gradle/` or `build/` between worktrees. The configuration cache and the execution history hold absolute paths, so copies wouldn't be reused; the build cache is the supported way.
- Windows support for daemon inspection in this change.
- Attributing Kotlin daemons to worktrees: their arguments and logs don't name the build directory.

## Decisions

### Providers as an object keyed by id
`RepositoryProvisioning` gains `providers: RepositoryProviders`, a data class with `gradle: GradleProvider?` (a `data object` for now, since it has no options yet; it becomes a data class when it gains one). `RepositoryProvisioningFiles` reads `providers` next to `copy`, skips unknown ids, and throws `InvalidRepositoryConfig` for a non-object `providers` or a non-object known provider. `addCopyPatterns` already rewrites only `copy` over the parsed `JsonObject`, so `providers` is kept as it is.
Alternatives: a list (`[{"type": "gradle"}]`) allows duplicates and merges badly; top-level `"gradle": {}` mixes providers with other fields. Accepting `"gradle": true` as a shorthand was rejected so every provider has a single shape that can grow options.

### State from the daemon log, read from its end
A new port `GradleDaemonSource` in `domain/io` returns `List<DaemonProcess>` (pid, kind, version, resident memory, last build directory, state). The implementation, `ProcessGradleDaemonSource`, runs `ps -A -ww -o pid=,rss=,command=` (POSIX `-A` rather than BSD `-ax`, so the same argv works on Linux) through `ShellCommandExecutor`, keeps the processes whose arguments contain one of the two main classes, takes the version from the argument after `GradleDaemon`, and for each Gradle daemon reads the last 1 MiB of `daemon-<pid>.out.log`, scanning backwards for the latest `Marking the daemon as busy|idle` and the latest `Received command: Build{…currentDir=…}`. A missing file, a read error, or no marker in that window gives state `Unknown`.
`GRADLE_USER_HOME` is taken from the environment, falling back to `~/.gradle`. The domain sees only the port.
Alternatives: parsing `registry.bin` (undocumented, changes between versions); running `gradle --status` (needs one Gradle binary per version and gives no build directory); `jps`/JMX (needs a JDK on the PATH and reveals no busy state).

### Stopping with SIGTERM after a fresh check
`StopGradleDaemonsUseCase` takes a list of pids (or all daemons), lists the daemons again, and for each one decides: a Gradle daemon is stopped only if its fresh state is `Idle`; a Kotlin daemon only if no Gradle daemon is `Busy` or `Unknown`. It runs `kill <pid>` (SIGTERM, which lets the JVM run Gradle's shutdown hooks) and returns a `DaemonStopReport` with stopped, kept (with reason: busy, unknown, build running), `notDaemons` (requested pids that aren't daemons) and failures. A pid that isn't a daemon is reported, not thrown, so the other requested daemons are still stopped; the CLI turns it into exit code 1.
Alternatives: `gradle --stop` (stops busy daemons and only one version); `kill -9` (skips cleanup and can leave locks behind in the shared caches).

### Attribution reuses `owningWorktree`
`ListGradleDaemonsUseCase` takes the repository's worktrees and maps each Gradle daemon's last build directory with `owningWorktree` to `DaemonOwner.Worktree(path)`, `RemovedWorktree` (no owning worktree and the folder doesn't exist, through `FileManager.exists`) or `OtherProject`. Outside a repository the list is unattributed.

### Cleanup stops daemons after removals, in the caller
`CleanUpWorktreesUseCase` stays focused on Git. The desktop view model and `shogun clean` call it, then, when the user chose to, call `StopGradleDaemonsUseCase` with the pids of idle daemons attributed to paths in `CleanupReport.removed`. Because removal deletes the folder, attribution is computed from the list taken before the cleanup, and the stop step's fresh check still protects a daemon that became busy. The daemon results go into the screen's report, never into `CleanupReport.failures`.
The GUI option is checked by default because stopping an idle daemon only costs a restart on the next build. `shogun clean` needs the explicit flag, as `--delete-branches` does, because scripts call it.

### Build cache notice
`CheckGradleBuildCacheUseCase` reads `<repository>/gradle.properties` and `$GRADLE_USER_HOME/gradle.properties` through a small `PropertiesReader` added to `FileManager` (`readProperties(path): Map<String, String>`), and reports enabled when either has `org.gradle.caching=true`. Command-line `-P`/`--build-cache` flags and `settings.gradle` build cache blocks are not inspected: the notice is advice, not a guarantee.

### Desktop section
`WorktreeViewModel` loads the daemons only when the repository declares the Gradle provider, in the same refresh cycle as the running agents, so it follows the screen's auto-refresh. The section is a `SectionCard` below the worktree list: one row per daemon (kind and version, memory, "busy" / "idle for 12 min" / "unknown", worktree name, "removed worktree" or "other project") with a stop button, a "Stop idle daemons" action, the total memory, and the build cache notice. Strings go to `Strings`, `EnglishStrings` and `SpanishStrings`.

### Command-line shape
`CliCommand.Gradle` and `CliCommand.GradleStop(pids: List<Long>)` are parsed under the `gradle` verb; `clean` gains `stopGradleDaemons`. `shogun gradle` resolves the repository only if the current directory is inside one, so it also works elsewhere.

## Risks / Trade-offs

- [The log format is Gradle's, not a public API] → The markers are INFO lines that have been stable for years; if they disappear the state becomes `Unknown`, which disables stopping instead of stopping a busy daemon. Tests pin the parser to real log excerpts.
- [A build can start between the fresh check and `kill`] → The window is milliseconds; the build's client fails and Gradle reports a daemon disappearing, and the agent can rerun it. Documented as a known limit.
- [`currentDir` is the directory the Gradle client was started from, not the project directory: `./gradlew -p <worktree>` run from elsewhere is attributed to where it was run, and the log has no line naming the project directory (checked on Gradle 9.6.1)] → Agents and terminals build from inside the worktree, which is the case this targets. A misattributed daemon is still listed with its real state, and the stop rules depend on the state, not on the attribution.
- [A daemon running with another `gradle.user.home`] → Its log isn't found, its state is `Unknown`, and it's listed but never stopped.
- [Kotlin daemons are also used by IDEs] → Only stopped while no Gradle daemon is busy or unknown, and only when the user asks; the IDE starts a new one when it needs it.
- [Reading 1 MiB per daemon on every refresh] → A few daemons at most, read from the end; cost is milliseconds. The section is only loaded for Gradle projects.
- [`ps` output differs between macOS and Linux] → Only `pid`, `rss` and `command` columns are requested, which both support; Windows reports the section as unavailable.

## Migration Plan

No migration: repositories without `providers` behave as before, and older ShogunAi versions already ignore the new field.
