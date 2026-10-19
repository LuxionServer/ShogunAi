# Tasks

## 1. Trust store and decision port

- [ ] 1.1 Add the `trust.json` store (approve, isApproved, revoke, keyed by the repository's real path, value the SHA-256 of the canonical command list) written atomically with 0600; add tests for approve, changed list, reordered list, other repository and revoke, and verify they pass
- [ ] 1.2 Add the `PostCreateApproval` port and `PostCreateDecision`, call it from creation and provisioning only for unapproved commands, record the approval on `Run`, return `skippedPostCreateCommands`; update the creation and provisioning tests (approved runs, unapproved skipped, run records approval) and verify they pass

## 2. Command line

- [ ] 2.1 Implement the terminal approval (prompt on a console, skip without one) for `new` and `provision`, and the skipping approval for `hook herdr` and `hook worktree-create`, with the stderr note; add CLI tests for yes, no, no console and both hooks, and verify they pass
- [ ] 2.2 Add `shogun trust [path] [--yes] [--revoke]` with parser, usage text and tests (show, approve, revoke, nothing to approve), and verify they pass
- [ ] 2.3 Document the approval and `shogun trust` in `docs/guide/cli.md`, `docs/guide/configuration.md`, `docs/agents.md` and their `.es.md` versions, and verify `mkdocs build --strict` passes

## 3. Desktop app

- [ ] 3.1 Add the approval step to the create dialog and to provisioning actions ("Run and remember", "Skip"), and report skipped commands; add UI tests for both choices and verify they pass
- [ ] 3.2 Approve the list saved from project settings and show its approval state with "Revoke"; add view-model tests and verify they pass
- [ ] 3.3 Add the new strings in English and Spanish and verify `StringsTest` passes

## 4. Hardening

- [ ] 4.1 Reject reserved variable names in `.shogunai.json` parsing and in the settings validation; add parsing and draft tests for `PATH`, `LD_PRELOAD`, `GIT_SSH_COMMAND` and a valid name, and verify they pass
- [ ] 4.2 Check the real location before copying, linking and writing the environment file; add `NioFileManager` tests with a symlinked parent pointing outside (nothing written or deleted outside) and verify they pass
- [ ] 4.3 Escape backticks in the environment file; add a test that sources the file with `sh` and compares the value, and verify it passes
- [ ] 4.4 Quote the launch command per platform with the shared quoting helper; update the use case tests (POSIX path with `$(...)` and `'`, Windows path) and verify they pass
- [ ] 4.5 Reject option-like branch names and start points and check branch existence with `show-ref --verify`; add tests for `-d`, `--upload-pack=x` and `feature/*`, and verify they pass

## 5. Validation

- [ ] 5.1 Run `./gradlew test` and verify every module passes
- [ ] 5.2 In the running app (secondary display), create a worktree in a repository with a post-creation command: verify the approval step, the skip path and that a second creation doesn't ask; then change the command on disk and verify it asks again

## Workflow follow-up

- Archive the change before opening its pull request, and mention the one-time approval in the release notes.
