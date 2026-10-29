# Design

## Context

This change builds on `orca-integration` (see proposal.md, Why), which adds:

- The `OrcaClient` port, with `CliOrcaClient` running `orca … --json` as argv.
- `OpenWorktreeInOrcaUseCase`, which follows these steps: start Orca if needed, register the repository, make the worktree visible, focus a live agent or start one, and wait for Orca to report it.
- The Orca source in `DetectRunningAgentsUseCase`, from one `orca worktree ps --json` per check.
- The Claude session names for group worktrees (`<alias>-<id>`) and the multirepo agent (the multirepo's name).

Its port addresses terminals by worktree path (`terminals(worktreePath)`, `createTerminal(worktreePath, …)`, i.e. `--worktree path:<abs>`).

Today Herdr is the only runner for multirepos:

- `OpenGroupInHerdrUseCase` is a loop over `OpenWorktreeInHerdrUseCase` with group session names.
- `OpenMultirepoAgentInHerdrUseCase` finds or creates a Herdr workspace in the multirepo folder.
- The desktop app gets them through `MultirepoActions` (`openAgent` returns `HerdrOpening`).
- The "New change" dialog creates the group and opens nothing.

What Orca 1.4.243 offers for a multirepo folder (verified on a sample, see proposal.md):

| Multirepo folder | How Orca gets a workspace there | What the CLI sees | Terminal selector |
|---|---|---|---|
| Git repository (workspace repository) | `orca repo add` | its main worktree in `worktree list`/`ps` | `path:<folder>` |
| Not Git, added in the app with "Open as Folder" | app only (`repo add` rejects it; the runtime's `kind: folder` isn't exposed by the CLI) | a `folder` repo whose single worktree is the folder | `path:<folder>` |
| Not Git, imported as a group plus a folder workspace | app only (no CLI command creates project groups or folder workspaces) | a `ps` row with `workspaceKind: folder-workspace`, `worktreeId: folder:<id>`, `path`, `agents`; not in `worktree list`, not resolvable by `path:` | `id:folder:<id>` |

Two inconsistencies of 1.4.243 matter here:

- `terminal close --worktree id:folder:<id> --all` doesn't resolve the folder, though `terminal list`/`create` do.
- `terminal list` reports an empty `worktreePath` for a folder workspace's terminals.

## Goals / Non-Goals

**Goals:**
- Group worktrees and the multirepo agent open in Orca with the same outcomes, errors and JSON shape as with Herdr, minus the `opened` outcome Orca doesn't have.
- Use only what the released CLI accepts. Never drive Orca's app or its private runtime methods.

**Non-Goals:**
- Creating Orca project groups or folder workspaces, or moving repositories into a group: the CLI can't. The guide explains the one-time setup in Orca's app.
- Showing a group's worktrees as children of the multirepo agent's workspace (`orca worktree set --parent-worktree`). It's a later roadmap step, and the exploration found that a folder workspace can't be the parent of an existing worktree in 1.4.243.
- The session-start hook and the workflow guide naming `--orca` for the multirepo agent: they follow the runner-choice step.
- A common runner port (see Decisions).

## Decisions

### The group is a loop over the single-worktree open
`OpenGroupInOrcaUseCase(group)` maps each member to `OpenWorktreeInOrcaUseCase(member.worktree, member.config.agentLaunchConfig, groupMemberSessionName(alias, group.title))` and returns `GroupOrcaOpening(alias, path, Result<OrcaOpening>)`, mirroring `GroupOpening`.

- Members are opened one after another, as with Herdr. Orca's app is a single process, and parallel `terminal create` calls would race on focus.
- "Orca not installed" is checked once before the loop, so it's reported once.
- `findGroup` and the ambiguous and unknown id handling are shared with Herdr unchanged.

*Alternative:* register the group's repositories as an Orca project group first. Rejected: the CLI can't, and repositories the person already imported as a group are found by path anyway.

### An Orca workspace target instead of a path in the port
`OrcaClient`'s terminal methods take an `OrcaWorkspace` instead of a `String` path:

- `OrcaWorkspace.Worktree(path)`, rendered as `path:<abs>`.
- `OrcaWorkspace.Folder(id)`, rendered as `id:folder:<id>`.

`agentsByWorktree()` keeps its single `ps` call. Its rows gain `workspaceKind` and `worktreeId`, so a folder workspace is an `OrcaWorktree` with `folderId` set. `closeTerminals` closes a folder workspace's terminals one by one by handle, working around the `--all` inconsistency. Nothing in this change closes them, but the port shouldn't offer a call that fails.

*Alternative:* keep the path-only port and add folder-specific methods. Rejected: it doubles four methods for one selector difference.

If `orca-integration` is still being implemented when this change starts, the port is amended there. Otherwise it's amended here, with the single-worktree use case passing `Worktree(path)`.

### Finding the multirepo agent's workspace, in order
`OpenMultirepoAgentInOrcaUseCase(multirepo)`:

1. Start Orca if needed, as for a worktree.
2. From `agentsByWorktree()`, take the rows whose canonical path equals the multirepo folder: folder workspaces, a `folder` repository's worktree and a Git main worktree alike.
3. When there are several, prefer one with a live agent, then the first in Orca's order.
4. When there are none:
   - If the folder is the top level of a Git repository, register it like any repository and use its main worktree.
   - Otherwise fail with `WorktreeError.OrcaMultirepoFolderNotOpen(folder)`, whose message gives the two ways to add it in Orca's app.
5. Focus a live agent (the first terminal with a non-null `terminalAgent`), or create a focused terminal titled with the session name running the multirepo agent's resolved command, then wait up to 10 s for Orca to report it, as `orca-integration` does.

The multirepo agent gets no environment variables: the folder isn't a worktree, and the Herdr path sends none either.

*Alternative:* call the runtime's `repo.add` with `kind: folder` through Orca's own client library, which works (verified). Rejected: it isn't part of the CLI, so nothing promises it survives Orca's near-daily releases.

*Alternative:* open the multirepo agent's terminal in a member repository's main worktree with `cd` to the folder in the command. Rejected: Orca would attribute the agent and its state to that repository.

### Outcomes and JSON
- **Group:** `{ "branch", "repos": [{ "alias", "path", "result": "started-agent" | "focused" | "failed", "terminal", "agent", "session_name", "error" }] }`, the Herdr shape with `terminal` in place of `pane_id`.
- **Multirepo agent:** `{ "path", "orca": "started-agent" | "focused", "terminal", "agent", "session_name" }`.
- **Text lines and exit codes:** as with Herdr.

### Desktop app: actions next to Herdr's, and a selector in "New change"
- **`MultirepoActions`** gains `openGroupInOrca` and `openAgentInOrca`. Its notices get an Orca variant that carries `OrcaOutcome`.
- **The group header and the multirepo view** add the Orca actions next to Herdr's, shown when Orca is installed, as "Open in Orca" is for a worktree.
- **"Clean up group"** reuses `orca-integration`'s Orca lookup for the dialog's mark.
- **"New change"** gets a selector with three options: "Don't open", "Herdr" and "Orca". A runner is offered only when it's installed. The default is "Don't open", and the selector is hidden when neither runner is installed.
  - After the group is created, the dialog runs the chosen runner's group open on the worktrees that exist.
  - Each repository's line shows its creation result and then its opening result.
  - The choice isn't remembered between dialogs. The runner-choice step can make it default to the chosen runner.

### No common runner port yet
Herdr and Orca each keep their own group and multirepo-agent use cases. They differ in how the multirepo's host is obtained: Herdr creates a workspace, while Orca finds one, registers only a Git folder and otherwise fails. They also differ in their outcomes (`OPENED` exists only in Herdr) and their handles (pane vs. terminal). The roadmap's runner-choice step extracts the port with both adapters complete. This change keeps the duplication small: the group loop and the JSON writer are each a few lines, and `findGroup` and the session names are already shared.

## Risks / Trade-offs

- [The folder workspace selector and `ps`'s `workspaceKind` aren't documented as stable] → Fixtures recorded from 1.4.243 in `core/src/test/resources/orca/`, and tolerant parsing: an unknown kind is treated as a worktree with a path.
- [A non-Git multirepo needs a one-time setup in Orca's app] → A clear error with the steps, in the CLI and the app, and the multirepo guide documents them. A Git multirepo folder needs nothing.
- [Several workspaces with the same path] → A deterministic choice (live agent, then Orca's order), covered by a scenario.
- [Orca keeps showing a ShogunAi-registered member repository outside the person's Orca group] → Documented: importing the multirepo as a group in Orca first keeps the members grouped. ShogunAi never moves repositories between groups.

## Migration Plan

Additive. Nothing changes without Orca, and the Herdr actions are unchanged. Rollback is reverting the change. It merges after `orca-integration`, or together with it on the same base.

## Open Questions

- Whether a later Orca release accepts a folder workspace as the parent in `worktree set`, which the "children of the multirepo agent's workspace" step depends on. This doesn't affect this change.
