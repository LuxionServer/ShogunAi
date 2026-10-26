# Tasks

## 1. Herdr panes with their agent

- [ ] 1.1 Add `agent: String?`, `agentStatus: String` and `hasLiveAgent` to `HerdrPane`; `HerdrClient.listPanes(workspaceId: String? = null)`; `CliHerdrClient.listPanes` reads `agent` and `agent_status` (`unknown` when missing) and passes `--workspace <id>` when given. Verify with `CliHerdrClientTest` cases using Herdr 0.9.5's real `pane list` output (a pane with `agent: claude, agent_status: idle`, one with only `agent_status: unknown`, and the `--workspace` argv).
- [ ] 1.2 Update `FakeHerdrClient`: `listPanes(workspaceId)` filters by workspace and includes panes it created by `openWorktree`/`createWorkspace`/`splitPane`; a `startsAgents: Boolean = true` switch makes a pane report a live agent after `runInPane`. Verify existing core tests still pass (`./gradlew :core:test`).

## 2. Opening outcome and agent start check

- [ ] 2.1 Replace the `HerdrOpening` enum with `HerdrOpening(outcome: HerdrOutcome, paneId, agent)` and `HerdrOutcome { OPENED, STARTED_AGENT, FOCUSED }`; add `WorktreeError.HerdrAgentNotStarted(paneId, command)` with a message naming pane, command and that the pane is left open. Verify it compiles across modules (`./gradlew compileKotlin compileTestKotlin`) with callers adapted minimally.
- [ ] 2.2 Add the shared launcher (focus the first live-agent pane of a workspace with `focusAgent`; type the command and poll `listPanes(workspace)` every 500 ms for up to 10 s, configurable by constructor). Verify with unit tests under `runTest` virtual time: agent detected after a few polls, never detected → `HerdrAgentNotStarted` after 10 s, `done`/`idle` count as live.
- [ ] 2.3 Rework `OpenWorktreeInHerdrUseCase`: already open with a live agent → `FOCUSED` with its pane and Herdr's agent name, nothing typed; already open without one → split a new pane from the root pane with the resolved environment (even when empty), keep the other panes, launch → `STARTED_AGENT`; fresh → as today plus the start check → `OPENED`. Verify with `OpenWorktreeInHerdrUseCaseTest` covering the three outcomes, the environment on the new pane, no pane closed in the reuse case, and the timeout error.
- [ ] 2.4 Rework `OpenMultirepoAgentInHerdrUseCase` the same way (focus live agent; else split the found pane in the multirepo folder and launch; else create and launch) and let `OpenGroupInHerdrUseCase` carry the new result. Verify with `GroupStateAndContextTest` cases for focused, started-agent, opened and a member whose agent doesn't start while the other still opens.

## 3. CLI

- [ ] 3.1 Update `ShogunCli.open` and `openInMultirepo` text output for the three outcomes with the pane id, and report `HerdrAgentNotStarted` on standard error with exit code 1 (groups: per member via `failureReason`, exit 1). Verify with `ShogunCliTest`/`MultirepoCommandsTest` cases, including the repro (open, agent gone, open again → started-agent).
- [ ] 3.2 Update `openJson` (`herdr` = `opened|started-agent|focused`, `pane_id`, `agent`), `groupOpenJson` (`result` gains `started-agent`, add `pane_id` and `agent`) and `multirepoAgentOpenJson` (keep `opened`, add `herdr` and `pane_id`). Verify with JSON assertions in the CLI tests.
- [ ] 3.3 Update `docs/guide/cli.md` and `docs/guide/cli.es.md` (`open` section and JSON table) and any multirepo guide page that describes opening a group or the multirepo agent, in both languages. Verify by reading the pages against the specs and running `mkdocs build --strict`.

## 4. Desktop app

- [ ] 4.1 Replace the `focused` boolean in `WorktreeSuccess.OpenedInHerdr` and `MultirepoNotice.AgentOpened` with the outcome; map the `focusAgent` shortcut in `ProjectWorktrees.openInHerdr` to `FOCUSED`; update `WorktreeBoard.openMultirepoAgent`, `WorktreeScreen.successMessage` and `NewChangeDialog`'s notice text. Verify with `ProjectWorktreesTest` and `WorktreeBoardTest` cases for the three outcomes and the start failure.
- [ ] 4.2 Add `agentStartedInHerdr(name)` and `multirepoAgentStarted` to `Strings`, `EnglishStrings`, `SpanishStrings`, and `HerdrAgentNotStarted` to `EnglishErrors`, `SpanishErrors` and `ErrorDescription` (pane and command as technical details). Verify with `StringsTest` and the error description tests.

## 5. Architecture docs and integration

- [ ] 5.1 Update `docs/architecture/domain.md`/`.es.md` (`Herdr.kt`, `HerdrClient`, the open use cases), `docs/agents.md` and any Spanish counterpart describing "already open", and `docs/architecture/cli.md`/`.es.md` if they describe the open output. Verify with `mkdocs build --strict`.
- [ ] 5.2 Run `./gradlew test` with no warnings, then check by hand with Herdr 0.9.5: `shogun new TASK-123`, `shogun open TASK-123 --herdr` (opened), quit the agent, run it again (started-agent in the same workspace with the environment), run it again (focused), `--json` for each, and whether `headroom wrap claude` is detected by Herdr; record the Headroom result in `design.md`.

## Workflow follow-up

- Commit the implementation once validated, then archive with `/opsx:archive` in this branch, updating `docs/roadmap.md` and `docs/roadmap.es.md` if affected, before opening the pull request against `develop`.
