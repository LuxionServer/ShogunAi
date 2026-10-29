# Tasks

## 1. Regression test first

- [ ] 1.1 Add `DockLaunchedChildProcessTest` in `core` (macOS and Linux only): fake `shogun`, fake `herdr` printing `command -v shogun` and a fake login shell in a temporary folder, Dock variables (`PATH=/usr/bin:/bin:/usr/sbin:/sbin`), Herdr run through `ExecutableLocator`, the executor and `CliHerdrClient`, expecting the fake `shogun`'s path. Verify it fails with the executor's current `HomebrewPath.merge`.

## 2. The host environment

- [ ] 2.1 Add `domain/io/HostEnvironment` (`os`, `home`, `shell`, `path: HostPath`, `variable`), `HostPath` and `HostPathSource`, and `infrastructure/SystemHostEnvironment` with `forCommandLine()` and `forDesktopApp()`, moving `ShellLoginShellPath`'s login shell reading into it. Verify with `SystemHostEnvironmentTest`: the cases of `ShellLoginShellPathTest` (read and merged, rc noise, non-zero exit, timeout, shell that can't start, no markers, empty value, no `SHELL`, Windows starts nothing, resolved once) plus the source of each case, the CLI's process `PATH` followed by the usual folders without starting a shell, blank `SHELL`, and `variable`.
- [ ] 2.2 Delete `ShellLoginShellPath`, `LoginShellPath` and their test; move `CommandLineToolTest`'s login shell case onto `SystemHostEnvironment`. Verify `./gradlew :core:test` compiles and passes.

## 3. Child processes and tool lookup

- [ ] 3.1 Give `ProcessBuilderShellCommandExecutor` a `path: (() -> List<String>)?` that sets each child's `PATH` before explicit variables, and delete `HomebrewPath` and `HomebrewPathTest`. Verify with executor tests (host `PATH` reaches the child, explicit `PATH` wins, `null` inherits) and that 1.1 now passes.
- [ ] 3.2 Build `ExecutableLocator` from a `HostEnvironment`; give `CliHerdrClient` and `CliOrcaClient` a `findExecutable` and drop their folder lists and `userHome`, keeping Orca's macOS app bundle. Verify with their updated tests, including Herdr found in a folder only the host `PATH` has and Orca's bundle when nothing is on the `PATH`.

## 4. One source for the whole code

- [ ] 4.1 Remove every `System.getenv` and `user.home` default from production constructors and functions (`core`, `cli`, `desktopApp`), add `infrastructure/ShogunFiles(home)` for the `~/.shogunai` paths, and pass `HostEnvironment` (or its values) from `cli/Main.kt`, `ShogunCli` and `AppContainer`, which keeps starting the `PATH` resolution on a background thread. Verify `./gradlew test` compiles and passes.
- [ ] 4.2 Add `HostEnvironmentSourceTest` scanning the production sources of `core`, `cli` and `desktopApp` for `System.getenv(` and `System.getProperty("user.home")` outside `SystemHostEnvironment.kt` (and the generated-script string in `FileKmpTestWiring.kt`). Verify it passes, and fails when a read is added elsewhere.

## 5. Docs

- [ ] 5.1 Update `docs/architecture/domain.md`, `docs/architecture/infrastructure.md` and `docs/guide/installation.md` with their Spanish translations: the host environment, where its `PATH` comes from in the app and in the CLI, and that child processes and tool lookups use it. Verify `mkdocs build --strict` passes.

## 6. Validation

- [ ] 6.1 Run `./gradlew test` and verify it passes with no compiler warnings.
- [ ] 6.2 Build the packaged app, open it the way the Dock does (`open`, minimal `PATH`), start Herdr from it, and verify with `ps eww` that the Herdr server's `PATH` is the login shell's, and that the Claude Code section shows the command as available.

## Workflow follow-up

- Archive the change with `/opsx:archive` and update roadmap item 1 in `docs/roadmap.md` and `docs/roadmap.es.md`.
- Commit the implementation, bring the branch up to date with `origin/develop`, re-run the tests and open the PR with `--base develop --assignee CiroFeliu`.
