# Tasks

## 1. Orca port and adapter

- [x] 1.1 Record `worktree-ps-tool.json` (Claude waiting on `Bash`) and `ports-all.json` (a Python dev server with `advertisedUrl`, listeners outside any workspace) from Orca 1.4.243 on a disposable repository into `core/src/test/resources/orca/`, with placeholder paths. Verify: `grep -r "$HOME" core/src/test/resources/orca` is empty.
- [x] 1.2 Add `AgentActivity`, `AgentTool`, `OrcaPort` and `ObservedPort` to the domain model, `activity` to `OrcaAgent` and `RunningAgent`, and `ports()` to `OrcaClient` and `FakeOrcaClient`. Verify: `./gradlew :core:compileKotlin :core:compileTestKotlin` without warnings.
- [x] 1.3 Parse the agent details (blank as missing, `waitingOn` only while `waiting`) and `orca ports --all --json` (only `kind: "workspace"` rows with `owner.path`, `processName`, `advertisedUrl`) in `CliOrcaClient`. Verify: `CliOrcaClientTest` against both new fixtures and the existing `worktree-ps-done.json` asserts the argv and every parsed field.

## 2. Reading and merging

- [x] 2.1 Read ports in `OrcaWorktreeReader.read()` alongside `agentsByWorktree()` within the 2-second limit, with their own 30-second back-off, and answer `ports(worktrees)` by canonical path, deduplicated and sorted, `null` when unavailable. Verify: `OrcaWorktreeReaderTest` covers every scenario of "Read the ports Orca sees each worktree listen on" and "Reading Orca's ports doesn't slow the list", including `/tmp` vs `/private/tmp` and a ports failure that keeps agents.
- [x] 2.2 Carry the chosen Orca agent's `activity` in `DetectRunningAgentsUseCase.mergeOrca`, also when a Herdr state is kept. Verify: `DetectRunningAgentsUseCaseTest` covers every scenario of "Carry the details Orca reports for each agent".

## 3. Command line

- [x] 3.1 Add `activity` to `agentJson`, `listeningPorts` to each worktree of `shogun status --json`, and `listening=` to its lines. Verify: CLI tests for every scenario of "Report Orca's agent details in status" and "Report the ports Orca sees in status".

## 4. Desktop app

- [x] 4.1 Keep `observedPorts` in `ProjectWorktrees`, refreshed with the agents through `OrcaUseCases.ports`, and wire it in `AppContainer`. Verify: a view-model test that observed ports appear after a check and change on the next one.
- [x] 4.2 Show the agent details under each agent in the detail (labelled, shortened to three lines) and the observed ports in the environment section (reserved ports marked as listening, section shown with only observed ports), with English and Spanish strings. Verify: tests for `shortened` and the port labels, i18n key parity, and the scenarios of "Orca's agent details in the detail" and "Observed ports in the detail".

## 5. Documentation and validation

- [x] 5.1 Document the details and the ports in `docs/architecture/domain.md`, `infrastructure.md`, `docs/guide/desktop-app.md` and `docs/guide/cli.md`, with their `.es.md` translations. Verify: `mkdocs build --strict` passes.
- [x] 5.2 Run `./gradlew test` and check with the real Orca 1.4.243: a worktree with Claude waiting on a tool and a dev server shows the tool, prompt and ports in the desktop app's detail and in `shogun status --json`. Verify: all tests pass and the detail and JSON show them.

## Workflow follow-up

- Archive with `/opsx:archive`, removing the roadmap items "The tool an agent waits on, its prompt and its last message in the worktree's detail" and "The ports Orca sees a worktree listen on, next to the ones ShogunAi reserves" from `docs/roadmap.md` and `docs/roadmap.es.md`.
- Implementation commit, rebase on `origin/develop`, pull request against `develop` assigned to CiroFeliu.
