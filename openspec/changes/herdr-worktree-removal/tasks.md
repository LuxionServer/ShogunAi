# Tasks

## 1. Herdr client and the close use case (core)

- [ ] 1.1 Add `openWorktreeWorkspaces(repository): Map<String, String>` (from `worktree list --cwd <repository>`, path → `open_workspace_id`, skipping entries without one) and `closeWorkspace(workspaceId)` (`workspace close <id>`) to the `HerdrClient` port, `CliHerdrClient` and `FakeHerdrClient`; record `worktree list` and `workspace close` fixtures from Herdr 0.9.5 against an isolated Herdr session (never the user's running one), with placeholder paths; verify with `CliHerdrClientTest` cases on the built argv, the parsed list (open, not open, main checkout), the close response and `server_not_running`
- [ ] 1.2 Add `CloseHerdrWorkspacesUseCase(config, herdr)` with `openWorkspaces(paths)` (one lookup, matched with `normalizePath`, empty map on any failure) and `close(workspaces, removedPaths)` (closes only removed paths, collects failures); verify with use case tests for an open worktree, one not open, a path differing only by a trailing slash or backslashes, Herdr not installed, server not running, and a failing close among several
- [ ] 1.3 Give `RemoveWorktreeUseCase` an optional `CloseHerdrWorkspacesUseCase` (look up before `git worktree remove`, close after success) and return `Result<WorktreeRemoval>` with `herdrFailure`; verify with `RemoveWorktreeUseCaseTest` cases: closed after success, not closed when Git fails (including the force-needed failure), close failure reported without failing, `null` keeps today's behaviour
- [ ] 1.4 Give `CleanUpWorktreesUseCase` the same optional parameter, closing the workspaces of removed and pruned worktrees and adding `herdrFailures` to `CleanupReport`; verify with `CleanUpWorktreesUseCaseTest` cases for removed, pruned, failed-removal and close-failure worktrees

## 2. Command line

- [ ] 2.1 Wire a `CliHerdrClient` on the CLI's executor into `shogun clean --yes` and `shogun hook worktree-remove`; print `shogun: couldn't close the Herdr workspace of <path>: <message>` on standard error for each close failure without changing the exit code; verify with `ShogunCliTest` and `HookCommandsTest` cases (closed, Herdr not available, close failure keeps exit code 0)
- [ ] 2.2 In `shogun clean` without `--yes`, print `would close Herdr workspace of <path>` for each candidate open in Herdr; verify with a `ShogunCliTest` case, and that nothing is printed when Herdr isn't available
- [ ] 2.3 Handle `worktree.removed` in `shogun hook herdr` as the design describes (path and branch from `data.worktree`, repository from `data.workspace.worktree.repo_root` or the ShogunAi project containing the path, only with a ShogunAi project, `DeleteMergedBranchUseCase`, notes on standard error); verify with `HookCommandsTest` cases for each scenario of "Delete the branch of worktrees removed from Herdr", using an event fixture recorded from Herdr 0.9.5 with placeholder paths
- [ ] 2.4 Add the `worktree.removed` `[[events]]` entry to `integrations/herdr/herdr-plugin.toml` and set its version to `0.2.0`; update the `hook herdr` line of the usage text; verify `herdr plugin link integrations/herdr` in the isolated session links it without warnings and `herdr plugin list --json` shows both hooks
- [ ] 2.5 Validate end to end against the isolated Herdr session with a locally built `shogun` on its `PATH`: removing an opened worktree with `shogun hook worktree-remove` closes its workspace and emits no `worktree.removed`; `herdr worktree remove` on a merged worktree of a repository with a ShogunAi project deletes its branch, and `herdr plugin log` shows the run; record in the design any envelope detail that differs from it

## 3. Desktop app

- [ ] 3.1 Pass `CloseHerdrWorkspacesUseCase` on the app's `CliHerdrClient` to the remove and cleanup use cases in `AppContainer`, and expose `openWorkspaces` through `HerdrUseCases` (an empty map in `NONE`)
- [ ] 3.2 Look up the worktrees open in Herdr when the remove and "Clean up" dialogs open, without blocking them, and show the notice (remove dialog) or a per-worktree mark with the notice (cleanup dialog); add English and Spanish strings; verify with `WorktreeViewModelTest` (open, not open, lookup still pending or failed) and `StringsTest`
- [ ] 3.3 After a removal or cleanup with Herdr close failures, show the success together with a warning naming the worktree and Herdr's message; verify with `WorktreeViewModelTest`
- [ ] 3.4 Check in the running app with Herdr open (Claude, not delegated): both dialogs show the notice for a worktree opened with "Open in Herdr", and removing it closes the workspace in Herdr

## 4. Documentation and final checks

- [ ] 4.1 Update the Herdr section of `docs/agents.md` and `docs/agents.es.md` (removing from ShogunAi closes the workspace; removing from Herdr deletes the merged branch with the plugin; `herdr plugin update` for existing installs) and `integrations/herdr/README.md`; verify both languages match
- [ ] 4.2 Update `docs/architecture/domain.md`, `infrastructure.md`, `cli.md` and `ui.md` (both languages) for the new port methods, the use case, the CLI output and the dialogs; verify `mkdocs build --strict` passes
- [ ] 4.3 Run `./gradlew test`, `mkdocs build --strict` and `openspec validate herdr-worktree-removal --strict`; verify all pass

## Workflow follow-up

- Archive the change with `/opsx:archive` in the same branch once validated, before opening the pull request to `develop`.
