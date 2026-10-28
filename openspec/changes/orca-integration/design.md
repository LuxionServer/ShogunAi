# Design

## Context

The Herdr integration is the model to follow. It works through a domain port, `HerdrClient`, implemented by `CliHerdrClient`, which runs `herdr … --json` as argv and parses `result`/`error`. On top of it sit concrete use cases: `OpenWorktreeInHerdrUseCase`, `HerdrAgentLauncher`, `CloseHerdrWorkspacesUseCase` and the Herdr source in `DetectRunningAgentsUseCase`. `WorktreeOrigin.HERDR` and `AgentStateSource.HERDR` feed the control tower and `shogun status`. There's no generic runner port: `HerdrClient` speaks Herdr's vocabulary (workspace, pane, split), and `HerdrOpening`/`HerdrOutcome` leak into the CLI JSON and the UI.

Orca's model differs:

- Its CLI talks to the running desktop app; `orca open` starts the app and waits for it.
- A repository is registered once. Its worktrees are listed, and those Orca didn't create are hidden unless the repository's external-worktree visibility is `show`.
- Terminals belong to a worktree and are selected by `--worktree path:<abs>`; `terminal create` takes `--command`, `--title` and `--focus`.
- `orca worktree ps --json` lists every worktree with `agents[]` (`state`: `working`, `waiting` or `done`, plus `agentType`, `toolName`, `prompt` and `lastAssistantMessage`). `orca terminal show --json` gives a terminal's `agentIdentity`.
- Every response is `{ id, ok, result | error }`.

The spike's findings are in the proposal. Everything below uses only commands and flags accepted by Orca 1.4.243.

## Goals / Non-Goals

**Goals:**
- Parity with the Herdr integration for single-repository worktrees: open with environment, agent and session name; reuse a live agent; provision and release through repository hooks; read agent states; close terminals on removal; give clear errors.
- Keep Orca optional and never start it except for an explicit open.

**Non-Goals:**
- Multirepo groups and the multirepo agent in Orca (`shogun open <id> --orca` in a multirepo folder fails with a usage error saying groups aren't supported in Orca yet), and choosing a default runner in Settings or the workflow guide. Orca's extras (rich state details, `orca worktree set` comment and status, observed ports, parent/child lineage) are also out. Each is a later roadmap step.
- A generic agent-runner port. It's extracted when the runner becomes a user choice, with both adapters in hand.
- Installing anything into Orca automatically. The `orca.yaml` snippet is documented, not written into repositories.

## Decisions

### A separate `OrcaClient` port, mirroring `HerdrClient`
An `OrcaClient` port in `domain/io` with an argv CLI adapter, `CliOrcaClient`, in the shape of `CliHerdrClient`. It exposes:
- `status()`
- `start()`
- `repos()`, `addRepo(path)` and `showExternalWorktrees(repoId)`
- `worktrees(repoId)` and `agentsByWorktree()`, the latter from one `worktree ps` call
- `terminals(worktreePath)`, `terminalAgent(handle)`, `createTerminal(worktreePath, title, command, focus)`, `switchTo(handle)` and `closeTerminals(worktreePath)`

Responses are parsed tolerantly: unknown fields are ignored, and `ok: false` becomes `WorktreeError.OrcaCommandFailed` with Orca's `error.message`.

*Alternative:* extract an `AgentRunner` port now and make Herdr and Orca two adapters. Rejected for this step: the vocabularies don't line up yet (a workspace with panes versus a repository with terminals), and forcing them into one port before the multirepo and default-runner steps would fix the wrong abstraction. Shared pieces are reused instead: agent command resolution, `ResolveWorktreeEnvironmentUseCase`, the Claude session naming in `HerdrAgentLauncher`, and `AgentState`.

### Finding the binary
The adapter looks in `~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`, then `/Applications/Orca.app/Contents/Resources/bin/orca` on macOS, then `PATH`. On Linux it only looks for `orca-ide`: `orca` there is usually GNOME's screen reader. Not found means `WorktreeError.OrcaNotInstalled`.

### Opening: register, make visible, start the agent
1. Start Orca if needed. `status()` reads `result.runtime.reachable`. When the runtime isn't reachable, `start()` runs `orca open --json` with a 30 s timeout; a timeout or failure gives `WorktreeError.OrcaNotStarted`. Only the open path does this.
2. Register the repository. Find it among `repos()` by canonical path, resolving symbolic links: `/tmp` and `/private/tmp` both appear on macOS. When it's missing, `addRepo` it.
3. Make the worktree visible. When `worktrees(repoId)` doesn't list it, set the repository's visibility to `show`. Checking the listing instead of reading the setting covers `inherit`, whose effective value the CLI doesn't expose.
4. Reuse a live agent. For each terminal of `terminals(worktreePath)`, a non-null `terminalAgent(handle)` is a live agent, and the first one gets `switchTo`. Outcome `FOCUSED`.
5. Otherwise start the agent. `createTerminal(worktreePath, title = <session name or agent>, command, focus = true)`, then poll `terminalAgent` every 500 ms for up to 10 s. Outcome `STARTED_AGENT`; a timeout gives `WorktreeError.OrcaAgentNotStarted(handle, command)`.

*Alternative:* `orca agent launch --agent claude`. Rejected: it applies Orca's launch settings, whose default is full access, and it can't run the project's own agent command (Headroom, custom agents).

### Delivering the environment through the command
`terminal create` has no environment flag, so the command carries the variables. On POSIX it's `env 'NAME=value' … <agent command>`, quoted with `ShellQuoting`. On Windows it's the PowerShell form that `OpenWorktreeInTerminalUseCase` already builds for "Open in terminal". Values come from `ResolveWorktreeEnvironmentUseCase`, so only an approved repository's variables are sent, as with Herdr.

*Alternative:* `eval "$(shogun env)"` in the command. Rejected: it needs `shogun` on the PATH of Orca's shell, runs a second process, and is POSIX-only.

### Agent states: one `ps` per check, never starting Orca
`DetectRunningAgentsUseCase` gets an Orca source next to Herdr's. `agentsByWorktree()` runs `orca worktree ps --limit 1000 --json` once per check. Rows are matched to ShogunAi worktrees by canonical path, and `working`/`waiting`/`done` map to `WORKING`/`BLOCKED`/`DONE`. Precedence keeps today's rule (the most urgent state wins per agent), with `AgentStateSource.ORCA`.

The source runs nothing when the binary is missing. It uses the same 30 s backoff after a failure or a 2 s timeout. It checks `status()` first and never calls `start()`. If `ps` itself turns out to start the app, the check stays mandatory.

### Origin: what Orca reports, plus a remembered folder
While Orca answers, a worktree it lists that wasn't marked as external, with `creatorProvenance` set, is `WorktreeOrigin.ORCA`. ShogunAi remembers the parent folder of those worktrees (Orca's `<workspaceDir>/<repo>/<name>` layout) in its app data. While Orca can't be asked, worktrees inside that folder, or inside `~/orca/workspaces` by default, are `ORCA`. Precedence is Claude Code, then Herdr, then Orca, then ShogunAi, then "other location". A ShogunAi worktree shown in Orca stays `SHOGUNAI` because Orca marks it external.

*Alternative:* read `workspaceDir` from Orca's settings. Rejected: they live in a SQLite database whose schema isn't a contract.

### `shogun hook orca <setup|archive>` driven by Orca's variables
Orca's hooks are shell scripts with environment variables, not events, so the action is an argument and the path comes from `ORCA_WORKTREE_PATH`. `ORCA_ROOT_PATH` is used to find the repository when the worktree's Git metadata can't.
- `setup` reuses `ProvisionWorktreeUseCase` and the outside-the-worktrees-folder note of `hook herdr`, printed to standard output because Orca has no notification command.
- `archive` reuses the resource-release step of `RemoveWorktreeUseCase` without removing the worktree or the branch, since Orca deletes both.

The documented snippet:

```yaml
scripts:
  setup: |
    shogun hook orca setup
  archive: |
    shogun hook orca archive
```

### Closing terminals on removal
`CloseOrcaTerminalsUseCase` follows `CloseHerdrWorkspacesUseCase`:
- Look up `terminals(worktreePath)` before removing, only when `status()` says Orca is running.
- Call `closeTerminals(worktreePath)`, which is `terminal close --worktree path:<abs> --all`, after the removal succeeds, or before the single retry when the folder is locked.
- A close failure is a warning.

It's wired into the same five removal paths. Orca prunes its own record of a worktree that `git worktree remove` deleted.

### Errors and outcomes
- **New errors:** `WorktreeError.OrcaNotInstalled`, `OrcaNotStarted`, `OrcaCommandFailed(message)` and `OrcaAgentNotStarted(handle, command)`.
- **Outcomes:** `OrcaOpening(outcome, terminalHandle, agent, sessionName)` with `OrcaOutcome.STARTED_AGENT | FOCUSED`. There's no "opened workspace" outcome, because Orca shows a worktree as soon as it's visible.
- **CLI JSON:** `--json` prints `{"orca": "started-agent" | "focused", "terminal": …, "session_name": …}`.
- **Command line:** `open` accepts exactly one of `--herdr` or `--orca`.
- **Desktop app:** `LaunchKind.ORCA` joins the agent picker, and the action sits next to "Open in Herdr" in the detail and the context menu.

## Risks / Trade-offs

- [Orca's CLI changes almost daily, and its docs run ahead of the binary] → Only flags accepted by 1.4.243. JSON fixtures recorded from it in `core/src/test/resources/orca/`. Tolerant parsing. `status()` reads `appVersion`, and an Orca older than 1.4.243 gets `OrcaCommandFailed` naming the minimum version.
- [Setting a repository's external-worktree visibility to `show` changes a per-machine Orca setting] → Only for the repository being opened, and only when the worktree would be hidden. The guide says so.
- [`terminal switch` may switch Orca's tab without raising its window] → Verified in the tasks. If it doesn't raise the window, `orca open` (which focuses the running app) runs after it.
- [Orca's hooks in `~/.claude/settings.json` run in every Claude session] → Orca's business, not ShogunAi's. The guide mentions `orca agent hooks off` for people who use Orca without its states. ShogunAi's own hooks are unaffected (verified).
- [Windows: the CLI path and the shell Orca's terminals run aren't verified] → Covered by tests with fakes, and listed as a roadmap check on a real Windows machine, as for other Windows-only behaviour.
- [Path matching across `/tmp` and `/private/tmp` or case-insensitive file systems] → Canonical paths on both sides, with tests.

## Migration Plan

Additive and optional: nothing changes for people without Orca. Rollback is reverting the change.

## Open Questions

- Whether Orca marks external worktrees with a field of their own in `worktree ps` (the spike saw `creatorProvenance` and `cliCreated` only on worktrees Orca created). The task that records the fixtures settles which field tells them apart. It doesn't change the approach.
