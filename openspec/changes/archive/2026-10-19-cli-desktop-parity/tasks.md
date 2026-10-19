# Tasks

## 1. Command line

- [x] 1.1 Factor the per-worktree provider release out of cleanup and use it from the desktop removal; verify the existing cleanup and removal tests still pass
- [x] 1.2 Add `shogun remove <target> [--force] [--delete-branch]` with target resolution, the agent guard and provider release; add CLI tests for each spec scenario and verify they pass
- [x] 1.3 Add `shogun repair`; add a CLI test with a moved repository and verify it passes
- [x] 1.4 Document both commands in `docs/guide/cli.md` and `cli.es.md`, update the usage text, and verify `mkdocs build --strict` passes

## 2. Desktop app

- [x] 2.1 Wire `ProvisionWorktreeUseCase` and `CheckProvisionedFilesUseCase` in `AppContainer`, show the provisioning state and "Provision" in the detail; add UI tests for a never-provisioned worktree, a stale copy and an up-to-date one, and verify they pass
- [x] 2.2 Show the environment variables in the detail with "Copy as .env"; add a UI test and verify it passes
- [x] 2.3 Add uninstall to the KMP init script section in settings; add a view-model test and verify it passes
- [x] 2.4 Add the strings in English and Spanish, update the guide page that describes the detail (both languages), and verify `StringsTest` and `mkdocs build --strict` pass

## 3. Validation

- [x] 3.1 Run `./gradlew test` and verify every module passes
- [ ] 3.2 In the running app, provision a worktree created with `git worktree add` and check its files and environment; remove one with `shogun remove` and verify its emulator stops

## Workflow follow-up

- Implement after `process-robustness`, which this change depends on; archive before opening its pull request.
