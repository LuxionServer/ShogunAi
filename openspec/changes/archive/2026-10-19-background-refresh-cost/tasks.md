# Tasks

## 1. Watcher

- [x] 1.1 Pause the watcher while the window isn't focused or is minimized and resume on focus; add `WorktreeBoardTest` cases (no checks while inactive, focus refresh on return) with the test clock and verify they pass
- [x] 1.2 Check fingerprints and devices only for shown projects, keeping agent detection for all; add a board test with a loaded hidden project and verify it passes

## 2. Cheaper reads

- [x] 2.1 Add the per-worktree environment cache for device state reads, invalidated by the project and repository file fingerprints; add tests that count `git rev-parse` calls across two device refreshes and after an invalidation, and verify they pass
- [x] 2.2 Run creation, provisioning and provider reads on the IO dispatcher and make the provider lambdas suspend; verify `./gradlew :desktopApp:test` passes

## 3. Validation

- [x] 3.1 Run `./gradlew test` and verify every module passes
- [x] 3.2 In the running app with a project of 20 worktrees and the emulator provider, verify with `ps` sampling that no `git` or `adb` process starts while the window is in the background for a minute, and that statuses update when it comes back

## Workflow follow-up

- Archive the change before opening its pull request.
