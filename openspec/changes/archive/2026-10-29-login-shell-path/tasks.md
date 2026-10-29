# Tasks

## 1. Regression tests first

- [x] 1.1 In `CommandLineToolTest`, add a test with the app's minimal `PATH` (`/usr/bin:/bin:/usr/sbin:/sbin`), `shogun` in `/opt/homebrew/bin` and a login shell `PATH` including it, expecting `AVAILABLE`; and one where installing with `/opt/homebrew/bin` writable and on the login shell's `PATH` links into it. Verify both fail against the current wiring (the app's process `PATH`) before the fix.

## 2. Login shell PATH port

- [x] 2.1 Add `domain/io/LoginShellPath` and `infrastructure/ShellLoginShellPath` (argv `$SHELL -l -i -c`, markers, 5-second limit through `executeWithin`, resolved once, fallback to process `PATH` plus `ToolCatalog.userBinFolders`, process `PATH` only on Windows). Verify with `ShellLoginShellPathTest`: read and merged, rc noise around the markers, non-zero exit with markers, timeout (and the limit passed), shell that can't start, no markers, empty value, no `SHELL`, Windows starts nothing, resolved only once.

## 3. Use it in CommandLineTool and ExecutableLocator

- [x] 3.1 Change `CommandLineTool.pathFolders` and `ExecutableLocator.pathFolders` to `() -> List<String>` (default for `ExecutableLocator` still the process `PATH`), update their tests and callers, and verify the regression tests of 1.1 pass and `./gradlew :core:test` passes.
- [x] 3.2 In `AppContainer`, build one `ShellLoginShellPath`, start resolving it on a daemon thread at start-up, and pass `it::folders` to `CommandLineTool` and to both `ExecutableLocator`s. Verify `./gradlew :desktopApp:test` passes.

## 4. Docs

- [x] 4.1 Update `docs/guide/installation.md` and `docs/architecture/infrastructure.md` with their Spanish translations: the app judges and installs against the login shell's `PATH` on macOS and Linux, with the fallback. Verify `mkdocs build --strict` passes.

## 5. Validation

- [x] 5.1 Run `./gradlew test` and verify it passes with no compiler warnings.
- [x] 5.2 Build the installed app with the packaging task, open it from Finder (not from a terminal), and check on the secondary screen that the Claude Code section shows the command as available and the guided tour can start.

## Workflow follow-up

- Commit the implementation, archive the change with `/opsx:archive`, and update roadmap item 1 in both languages only if PR #230 is merged (otherwise say so in the PR).
- Bring the branch up to date with `origin/develop`, re-run the tests and open the PR with `--base develop --assignee CiroFeliu`.
