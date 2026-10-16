# Tasks

## 1. Herdr origin

- [ ] 1.1 Add `WorktreeOrigin.HERDR` and a `herdrWorktreesRoot` parameter to `classifyOrigin` (Claude Code first, then Herdr, then the project's folder); verify with `WorktreeOverviewTest` cases for each origin, including a Herdr folder inside the project's worktrees folder
- [ ] 1.2 Add the `HerdrConfigSource` port and its infrastructure implementation reading `[worktrees] directory` from Herdr's `config.toml` (macOS/Linux and Windows locations, `~` expansion, default `~/.herdr/worktrees` when missing or unparseable); verify with infrastructure tests on temporary config files (set, unset, other tables, comments, invalid file)
- [ ] 1.3 Use it in the desktop overview (label "Herdr" / "Herdr" in `Strings`) and in `shogun status` (`origin=herdr`); verify with `ShogunCliTest`, `StringsTest` and the view model tests that classify origins
- [ ] 1.4 Document the new origin in `docs/architecture/domain.md`, `docs/architecture/cli.md` and `docs/architecture/ui.md` (both languages); verify `mkdocs build --strict` passes

## 2. Plugin and `shogun hook herdr`

- [ ] 2.1 Parse `shogun hook herdr` and implement it reading `HERDR_PLUGIN_EVENT` / `HERDR_PLUGIN_EVENT_JSON` through `environmentVariable`, provisioning `data.worktree.path` like `shogun provision`; update the usage text; verify with `HookCommandsTest` cases for a created worktree, a repository without configuration, another event, missing or invalid variables (exit 2) and a provisioning failure (exit 1)
- [ ] 2.2 Add `integrations/herdr/herdr-plugin.toml` (id, name, version, `min_herdr_version = "0.9.5"`, `platforms`, `[[events]] on = "worktree.created"`, `command = ["shogun", "hook", "herdr"]`) and a short `integrations/herdr/README.md`; verify `herdr plugin link integrations/herdr` links it without warnings and `herdr plugin list --json` shows the hook
- [ ] 2.3 Validate the hook end to end with a locally built `shogun` on `PATH`: in a toy repository with `.shogunai.json` (a secret file and `env` with `envFile`), `herdr worktree create --branch feature/TASK-1` provisions the worktree, and `herdr plugin log` shows the copied files; also check whether `shogun` is found when the Herdr server was auto-started, and record the result in the design
- [ ] 2.4 Add the Herdr section to `docs/agents.md` and `docs/agents.es.md` (install the plugin, what the hook does, how the environment reaches agents in Herdr panes: `envFile`, Claude Code's `SessionStart`, `direnv`) and the command to `docs/architecture/cli.md` (both languages); verify `mkdocs build --strict` passes

## 3. Open in Herdr from the desktop app

- [ ] 3.1 Add the `HerdrClient` port (list workspaces, focus a workspace, create a workspace with cwd, label and env, run a command in a pane), `WorktreeError.HerdrNotInstalled` and `WorktreeError.HerdrCommandFailed`, and a fake in `testFixtures`; verify the fixtures compile
- [ ] 3.2 Implement `CliHerdrClient` on `ShellCommandExecutor` with argv lists, looking for `herdr` also in `~/.local/bin` and `~/.cargo/bin`, parsing only the used JSON fields; verify with infrastructure tests on recorded JSON responses (fixtures captured from Herdr 0.9.5), the built argv and the error mapping
- [ ] 3.3 Implement `OpenWorktreeInHerdrUseCase` (resolve environment, reuse an existing workspace by canonical path, otherwise create with `--env` and run the resolved agent command in the root pane); verify with use case tests for a new workspace with and without environment, an existing workspace, Herdr not installed and a failing command
- [ ] 3.4 Add the "Open in Herdr" row action, its progress state and error messages (not installed with a link to herdr.dev, command failed with Herdr's message) to the worktree screen, with English and Spanish strings; verify with view model tests and `StringsTest`
- [ ] 3.5 Validate in the running app with Herdr attached: opening a worktree with `env` creates a focused workspace whose shell has the variables and runs the agent; opening it again focuses the same workspace; with `herdr` hidden from `PATH` the not-installed message appears. Record whether Herdr groups the workspace under its repository
- [ ] 3.6 Document the action in `docs/architecture/ui.md` and the Herdr section of `docs/agents.md` (both languages); verify `mkdocs build --strict` passes

## 4. Roadmap and final checks

- [ ] 4.1 Mention the Herdr integration in the roadmap's direction section (both languages) as available, and the Herdr agent status in the overview as a possible next step; verify both languages match
- [ ] 4.2 Run `./gradlew test`, `mkdocs build --strict` and `openspec validate herdr-integration --strict`; verify all pass

## Workflow follow-up

- Archive the change with `/opsx:archive` in the same branch once validated, before opening the pull request to `epic/pivot`.
- Add the `herdr-plugin` topic to the GitHub repository so the plugin shows up in Herdr's marketplace (manual, after the pull request is merged and the epic reaches the default branch).
- Share the integration with the Herdr community (Discord) with a short demo using placeholder names.
