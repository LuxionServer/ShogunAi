# Proposal

## Why

ShogunAi plugs into the tools that run agents, and today only one of them is a first-class runner: Herdr. A spike compared Herdr with Orca (MIT, by Stably AI), a desktop app that also creates worktrees, runs Claude Code, Codex and other agents in them and reports their state. The decision is to support Orca fully: everything ShogunAi does with Herdr, plus what Orca offers that Herdr doesn't. This change is the first step: opening, provisioning and overseeing the worktrees of a single repository in Orca. Multirepo groups, choosing the default runner and Orca's extras follow in later changes listed in the roadmap.

What the spike verified against Orca 1.4.243 on a sample repository:

- **Provisioning.** The `scripts.setup` hook in `orca.yaml` runs inside each new worktree with `ORCA_ROOT_PATH`, `ORCA_WORKTREE_PATH` and `ORCA_WORKSPACE_NAME`, but not the branch. `shogun provision "$ORCA_WORKTREE_PATH"` copied the declared files, reserved the port and wrote the env file. `scripts.archive` runs inside the worktree before Orca removes it; from Orca's CLI it only runs with `--run-hooks`. Orca deletes the branch itself.
- **Opening a ShogunAi worktree.** `orca repo add`, `orca repo set --external-worktree-visibility show` and `orca terminal create --worktree path:<worktree> --command …` open a worktree created by `shogun new` in Orca, under its repository. The agent saw the worktree's environment. Without the visibility override, the terminal opens but the worktree stays hidden in Orca's sidebar. Orca's CLI needs the desktop app running, and `orca open` starts it.
- **Agent states.** `orca worktree ps --json` lists each worktree's agents with `working`, `waiting` or `done`, the tool an agent waits on, its prompt and its last message. It also reported `waiting` on Claude's folder-trust prompt before any hook ran. The agent kept running, and its state came back, after Orca was quit and reopened.
- **Coexistence.** Orca's hooks in `~/.claude/settings.json` sit alongside ShogunAi's and Herdr's. They exit at once outside Orca's terminals (about 13 ms), and `orca agent hooks off` removes them cleanly. A Claude session in Orca is reachable with inter-agent messages, like one in Herdr.
- **Pitfalls.** By default, agents started by Orca's own launcher run with full access (`--dangerously-skip-permissions`) and get branches named `<git user>/<name>`. Orca's documentation also describes flags its released CLI rejects (`worktree create --branch`, `worktree ps --agent-wait`). ShogunAi therefore launches the project's agent command itself and only relies on what the released CLI accepts.

## What Changes

- "Open in Orca" in the worktree's detail and its row's context menu, and `shogun open <target> --orca`:
  - The worktree opens in Orca under its repository, registering the repository with Orca and making its worktrees visible when needed.
  - It opens a focused terminal with the worktree's environment, running the project's agent launch command.
  - When the worktree already has a live agent, that agent's terminal is focused instead.
  - Orca is started when it isn't running.
  - The open fails with a clear error when Orca isn't installed, its command fails or the agent doesn't start.
- `shogun hook orca`, for the `setup` and `archive` scripts of `orca.yaml`:
  - On setup, it provisions the worktree like `shogun provision` and says when the worktree is outside the project's worktrees folder.
  - On archive, it frees what `shogun remove` frees for the worktree (emulator, simulator, idle Gradle daemons) before Orca removes it.
  - The guide documents the `orca.yaml` snippet.
- In the control tower and `shogun status`, an agent carries the state Orca reports (working, blocked for Orca's `waiting`, done), with `stateSource` `orca`. Worktrees Orca created are labelled as created by Orca.
- Every ShogunAi removal path closes the Orca terminals of the worktrees it removes. Removal dialogs warn about it beforehand, and Orca never blocks a removal.

## Capabilities

### New Capabilities

- `orca-integration`: opening worktrees in Orca with their environment and the project's agent, provisioning and releasing worktrees through Orca's repository hooks, and closing Orca's terminals when ShogunAi removes a worktree.

### Modified Capabilities

- `worktree-overview`: worktrees created by Orca get their own origin, and agents carry the state Orca reports.
- `command-line-tool`: the `orca` state source and origin in `shogun status`. `shogun open --orca` and `shogun hook orca` are specified in `orca-integration`.
- `claude-session-names`: Claude sessions started in Orca get the same `-n` name as those in Herdr, which inter-agent messages address them by.

## Impact

- **core**:
  - A new `OrcaClient` port with a CLI adapter, run as argv like `CliHerdrClient`.
  - Use cases to open in Orca, close Orca terminals and read Orca's agents.
  - `DetectRunningAgentsUseCase` gets an Orca source.
  - `WorktreeOrigin` and `AgentStateSource` gain `ORCA`.
  - New `WorktreeError` cases.
- **cli**: `open --orca`, `hook orca`, the status JSON, and dry-run lines in `clean`.
- **desktopApp**: the "Open in Orca" action, its outcome messages and errors in English and Spanish, and the Orca notice in the remove and cleanup dialogs.
- **docs**: the agents guide, the CLI guide and the configuration guide (the `orca.yaml` snippet), and the architecture pages for the new port; the roadmap in English and Spanish.
- **No new dependencies.** Orca is optional, like Herdr: without it nothing changes.
