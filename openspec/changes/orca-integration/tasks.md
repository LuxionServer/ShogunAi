# Tasks

## 1. Orca port and CLI adapter

- [ ] 1.1 Record JSON fixtures from Orca 1.4.243 on a disposable repository into `core/src/test/resources/orca/` (`status`, `repo list`/`add`/`show`, `worktree list`, `worktree ps` with working/waiting/done agents and with a ShogunAi worktree shown as external, `terminal list`/`show`/`create`/`close`, an `ok: false` error), with placeholder paths; settle which `worktree ps` field tells Orca-created worktrees from external ones and note it in `design.md`. Verify: fixtures contain no home directory or real project names (`grep -r "$HOME"` is empty).
- [ ] 1.2 Add the `OrcaClient` port, the `Orca.kt` model (`OrcaRepo`, `OrcaWorktree`, `OrcaAgent`, `OrcaTerminal`, `OrcaOpening`, `OrcaOutcome`) and the `WorktreeError` cases `OrcaNotInstalled`, `OrcaNotStarted`, `OrcaCommandFailed`, `OrcaAgentNotStarted`, plus `FakeOrcaClient` for tests. Verify: `./gradlew :core:compileKotlin :core:compileTestKotlin` without warnings.
- [ ] 1.3 Implement `CliOrcaClient`: binary lookup (`orca-ide` only on Linux, the app bundle path on macOS), argv commands, tolerant parsing, `ok: false` to `OrcaCommandFailed`, minimum version 1.4.243 from `status`. Verify: `CliOrcaClientTest` against the fixtures and a fake `ShellCommandExecutor` asserts each argv and every parsed field, the Linux lookup and the old-version error.
- [ ] 1.4 Document the port and adapter in `docs/architecture/domain.md`, `infrastructure.md` and `overview.md` and their `.es.md` translations. Verify: `mkdocs build --strict` passes.

## 2. Open in Orca

- [ ] 2.1 Implement `OpenWorktreeInOrcaUseCase`: start Orca when unreachable (30 s), register the repository by canonical path, make the worktree visible only when `worktree list` lacks it, focus the first terminal with a live agent, or create a focused terminal with the environment-carrying command and wait 10 s for its agent. Verify: `OpenWorktreeInOrcaUseCaseTest` with `FakeOrcaClient` covers every scenario of "Open a worktree in Orca from the desktop app", "Make the worktree visible in Orca", "Reuse the worktree's live agent in Orca", "Start Orca when it isn't running", "Report why a worktree can't be opened in Orca" and "Verify the agent started in Orca", including `/tmp` vs `/private/tmp`.
- [ ] 2.2 Build the terminal command: `env 'NAME=value' … <agent>` on POSIX and the PowerShell form used by `OpenWorktreeInTerminalUseCase` on Windows, with the Claude session name from the logic shared with `HerdrAgentLauncher` (extract it without changing Herdr's behaviour). Verify: unit tests for quoting of values with spaces and quotes on both shells, Headroom and non-Claude agents unnamed; `HerdrAgentLauncherTest` still passes.
- [ ] 2.3 Reserve the `ORCA_` prefix in `WorktreeEnvironment`'s reserved names, next to `HERDR_`, and list it in `docs/guide/configuration.md` and its Spanish translation. Verify: a test that a `.shogunai.json` declaring `ORCA_PORT` is invalid; `mkdocs build --strict`.
- [ ] 2.4 Wait for and report the Claude session name, both for a started and for a focused agent. Verify: tests for the scenarios of "Report the session name when opening in Orca".
- [ ] 2.5 Add `shogun open <target> --orca [--agent <name>] [--json]`: exactly one of `--herdr`/`--orca`, the multirepo-folder usage error, text and JSON output, exit codes, help text. Verify: CLI tests for "Open a worktree in Orca from the command line"; `./gradlew :cli:run --args="open --help"` shows `--orca`.
- [ ] 2.6 Add "Open in Orca" to the worktree detail and row context menu (`LaunchKind.ORCA` in the agent picker), its outcome messages and errors in English and Spanish. Verify: desktop tests for "Report the Orca outcome in the desktop app" and i18n key parity; run the app and open a sample worktree in Orca, checking that Orca's window comes to the front (if `terminal switch` doesn't raise it, call `orca open` after it, per `design.md`).
- [ ] 2.7 Document opening in Orca in `docs/agents.md`, `docs/guide/cli.md`, `docs/guide/desktop-app.md` and `docs/guide/installation.md` (Orca optional; `orca-ide` on Linux; the visibility setting ShogunAi changes; `orca agent hooks off`), with Spanish translations. Verify: `mkdocs build --strict`.

## 3. Provisioning and release through Orca's hooks

- [ ] 3.1 Implement `shogun hook orca setup`: provision `ORCA_WORKTREE_PATH`, print copied files, print the outside-the-worktrees-folder note, exit codes 0/1/2. Verify: CLI tests for "Provision worktrees from Orca's setup script" and "Flag worktrees created from Orca outside the worktrees folder".
- [ ] 3.2 Implement `shogun hook orca archive` reusing the resource release of `RemoveWorktreeUseCase` without removing the worktree or branch; unknown action exits 2. Verify: tests for "Release a worktree from Orca's archive script" with fake emulator, simulator and Gradle providers.
- [ ] 3.3 Document the `orca.yaml` snippet and Orca's setup run policy and approval in `docs/guide/configuration.md` and `docs/agents.md`, with Spanish translations. Verify: `mkdocs build --strict`; on a disposable repository with that `orca.yaml`, `orca worktree create --setup run` provisions the worktree and `orca worktree rm --run-hooks` runs the archive.

## 4. Orca in the control tower

- [ ] 4.1 Add the Orca source to `DetectRunningAgentsUseCase`: one `worktree ps` per check, canonical path matching, state mapping, `AgentStateSource.ORCA`, the 30 s backoff, never starting Orca. Verify: tests for "Show the state Orca reports for each agent", "Reading Orca's agents doesn't slow the list" and the Orca scenarios of "Show the state Claude Code's hooks report".
- [ ] 4.2 Add `WorktreeOrigin.ORCA`: classify from Orca's report, remember the folder Orca creates worktrees in, fall back to it or `~/orca/workspaces`, keep ShogunAi worktrees shown in Orca as ShogunAi. Verify: tests for every scenario of "Show where each worktree comes from"; rows and detail show the Orca label in English and Spanish.
- [ ] 4.3 Report Orca in `shogun status`: `stateSource` `orca`, `origin` `orca`, the text line. Verify: CLI tests for the scenarios of "Report each agent's state in status".
- [ ] 4.4 Document Orca states and origin in `docs/agents.md`, `docs/guide/cli.md` and `docs/architecture/ui.md`, with Spanish translations. Verify: `mkdocs build --strict`.

## 5. Removal

- [ ] 5.1 Implement `CloseOrcaTerminalsUseCase` (lookup before removal only when Orca runs, close after success, close and retry once on a locked folder, failures as warnings) and wire it into the desktop remove action and "Clean up", `shogun remove`, `shogun clean --yes` and `shogun hook worktree-remove`. Verify: tests for "Close the Orca terminals of a removed worktree" and "Orca never blocks a removal" on every path.
- [ ] 5.2 Show the Orca notice in the remove and cleanup dialogs and `would close Orca terminals of <path>` in `shogun clean`'s dry run. Verify: tests for "Warn before closing Orca terminals".
- [ ] 5.3 Document the removal behaviour in `docs/guide/cli.md` and `docs/guide/desktop-app.md`, with Spanish translations. Verify: `mkdocs build --strict`.

## 6. Integration check

- [ ] 6.1 Run `./gradlew test` and `openspec validate orca-integration --strict`. Verify: both pass with no warnings.
- [ ] 6.2 End to end on macOS with a disposable repository: `shogun new TASK-1`, `shogun open TASK-1 --orca` (agent with `PORT`, session name `-n`, reachable with inter-agent messages), a second open focuses it, `shogun status` shows its Orca state while it waits on a permission, `shogun remove TASK-1 --force` closes its terminals; and a worktree created from Orca with the `orca.yaml` snippet is provisioned and released. Verify: each step observed, recorded in the handoff note.

## Workflow follow-up

- Archive the change (`/opsx:archive`) once validated, before opening the pull request, updating `docs/roadmap.md` and `docs/roadmap.es.md`: remove this step from the Orca item and keep the remaining ones.
- After merge: update the personal workflow notes that mention `shogun open <id> --herdr` once the runner can be chosen (later step).
