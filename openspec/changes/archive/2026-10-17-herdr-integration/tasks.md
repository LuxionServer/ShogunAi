# Tasks

## 1. Herdr origin

- [x] 1.1 Add `WorktreeOrigin.HERDR` and a `herdrWorktreesRoot` parameter to `classifyOrigin` (Claude Code first, then Herdr, then the project's folder); verify with `WorktreeOverviewTest` cases for each origin, including a Herdr folder inside the project's worktrees folder
- [x] 1.2 Add the `HerdrConfigSource` port and its infrastructure implementation reading `[worktrees] directory` from Herdr's `config.toml` (Herdr's own lookup order, `~` expansion, default `~/.herdr/worktrees` when missing or unparseable); verify with infrastructure tests on temporary config files (set, unset, other tables, comments, invalid file)
- [x] 1.3 Use it in the desktop overview (label "Herdr" in `Strings`) and in `shogun status` (`origin=herdr`); verify with `ShogunCliTest`, `StringsTest` and the view model tests that classify origins
- [x] 1.4 Document the new origin in `docs/architecture/domain.md`, `docs/architecture/cli.md` and `docs/architecture/ui.md` (both languages); verify `mkdocs build --strict` passes

## 2. Rescue plugin and `shogun hook herdr`

- [x] 2.1 Parse `shogun hook herdr` and implement it reading `HERDR_PLUGIN_EVENT` / `HERDR_PLUGIN_EVENT_JSON` through `environmentVariable`, provisioning `data.worktree.path` like `shogun provision`; update the usage text; verify with `HookCommandsTest` cases for a created worktree, a repository without configuration, another event, missing or invalid variables (exit 2) and a provisioning failure (exit 1)
- [x] 2.2 Add `integrations/herdr/herdr-plugin.toml` (`min_herdr_version = "0.9.5"`, `platforms`, `[[events]] on = "worktree.created"`, `command = ["shogun", "hook", "herdr"]`) and `integrations/herdr/README.md`; verify `herdr plugin link integrations/herdr` links it without warnings and `herdr plugin list --json` shows the hook
- [x] 2.3 Validate the hook end to end against an isolated Herdr server with a locally built `shogun` on `PATH`: `herdr worktree create` provisions the worktree and `herdr plugin log` shows the copied files; check how an auto-started server gets its `PATH` and record it in the design
- [x] 2.4 After provisioning, when the repository has a ShogunAi project and the worktree is outside its worktrees folder, show a Herdr notification through `HERDR_BIN_PATH` (`herdr` when unset) with `notification show ShogunAi --body …`, noting a failure on standard error without changing the exit code; verify with `HookCommandsTest` cases for outside the folder, inside it, without a project and a failing notification, and against the isolated Herdr server
- [x] 2.5 Rewrite the Herdr section of `docs/agents.md` and `docs/agents.es.md` around the recommended flow (create with ShogunAi, "Open in Herdr", don't use Herdr's "New worktree"), with the plugin as a rescue path (what it provisions, the notification, `shogun` on the `PATH` of the shell that starts Herdr, `herdr plugin log`); update `integrations/herdr/README.md` and the `hook herdr` row of `docs/architecture/cli.md` (both languages); verify `mkdocs build --strict` passes

## 3. Open in Herdr from the desktop app

- [x] 3.1 Reshape the `HerdrClient` port around `openWorktree(repository, path, label)` (workspace id, root pane id, `alreadyOpen`), `splitPane(pane, cwd, environment)`, `closePane(pane)` and `runInPane(pane, command)`, dropping the pane listing and workspace creation; update the models and `FakeHerdrClient`; verify the fixtures compile
- [x] 3.2 Rework `CliHerdrClient` for `worktree open --cwd … --path … --label … --focus`, `pane split … --direction right --cwd … --env … --focus` and `pane close`; replace the recorded JSON fixtures with ones captured from Herdr 0.9.5 (`worktree_opened` new and already open, `pane split`, `pane close`, `server_not_running`), with placeholder paths; verify with infrastructure tests on the built argv, the parsed responses and the error mapping
- [x] 3.3 Rework `OpenWorktreeInHerdrUseCase`: open through Herdr, return `FOCUSED` when already open without touching panes, otherwise resolve the worktree's environment (the same as `shogun env`, providers included, e.g. `ANDROID_SERIAL` and `IOS_SIMULATOR_UDID`), swap the root pane for one with the environment when it isn't empty and run the resolved agent command; verify with use case tests for an environment with a provider variable, no environment, already open, Herdr not installed and a failing command
- [x] 3.4 Add the "Open in Herdr" row action, its progress state and error messages (not installed with a link to herdr.dev, command failed with Herdr's message) to the worktree screen, with English and Spanish strings; verify with view model tests and `StringsTest`
- [x] 3.5 Validate against an isolated Herdr server that the use case opens the worktree grouped under the repository's space with a single pane holding the environment and the agent running, and that opening it again only focuses it; then, in the running app with Herdr open, check the button end to end (left to the user if the session can't drive the app)
- [x] 3.6 Update `docs/architecture/domain.md`, `infrastructure.md` and `ui.md` (both languages) for the reshaped port, client and use case; verify `mkdocs build --strict` passes

## 4. Roadmap and final checks

- [x] 4.1 Mention the Herdr integration in the roadmap's direction section (both languages) as available, and the Herdr agent status in the overview as a possible next step; verify both languages match
- [x] 4.2 Run `./gradlew test`, `mkdocs build --strict` and `openspec validate herdr-integration --strict`; verify all pass

## Workflow follow-up

- Archive the change with `/opsx:archive` in the same branch once validated, before opening the pull request to `develop`.
- Add the `herdr-plugin` topic to the GitHub repository so the plugin shows up in Herdr's marketplace (manual, once the change is released).
- Possible follow-up change: a Herdr action that creates a worktree through ShogunAi and opens it, as a replacement for Herdr's "New worktree".
- Share the integration with the Herdr community (Discord) with a short demo using placeholder names.
