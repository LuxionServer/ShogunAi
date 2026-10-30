# Proposal

## Why

Opening in Herdr a worktree whose workspace is still open but has no live agent (for example after quitting Claude, which leaves the shell with "Resume this session with…") always splits a new pane next to the old one, so the user ends up with the idle shell on the left and the new agent on the right, every time they come back to the worktree. The spec asked for it on purpose ("the existing panes are kept"), but the old pane is just an empty shell at its prompt that the agent could run in.

## What Changes

- When ShogunAi opens a worktree whose Herdr workspace is open without a live agent, and that workspace has a single pane, in the worktree's folder, whose shell is at its prompt with nothing running in the foreground, ShogunAi runs the agent launch command in that pane instead of splitting a new one. The result is still `started-agent`.
- In every other case (several panes, a pane in another folder, a pane running something such as a dev server, or Herdr unable to report the pane's processes) it keeps splitting a new pane with the worktree's environment, as today.
- No pane is ever closed by this behavior.
- The same rule applies wherever ShogunAi restarts an agent in an open Herdr workspace: the desktop app's "Open in Herdr", `shogun open <target> --herdr`, the worktrees of a group opened with `shogun open <id> --herdr`, and the multirepo agent's own workspace (`shogun open --herdr` in the multirepo folder and its action in the app).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `herdr-integration`: "Reuse the worktree's Herdr workspace" reuses a single idle pane in the worktree instead of always adding one; "Open a worktree in Herdr from the command line" and its "Workspace left without an agent" scenario follow it.
- `command-line-tool`: the "Open without an agent" scenario of "Open a worktree in Herdr from the command line" says the agent starts in the idle pane or in a new one.
- `multirepo-groups`: the group open and the multirepo agent's open in Herdr follow the same reuse rule.
- `multirepo-desktop`: opening the multirepo agent from the app in a workspace without an agent follows the same rule.

## Impact

- `core`: `HerdrClient` gains a read of a pane's foreground processes (`herdr pane process-info`), implemented in `CliHerdrClient` and `FakeHerdrClient`; `HerdrAgentLauncher` picks the pane to reuse; `OpenWorktreeInHerdrUseCase` and `OpenMultirepoAgentInHerdrUseCase` use it.
- Tests: `OpenWorktreeInHerdrUseCaseTest`, `HerdrAgentLauncherTest`, `CliHerdrClientTest`, `GroupStateAndContextTest` (multirepo agent) and the CLI's `open in herdr starts the agent again in a workspace left without one`.
- Docs: `docs/architecture/domain.md`, `docs/guide/desktop-app.md` and `docs/guide/multirepo.md`, with their Spanish translations.
- Requires a Herdr version with `pane process-info` (present in 0.9.5); older versions or platforms where it fails fall back to splitting.
