# Tasks

## 1. Provider declaration

- [x] 1.1 Add `RepositoryProviders` and `GradleProvider` to `RepositoryProvisioning`, and read `providers` in `RepositoryProvisioningFiles` (unknown ids skipped, non-object `providers` or known provider rejected with `InvalidRepositoryConfig`); verify with tests for declared, missing, unknown and invalid providers, and that `addCopyPatterns` keeps `providers`
- [x] 1.2 Document the `providers` field and the Gradle provider in the user-facing `.shogunai.json` docs and `docs/architecture/domain.md`, in English and Spanish; verify both languages are updated and `mkdocs build` passes

## 2. Daemon inspection

- [x] 2.1 Add the `GradleDaemonSource` port, the `DaemonProcess` model (kind, version, memory, last build directory, state) and a fake in `testFixtures`; verify `./gradlew :core:compileTestFixturesKotlin` passes
- [x] 2.2 Implement `ProcessGradleDaemonSource`: parse `ps -A -ww -o pid=,rss=,command=` output and read the end of each daemon log for state and last build directory; verify with unit tests on real `ps` lines and log excerpts (busy, idle, no marker, missing log, unrelated Java process)
- [x] 2.3 Implement `ListGradleDaemonsUseCase` with worktree attribution (worktree, nested worktree, removed worktree, other project, unattributed outside a repository); verify with use case tests
- [x] 2.4 Implement `StopGradleDaemonsUseCase` (fresh check, idle-only Gradle daemons, Kotlin daemons only without busy or unknown Gradle daemons, `kill` as argv, unknown pids reported as `notDaemons`); verify with tests for every scenario of "Stop idle daemons"
- [x] 2.5 Add `readProperties` to `FileManager` and implement `CheckGradleBuildCacheUseCase` reading the repository's and the user's `gradle.properties`; verify with tests for enabled in either file and off
- [x] 2.6 Describe the daemon source and its log-based state in `docs/architecture/infrastructure.md` and the new use cases in `docs/architecture/domain.md`, in both languages; verify `mkdocs build` passes

## 3. Command line

- [x] 3.1 Parse `shogun gradle`, `shogun gradle stop [<pid>...]` and `shogun clean --stop-gradle-daemons`, and update `shogun help`; verify with `CliCommand` parsing tests including usage errors for invalid pids
- [x] 3.2 Implement the `gradle` and `gradle stop` output and exit codes, working outside a repository and printing the build cache notice in Gradle repositories; verify with CLI tests against the fake daemon source
- [x] 3.3 Stop the idle daemons of removed worktrees in `shogun clean --yes --stop-gradle-daemons`, and list them without `--yes`; verify with CLI tests for each scenario
- [x] 3.4 Document the new commands and option in `docs/architecture/cli.md` and the user-facing CLI docs, in both languages; verify `mkdocs build` passes

## 4. Desktop app

- [x] 4.1 Load daemons and the build cache check in `WorktreeViewModel` only for repositories that declare the Gradle provider, in the same refresh as running agents, with stop-one and stop-all actions; verify with view model tests
- [x] 4.2 Add the "Gradle daemons" section to `WorktreeScreen` (rows, disabled stop for busy or unknown, total memory, build cache notice, unavailable state) with English and Spanish strings; verify by running the app on a Gradle repository
- [x] 4.3 Add the checked "also stop their idle Gradle daemons" option to `CleanupDialog` for Gradle repositories and report the stopped and kept daemons after cleanup; verify with view model tests and in the running app
- [x] 4.4 Describe the section and the cleanup option in `docs/architecture/ui.md`, in both languages; verify `mkdocs build` passes

## 5. Roadmap and end-to-end validation

- [x] 5.1 Update `docs/roadmap.md` and `docs/roadmap.es.md`: remove the delivered daemon bullet from "Android, KMP and JVM provisioning" and add the "Per-worktree environment for agents" item that the emulator provider depends on; verify both languages match
- [x] 5.2 Validate end to end on a toy Gradle repository in the scratchpad with two worktrees: build in both, check `shogun gradle` and the app section show both daemons with their worktrees, clean up one worktree with the option on, and confirm only its idle daemon is stopped while a running build is never interrupted
- [x] 5.3 Run `./gradlew test` and `openspec validate gradle-daemon-control --strict`; verify both pass

## Workflow follow-up

- Archive the change with `/opsx:archive` in the same branch once validated, before opening the pull request to `epic/pivot`.
- Add the pull request to the GitHub Project (layer C, step 9) and move the step to In Progress.
