# Tasks

## 1. Command execution

- [x] 1.1 Close stdin, kill the process and its descendants on cancellation, and add the optional timeout in `ProcessBuilderShellCommandExecutor`; add infrastructure tests (cancel `sleep 600` returns within seconds and leaves no process, `cat` gets end-of-file, a timeout raises `TimedOut`) and verify they pass
- [x] 1.2 Pass timeouts from the read-only callers (Git status and listing reads, adb, simctl, ps) and report a timeout as unavailable state; update their tests and verify they pass

## 2. Creation and repository layout

- [x] 2.1 Delete the branch the creation created on every rollback path; add tests for a failed environment and a failed copy that check the branch is gone, and one for `--branch` mode that checks it's kept, and verify they pass
- [x] 2.2 Resolve submodules and separate Git folders, and report bare repositories and repositories without commits with typed errors; add use case tests for each layout and verify they pass
- [x] 2.3 Compare the main worktree by resolved path; add tests for a trailing slash and a symlinked base path and verify they pass
- [x] 2.4 Map task ids with `/` to one folder; add a test for `team/TASK-1` and verify it passes

## 3. Branch deletion and cleanup

- [x] 3.1 Make `DeleteMergedBranchUseCase` use the main branch rule and update both hooks; add tests for a branch merged only into its upstream and verify they pass
- [x] 3.2 Keep worktrees with a running agent out of cleanup in core, the desktop cleanup dialog and `shogun clean`; add use case, UI and CLI tests and verify they pass

## 4. Local files

- [x] 4.1 Report unreadable `projects.json` and `settings.json` with typed errors, keep the unreadable copy before writing, and show the desktop notice; add tests for both files and verify they pass
- [x] 4.2 Serialize read-modify-write of both files with a lock file; add a test with two concurrent writers and verify no change is lost
- [x] 4.3 Add the new desktop strings in English and Spanish and verify `StringsTest` passes

## 5. Command line reporting

- [x] 5.1 Add the last-resort catch to `ShogunCli.execute`; add tests for a corrupted projects file and an unwritable `CLAUDE_ENV_FILE` (one line, exit 1) and verify they pass
- [x] 5.2 Move Git failure classification to `core` and print its hints in the CLI; add a test for "branch already exists" and verify it passes
- [x] 5.3 Add per-command `--help`, the status reason, `clean`'s skipped list and the dry-run hint with the given flags; add CLI tests and verify they pass
- [x] 5.4 Update `docs/guide/cli.md`, `docs/guide/configuration.md` and their Spanish versions and verify `mkdocs build --strict` passes

## 6. Emulator

- [x] 6.1 Fail the boot wait as soon as the emulator process exits, with its last log lines; add a use case test with a fake process that exits and verify it passes

## 7. Validation

- [x] 7.1 Run `./gradlew test` and verify every module passes
- [x] 7.2 In a scratch repository, verify by hand: `shogun new` with a post-creation `read` command finishes; Ctrl+C during a `sleep` post-creation command leaves no process; a corrupted `projects.json` gives one line; `shogun new` in a submodule works

## Workflow follow-up

- Archive the change before opening its pull request.
