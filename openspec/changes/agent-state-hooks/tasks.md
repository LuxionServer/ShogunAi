# Tasks

## 1. Agent session records (core)

- [ ] 1.1 Check the `Notification`, `UserPromptSubmit`, `Stop` and `SessionEnd` hook inputs of the installed Claude Code (2.1.305), including `notification_type`, and adjust the mapping in design.md if they differ; verified by recording a real input for each event as a test fixture.
- [ ] 1.2 Add the `AgentSessionStore` port and its file implementation in the worktree's Git directory (atomic writes, removal, listing, deleting dead records); verified by tests for a linked worktree, the main worktree and "Session records stay out of the working tree" with a real Git repository.
- [ ] 1.3 Generalize `HerdrAgentState` to `AgentState` with `AgentStateSource`, updating every use in `core`, `cli` and `desktopApp`; verified by `./gradlew test` passing unchanged.
- [ ] 1.4 Merge live session records in `DetectRunningAgentsUseCase` (pid liveness, pid reuse, transcript newer than the record, most urgent session, Herdr wins); verified by tests for every scenario of "Show the state Claude Code's hooks report".

## 2. Hook command (cli)

- [ ] 2.1 Add `shogun hook agent-state` with the event mapping and the ancestor walk to the `claude` pid; verified by `HookCommandsTest` cases for each scenario of "Record agent states through Claude Code's hooks".
- [ ] 2.2 Report `state` and `stateSource` in `shogun status` and its JSON, keeping `herdrState`; verified by `StatusJson` tests for both scenarios of "Report each agent's state in status".

## 3. Hook installation (core and cli)

- [ ] 3.1 Add the `ClaudeCodeHooks` use case (desired table, install, uninstall, status, preview, invalid JSON, `CLAUDE_CONFIG_DIR`); verified by unit tests for every scenario of "Install the hooks", "Installing is idempotent and updates old hooks", "Invalid settings are never overwritten", "Uninstall the hooks" and "Check the hooks".
- [ ] 3.2 Add `shogun hooks install|uninstall|status` with `--project`, `--yes`, `--json` and help; verified by CLI tests for "Install in a repository", "Declined" and "Help".

## 4. Desktop app

- [ ] 4.1 Add the "Claude Code" section to Settings with status, Install/Update/Remove, the preview dialog and the `PATH` warning, in English and Spanish; verified by UI tests for "Install from Settings" and "Outdated after an upgrade".
- [ ] 4.2 Add the one-time notice in the worktree view, remembered when dismissed; verified by a UI test for "Dismissed".

## 5. Docs

- [ ] 5.1 Update `docs/agents.md` and `docs/agents.es.md` (one-command installation, what each hook does, agent states outside Herdr, the checked Claude Code version), `docs/guide/cli.md` and `cli.es.md` (`hooks`, `hook agent-state`, the new JSON fields), and the architecture pages for the CLI and infrastructure; verified by `mkdocs build --strict`.

## 6. Verification

- [ ] 6.1 Run `./gradlew test` and check every scenario in the change's specs has a test.
- [ ] 6.2 Install the hooks with `shogun hooks install` built from this branch, run a real Claude session in a test worktree, trigger a permission prompt, grant it and finish, and check the desktop app shows blocked, working and done in turn; then `shogun hooks uninstall` leaves the settings as before.

## Workflow follow-up

- Rebase on `develop` once `worktree-states-redesign` is merged, before validating the states end to end.
- Archive the change with `/opsx:archive` once validated, before opening the pull request, updating the roadmap in both languages.
- Open the pull request against `develop`.
