# Proposal

## Why

The change `orca-integration` makes Orca a first-class runner for a single repository's worktrees and leaves multirepos out on purpose: `shogun open <id> --orca` in a multirepo folder fails with a usage error. This change is the roadmap's second Orca step. A group's worktrees and the multirepo agent open in Orca as they already do in Herdr, from the command line and from the multirepo view.

**Depends on `orca-integration`**, which isn't implemented or archived yet. This change reuses its `OrcaClient` port, `OpenWorktreeInOrcaUseCase`, the Orca agent-state source and the Claude session names it already defines for groups and the multirepo agent. It can only be implemented on top of it, and its pull request targets a base that contains it.

An exploration against Orca 1.4.243 on a disposable sample multirepo settled how Orca's model relates to a multirepo:

- **Group worktrees need nothing new.** Each member repository is an ordinary Orca repository, so a group's worktrees open under their own repository exactly as `orca-integration` opens one worktree. When the multirepo was imported in Orca as a group ("Group these repositories?"), `orca repo list` gives each member a `projectGroupId` and its worktrees show nested under the group.
- **A non-Git multirepo folder can host a terminal only if Orca's app created its workspace.** `orca repo add` rejects a folder that isn't a Git repository, and the released CLI can't create projects, project groups or folder workspaces. Orca's GUI can. Importing the folder as a group and adding a "folder workspace" to it, or adding it with "Open as Folder", gives the folder a workspace. `orca worktree ps --json` then lists it, with its path and its agents (`workspaceKind: folder-workspace`), and `orca terminal create --worktree id:folder:<id>` opens a terminal in it.
- **A multirepo folder that is a Git repository** (the workspace repository) registers with `orca repo add`, and its main worktree hosts the terminal.
- **Lineage, for a later step.** `orca worktree set --parent-worktree` attaches an existing ShogunAi worktree to a Git or `folder` workspace, even across repositories, but refuses a folder workspace as the parent ("accepted only by `orca worktree create`").

## What Changes

- `shogun open <id-or-branch> --orca` in the multirepo folder opens every worktree of the group in Orca, each under its own repository, with its environment, its project's agent and its group session name. It focuses the worktrees that have a live agent and starts the agent in the others. Each worktree is reported as `started-agent`, `focused` or failed, and one failure doesn't stop the others. This replaces the usage error that `orca-integration` gives in a multirepo folder.
- `shogun open --orca` without a target in an approved multirepo's folder opens the multirepo agent in Orca:
  - It runs in the Orca workspace whose path is the multirepo folder: a folder workspace, a folder added with "Open as Folder", or the main worktree of the folder's Git repository.
  - It focuses that workspace's live agent, or otherwise starts the multirepo agent in a new focused terminal and checks that it started.
  - When the folder is a Git repository that Orca doesn't know, ShogunAi registers it, as it does for any repository.
  - When the folder isn't a Git repository and Orca has no workspace for it, the open fails with an error that explains how to add the folder in Orca, since only Orca's app can do it.
- The multirepo view gets "Open all in Orca" on each group header and "Open multirepo agent in Orca", next to the Herdr actions, with their outcomes and errors in English and Spanish.
- The multirepo agent's state comes from Orca when Orca reports an agent in the multirepo folder's workspace.
- The "Clean up group" dialog lists the group worktrees that have Orca terminals, as it does with Herdr workspaces.
- The "New change" dialog gets a selector to open the new change's worktrees once they're created: in Herdr or in Orca, each offered only when installed, as "Open all" does for that runner. "Don't open" stays the default, so the dialog keeps opening nothing unless a runner is picked.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `multirepo-groups`: opening a group's worktrees and the multirepo agent in Orca from the command line.
- `multirepo-desktop`: "Open all in Orca" and the Orca warning in group cleanup on the group header, "Open multirepo agent in Orca", opening a new change's worktrees in Herdr or Orca from the "New change" dialog, and the multirepo agent's state reported by Orca.

## Impact

- **core**:
  - `OpenGroupInOrcaUseCase` and `OpenMultirepoAgentInOrcaUseCase`, in the shape of their Herdr counterparts.
  - `OrcaClient` (from `orca-integration`) addresses terminals by an Orca workspace, either a worktree path or a folder workspace id, instead of only a path. It also lists folder workspaces from the `worktree ps` call it already makes.
  - A new `WorktreeError.OrcaMultirepoFolderNotOpen`.
- **cli**: `open --orca` in the multirepo folder, with and without a target, its text and JSON output, and the help text.
- **desktopApp**: the two Orca actions in the multirepo view, the runner selector of the "New change" dialog, their notices and errors in English and Spanish, and the Orca notice in the "Clean up group" dialog.
- **docs**: the multirepo guide, the agents guide and the CLI guide, in English and Spanish; and the roadmap, where this step is done.
- **No new dependencies.** Without Orca, nothing changes.
