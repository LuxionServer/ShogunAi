# Tasks

## 1. Runner identity, the runner in use and its preference

- [ ] 1.1 Add `RunnerId` (display name, `shogun open` flag), `RunnerOutcome`, `RunnerOpening`, `GroupRunnerOpening` and `runnerInUse(chosen, installed)` in `domain/model`, with tests for every resolution case in the `runner-choice` spec (`./gradlew :core:test`)
- [ ] 1.2 Add `Settings.runner`, the `RunnerPreferenceRepository` port and `JsonRunnerPreferenceRepository`, decoding an unknown value as no choice, with tests for save, load, a missing key and an unknown value (`./gradlew :core:test`)

## 2. The `AgentRunner` port

- [ ] 2.1 Add the `AgentRunner` port and `HerdrClient.isInstalled()` (default `true`, overridden by `CliHerdrClient`), and verify the build compiles without warnings
- [ ] 2.2 Implement `HerdrAgentRunner` and `OrcaAgentRunner` over the existing use cases, unchanged, with tests that map every Herdr and Orca outcome, a group, the multirepo agent and "not installed" (`./gradlew :core:test`)

## 3. Workflow guide and session context

- [ ] 3.1 Replace `herdrAvailable` with `runner`/`runnerAvailable`, `WorkflowCondition.HERDR` with `RUNNER`, and add `WorkflowRules.forRunner`; `GroupContext.multirepoLines` takes the runner; update `WorkflowRulesTest` and add tests for Orca in the rules and the multirepo lines (`./gradlew :core:test`)

## 4. Command line

- [ ] 4.1 Build the runners map in `ShogunCli`, give `CliCommand.Open` an optional `runner`, and route `open` and `openInMultirepo` through `AgentRunner`, with one `openingLine` and JSON writers over `RunnerOpening`; existing open tests pass unchanged and `ParseArgumentsTest` covers no flag, each flag and both (`./gradlew :cli:test`)
- [ ] 4.2 Use the runner in use in `open` without a flag, `shogun guide`, `shogun guide --plain` and `shogun hook session-start` (Herdr probed, Orca installed), with CLI tests for Orca in use in each spec scenario (`./gradlew :cli:test`)
- [ ] 4.3 Update `shogun help` for the optional runner flag and verify `WorkflowRuleCommandsTest` passes

## 5. Desktop app

- [ ] 5.1 Add `RunnerPreferences` and the "Runner" choice in Settings' Tools section, with English and Spanish strings and a UI test for choosing, a single runner and none installed (`./gradlew :desktopApp:test`)
- [ ] 5.2 Route `MultirepoActions` and "New change" through `AgentRunner` (`openGroup`, `openAgent`, `installed`), start the selector on the runner in use, and keep the notices' texts; existing multirepo tests pass and a test covers the selector's starting runner (`./gradlew :desktopApp:test`)
- [ ] 5.3 Add `openIn(runner, …)` to `ProjectWorktrees`, `openSelectedInRunner` to the board, the Worktree menu item and Cmd+E named after the runner in use, the tour's runner button, and the runner-in-use-first order in the detail, the context menu, the group header and the multirepo agent's menu, with tests for Orca in use (`./gradlew :desktopApp:test`)

## 6. Documentation

- [ ] 6.1 Document the runner choice and `shogun open` without a flag in `docs/agents.md`, `docs/guide/cli.md`, `docs/guide/desktop-app.md` and `docs/guide/multirepo.md`, and the port in `docs/architecture/domain.md`, each with its `.es.md` translation, and verify `mkdocs build --strict` passes

## 7. Integration checks

- [ ] 7.1 Run `./gradlew test` and verify every module passes without warnings
- [ ] 7.2 With real Herdr and Orca 1.4.243: choose Orca in Settings, check the Worktree menu, Cmd+E and the tour open in Orca, `shogun open <id>` opens in Orca, and `shogun guide parallel` and `shogun hook session-start` say `--orca`; switch back to Herdr and check the same

## Workflow follow-up

- Archive the change with `/opsx:archive`, removing the runner-choice item from `docs/roadmap.md` and `docs/roadmap.es.md`.
- Commit the implementation, bring in `origin/develop` resolving the expected conflicts, re-run the tests and open the pull request against `develop`, assigned to CiroFeliu.
