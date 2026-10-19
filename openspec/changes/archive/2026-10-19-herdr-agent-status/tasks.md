# Tasks

## 1. Core

- [x] 1.1 Add `HerdrAgentState` and the optional state and pane id on `RunningAgent`; add `HerdrClient.listAgents()` parsing `herdr agent list` leniently and `focusAgent(pane)`; add client tests with recorded JSON (all states, `unknown`, missing fields) and verify they pass
- [x] 1.2 Merge Herdr agents into `DetectRunningAgentsUseCase` (attribution by `foreground_cwd`, enrichment by name, most urgent state, Herdr failures ignored, 30-second back-off, nothing run without the executable); add use case tests for each spec scenario and verify they pass
- [x] 1.3 Make `worktreeActivity` classify blocked or done agents as `NeedsYou`, and check cleanup keeps worktrees with Herdr-only agents; add `WorktreeOverview` and cleanup tests and verify they pass

## 2. Desktop app

- [x] 2.1 Show the state in the agent chip and the detail's agents section, and update the "Needs you" hint and criterion; add row model and UI tests and verify they pass
- [x] 2.2 Make "Open in Herdr" focus the agent's pane when there is one; add a holder test with a fake client and verify it passes
- [x] 2.3 Add the strings in English and Spanish and verify `StringsTest` passes

## 3. Command line and docs

- [x] 3.1 Show the state in `shogun status` and add `agents` to `status --json`; add CLI tests and verify they pass
- [x] 3.2 Document the states in `docs/agents.md`, `docs/guide/desktop-app.md`, `docs/guide/cli.md` and their Spanish versions, and verify `mkdocs build --strict` passes

## 4. Validation

- [x] 4.1 Run `./gradlew test` and verify every module passes
- [ ] 4.2 In the running app (secondary display), open a worktree in Herdr, start Claude, and check the row goes working → blocked (on a permission prompt, moving to "Needs you") → done; stop Herdr's server and check the rows fall back to agents without state

## Workflow follow-up

- Archive the change before opening its pull request.
