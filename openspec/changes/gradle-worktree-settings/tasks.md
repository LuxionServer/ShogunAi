# Tasks

## 1. Provider settings

- [ ] 1.1 Turn `GradleProvider` into a data class with `daemonMaxHeap`, `idleTimeoutMinutes`, `kotlinDaemonMaxHeap` and `hasSettings`, and update its usages; verify `./gradlew :core:compileKotlin :cli:compileKotlin :desktopApp:compileKotlin` passes
- [ ] 1.2 Parse and validate the three settings in `RepositoryProvisioningFiles` (memory size `^[0-9]+[kKmMgG]$`, positive whole number for minutes, errors naming `providers.gradle.<field>`); verify with tests for every scenario of "Declare the Gradle provider settings", and that `{}` still declares the provider without settings

## 2. Environment contribution

- [ ] 2.1 Add the pure function that builds the daemon arguments from the effective `org.gradle.jvmargs` and the declared heap (replace every `-Xmx…`, put the new one first, fall back to `-Xmx<size>` alone when the arguments contain a double quote or are missing); verify with unit tests for kept flags, several `-Xmx` tokens, no arguments and quotes
- [ ] 2.2 Add the Gradle contribution that produces `GRADLE_OPTS` (`-Dorg.gradle.jvmargs="…"`, `-Dorg.gradle.daemon.idletimeout=<ms>`, `-Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx<size>`, only for declared settings) reading the user's then the repository's `gradle.properties`; verify with unit tests on a fake `FileManager` for each setting alone, all together, and user-over-repository precedence
- [ ] 2.3 Wire it in `MachineProviderContributions` (with `FileManager` and the Gradle user home) and in its construction in `AppContainer` and `ShogunCli`, only when the provider has settings; verify with `ResolveWorktreeEnvironmentUseCase` tests: same `GRADLE_OPTS` for main and linked worktrees, nothing added for `{}`, and a `GRADLE_OPTS` declared in `env` wins

## 3. Desktop app

- [ ] 3.1 Expose the declared settings in the Gradle daemons section state and show the settings line with English and Spanish strings, only when settings exist; verify with view model tests and in the running app

## 4. Documentation

- [ ] 4.1 Document the settings in the README's "Gradle repositories" section and in `docs/agents.md` (how `GRADLE_OPTS` reaches agents, a user's own `GRADLE_OPTS` being replaced, IDE builds not covered and not sharing daemons, Windows quoting unverified), with the Spanish translations; verify both languages match
- [ ] 4.2 Describe the contribution in `docs/architecture/domain.md` and the settings line in `docs/architecture/ui.md`, in both languages; verify `mkdocs build` passes

## 5. Validation

- [ ] 5.1 Validate end to end on a toy Kotlin/JVM Gradle repository in the scratchpad with `org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8` and settings `2g` / `5` minutes / `512m`: after `eval "$(shogun env)"`, build in two worktrees at once and check each Gradle daemon's command line has `-Xmx2g -Dfile.encoding=UTF-8`, its log reports `idleTimeout=300000`, the Kotlin daemon has `-Xmx512m`, and a later build in a third worktree reuses an idle daemon; check the app shows the settings line
- [ ] 5.2 Run `./gradlew test` and `openspec validate gradle-worktree-settings --strict`; verify both pass

## Workflow follow-up

- Archive the change with `/opsx:archive` in the same branch once validated, removing the "Gradle settings per worktree" bullet from `docs/roadmap.md` and `docs/roadmap.es.md` and updating the "Android, KMP and JVM" layer row, before opening the pull request to `epic/pivot`.
