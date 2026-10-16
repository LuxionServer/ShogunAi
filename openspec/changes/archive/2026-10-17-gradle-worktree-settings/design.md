# Design

## Context

See proposal.md for the motivation. The facts below were checked on this machine with the repository's Gradle wrapper (Gradle 9) before writing this design, and they shape the approach:

- `GRADLE_OPTS="-Dorg.gradle.jvmargs='-Xmx777m -Dfile.encoding=UTF-8'"` started a daemon with exactly those arguments although the project's `gradle.properties` set `-Xmx1g`. With a user `gradle.properties` setting `-Xmx1500m`, `GRADLE_OPTS` still won. The wrapper script splits `GRADLE_OPTS` with `xargs`, so both single and double quotes group a value with spaces.
- `-Dorg.gradle.daemon.idletimeout=120000` in `GRADLE_OPTS` gave the daemon `idleTimeout=120000` (in its log's daemon context).
- `-Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx333m` in `GRADLE_OPTS` made `kotlin.daemon.jvmargs` a project property with that value (`./gradlew properties`).
- Gradle reuses an idle daemon only if it's compatible, which includes its JVM arguments. A daemon started with different arguments isn't reused.
- The environment already supports provider contributions (`EnvironmentContribution` from `MachineProviderContributions.forRepository`, given the repository path), declared variables win over provider ones, and `FileManager.readProperties` plus the Gradle user home are already used by `CheckGradleBuildCacheUseCase`.
- The variables reach the agent's shell through `shogun env`, the Claude Code `SessionStart` hook and `envFile`; nothing reaches an IDE that wasn't started from that shell.

## Goals / Non-Goals

**Goals:**
- Bound the memory and lifetime of the daemons that agent builds start, without editing tracked files or the user's Gradle home.
- Keep every worktree's daemons compatible with each other, so parallel worktrees share idle daemons.
- Leave repositories that don't declare settings exactly as they are.

**Non-Goals:**
- Different values per worktree. They'd make daemons incompatible and multiply them, the opposite of the goal.
- A per-worktree `GRADLE_USER_HOME`: it would duplicate the dependency and build caches per worktree.
- Settings for builds started from Android Studio or IntelliJ, which read `gradle.properties` and their own settings, not the agent's shell. The docs point this out.
- Arbitrary Gradle properties or JVM flags. Three settings cover the memory problem; a general escape hatch already exists as `GRADLE_OPTS` in `env`.
- Machine-level settings stored with the project (like the emulator's). The limits describe how the repository builds, so they belong in `.shogunai.json`; a user who wants different limits on one machine can set `GRADLE_OPTS` in their own shell after `shogun env`.

## Decisions

### Settings in the provider declaration
`GradleProvider` becomes `data class GradleProvider(val daemonMaxHeap: String? = null, val idleTimeoutMinutes: Int? = null, val kotlinDaemonMaxHeap: String? = null)` with `hasSettings`. `RepositoryProvisioningFiles` parses them with the existing `providerOptions` and two small readers: a memory size matching `^[0-9]+[kKmMgG]$` and a positive integer (a JSON number without fraction). Errors use `InvalidRepositoryConfig` naming `providers.gradle.<field>`, like the emulator and simulator options.
Alternatives: a single `jvmArgs` string would make users restate every flag their `gradle.properties` already has, and is easy to get wrong; minutes as a duration string (`"30m"`) adds a parser for no gain.

### Delivery through `GRADLE_OPTS`
The provider contributes one variable, `GRADLE_OPTS`, built from the settings: `-Dorg.gradle.jvmargs="<args>"`, `-Dorg.gradle.daemon.idletimeout=<minutes × 60000>` and `-Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx<size>`, each only when its setting is declared, in that order and separated by spaces. The jvmargs value is wrapped in double quotes because both the Unix wrapper (`xargs`) and `gradlew.bat` (the JVM's Windows argument parsing) group a double-quoted value.
Alternatives: `ORG_GRADLE_PROJECT_*` variables only set project properties, not daemon arguments; `JAVA_OPTS` would also reach the client JVM but isn't documented for Gradle properties; writing a `gradle.properties` per worktree would touch a tracked file.

### Keeping the repository's other JVM arguments
`org.gradle.jvmargs` replaces Gradle's whole argument list, so setting only `-Xmx2g` would drop flags like `-Dfile.encoding=UTF-8` or `-XX:MaxMetaspaceSize`. The contribution reads `org.gradle.jvmargs` from the user's Gradle `gradle.properties`, falling back to the base repository's (the same order Gradle uses), splits it on whitespace, removes every `-Xmx…` token and puts `-Xmx<size>` first. If the effective arguments contain a double quote, which can't be nested in the quoted value, it uses `-Xmx<size>` alone. A pure function (`gradleDaemonArguments(effective: String?, maxHeap: String): String`) holds this logic so it's unit-tested without files.
The arguments are read from the base repository, not from each worktree's checkout, so every worktree gets the same `GRADLE_OPTS` and daemons stay compatible even when a branch changes its `gradle.properties`.

### Every worktree, the main one included
The contribution doesn't depend on the worktree, so it applies to the main worktree too. Builds an agent starts from the main checkout then share daemons with builds in linked worktrees. The trade-off: those daemons aren't reused by IDE builds, which use the unmodified `gradle.properties` (see Risks).

### Wiring
`MachineProviderContributions` gains `gradleJvmArgsFor: (repositoryPath) -> String?`, which `AppContainer` and `ShogunCli` build from the `FileManager` and Gradle user home they already have for the build cache notice (`effectiveGradleJvmArgs`), and adds the Gradle contribution when `providers.gradle?.hasSettings` is true. The function defaults to no arguments, so tests that don't care about Gradle construct it as before. It has no ports or resources, only `variables`. The CLI needs no change beyond construction.

### Settings on the worktree screen
The Gradle daemons section state gains the declared `GradleProvider` and shows one line, e.g. "Worktree builds: 2 GB heap · idle after 30 min · Kotlin 1 GB", in English and Spanish, only when settings exist. Memory sizes are displayed as written (`2g` → "2 GB", `1024m` → "1024 MB").

## Risks / Trade-offs

- **A user's own `GRADLE_OPTS` is replaced** in shells that load the worktree environment. → Documented; repositories that need extra options declare `GRADLE_OPTS` in `env`, which wins over the provider.
- **IDE and agent builds don't share daemons**, because their JVM arguments differ. → Documented, with the alternative of setting the same limits in `gradle.properties` when the team wants them everywhere.
- **Kotlin daemon setting depends on the Kotlin Gradle plugin** reading `kotlin.daemon.jvmargs` as a project property, which `-Dorg.gradle.project.` provides; a build that runs Kotlin in-process ignores it. → A task checks the running Kotlin daemon's heap on a Kotlin project.
- **Quoting on Windows** (`gradlew.bat`) is reasoned from the JVM launcher's parsing, not tested. → Covered by a unit test of the produced string; manual Windows validation is out of reach in this change and is noted in the docs as unverified.
- **Settings take effect for new daemons only.** Idle daemons started with the old arguments are incompatible and simply idle out; the daemons section already lets the user stop them.
