# Tasks

## 1. Reading a pane's foreground processes

- [x] 1.1 Add `HerdrPaneProcesses(shellPid, foregroundProcessGroupId)` with `atPrompt` to `domain/model/Herdr.kt` and `suspend fun paneProcesses(paneId: String): HerdrPaneProcesses?` to `HerdrClient`; verified by the build compiling every implementation.
- [x] 1.2 Implement it in `CliHerdrClient` with `listOf(herdr, "pane", "process-info", "--pane", paneId)`, parsing `result.process_info.shell_pid` and `foreground_process_group_id`, and returning `null` on any `herdr` error or unexpected response; verified by `CliHerdrClientTest` cases for a shell at its prompt (the 0.9.5 shape from design.md), a pane running a program, a failed command and a response without those fields.
- [x] 1.3 Implement it in `FakeHerdrClient`: every pane at its prompt by default, `busyPanes` and `unknownProcessPanes` to simulate a running program and a failed read, and `processReads` recording each read; verified by the tests below using them.

## 2. Choosing the pane to reuse

- [x] 2.1 Add `HerdrAgentLauncher.reusablePane(workspaceId, folder): String?` returning the workspace's only pane when it has no live agent, its directory is the folder (`sameDirectory`) and `paneProcesses` reports it at its prompt, reading processes only when the other conditions hold; verified by `HerdrAgentLauncherTest` cases for each condition failing (two panes, a live agent, another folder, a busy pane, unknown processes) and for the reusable pane.

## 3. Using it when opening

- [x] 3.1 In `OpenWorktreeInHerdrUseCase`, when the workspace is already open without a live agent, run the agent in `reusablePane` when there is one, without resolving the environment, splitting or closing anything, and keep today's split with the environment otherwise; verified in `OpenWorktreeInHerdrUseCaseTest` by: the idle single pane in the worktree is reused (`STARTED_AGENT` with that pane, no splits, nothing closed, the command typed there, its session name reported); a busy single pane, two panes, a pane in another folder and unknown processes each split with the worktree's environment and keep the existing panes; the existing tests for an already open worktree without an agent updated to the case that still splits.
- [x] 3.2 In `OpenMultirepoAgentInHerdrUseCase`, try `reusablePane` with the multirepo folder before splitting; verified in `GroupStateAndContextTest` by the multirepo agent reusing its workspace's idle pane and still splitting when that pane is busy (updating `the multirepo agent starts in a new pane of its open workspace without an agent`).
- [x] 3.3 Update the CLI test `open in herdr starts the agent again in a workspace left without one` in `ShogunCliTest` so the second run reports and types into `w1:p1` and nothing is split or closed; verified by `./gradlew :cli:test`.

## 4. Documentation

- [x] 4.1 Update `docs/architecture/domain.md` (`OpenWorktreeInHerdrUseCase`, `HerdrAgentLauncher`, `HerdrClient`) and `docs/architecture/domain.es.md` with the reuse rule, the `process-info` read and the fallback to splitting; verified by reading both pages side by side.
- [x] 4.2 Update `docs/guide/desktop-app.md`, `docs/guide/multirepo.md` and their `.es.md` translations: an open workspace without an agent gets the agent in its idle pane, or a new pane when it has several panes or runs something, and a reused pane keeps the environment from when the workspace was opened; verified with `mkdocs build --strict`.

## 5. Verification

- [x] 5.1 Run `./gradlew test` with every module passing and no compiler warnings.
- [x] 5.2 Check it against the real Herdr: open a scratch worktree with `shogun open <id> --herdr`, quit the agent, open again and confirm the agent restarts in the same pane with a single pane in the workspace; then start `sleep 300` in that pane, quit nothing else, open again and confirm a new pane is split and the `sleep` pane is kept. Close the scratch workspace and remove the worktree afterwards.

## Workflow follow-up

- Archive the change (`/opsx:archive`) once validated, updating `docs/roadmap.md` and `docs/roadmap.es.md` if it affects them, before opening the pull request against `develop`.
