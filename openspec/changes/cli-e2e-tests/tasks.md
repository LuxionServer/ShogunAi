# Tasks

## 1. Build wiring

- [ ] 1.1 Add the `e2eTest` source set and `Test` task to `cli/build.gradle.kts`, depending on `installDist`, passing the launcher's path and the e2e runtime classpath as system properties, and make `test` depend on it; verify `./gradlew :cli:test` installs the distribution and runs an empty e2e task

## 2. Harness

- [ ] 2.1 Write the sandbox: a real-path temporary folder per test with an isolated home, a Git global configuration and the process environment described in design.md; verify with a test that `shogun project add` writes its catalog under the temporary home
- [ ] 2.2 Write the Git helpers (init, commit, bare `origin`, merge) and the process runner for the installed launcher (argv, working directory, timeout, stdout, stderr, exit code); verify with the `shogun new TASK-1` scenario

## 3. Worktree flows

- [ ] 3.1 Test `shogun new` with `--fix`, `--branch` (local and only on `origin`) and `--json`; verify the branches, paths and JSON fields against `git worktree list`
- [ ] 3.2 Test local files copied, linked and matched by patterns from `.shogunai.json` and `.worktreeinclude` on `shogun new`; verify contents and symbolic links (or copies where links can't be created)
- [ ] 3.3 Test `shogun provision --check` and `shogun provision` restoring a deleted file; verify exit codes 3 and 0 and the restored content
- [ ] 3.4 Test `shogun status --json` and `shogun remove`, and `shogun clean` / `clean --yes --delete-branches` after a real merge; verify folders and branches are gone

## 4. Runners

- [ ] 4.1 Write the fake runner `main` for Herdr and Orca and the launcher scripts the harness installs in the temporary home or `PATH`; verify through the open tests
- [ ] 4.2 Test `shogun open TASK-1 --herdr` (skipped on Windows with its reason) and `shogun open TASK-1 --orca`; verify the logged commands carry the worktree's path and the agent command

## 5. Multirepo groups

- [ ] 5.1 Test `multirepo init`, `add-repo` for two repositories, `new TASK-2`, `status --json` and `open TASK-2 --herdr`; verify each repository's worktree and the group in the status
- [ ] 5.2 Test `clean --yes` of a group once both branches are merged; verify both worktrees are removed

## 6. Bugs, docs and validation

- [ ] 6.1 Fix every bug the tests uncover with its regression test, or record it as a follow-up in design.md when the fix is large; verify `./gradlew test` passes
- [ ] 6.2 Document the end-to-end tests in `docs/architecture/cli.md` and `cli.es.md`; verify `mkdocs build --strict` passes
- [ ] 6.3 Run `./gradlew test` and check the e2e test report lists every scenario with the expected skips; verify the Tests workflow passes on Linux, macOS and Windows on the pull request

## Workflow follow-up

- Archive the change and update `docs/roadmap.md` and `docs/roadmap.es.md`.
- Commit the implementation, bring in `origin/develop`, and open the pull request against `develop` assigned to CiroFeliu.
