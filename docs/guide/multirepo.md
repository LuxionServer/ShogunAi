# Multirepos and groups

Some products live in several repositories: an app, its backend, its docs. A **multirepo** tells ShogunAi which repositories belong together, so one change can span several of them while each repository keeps its own worktree, its own environment and its own agent, and an agent for the whole multirepo coordinates them.

Nothing about a group is stored: ShogunAi computes groups from Git and the multirepo's declaration, so worktrees created by Herdr, Claude Code or plain `git worktree add` are grouped exactly like the ones ShogunAi creates.

## The multirepo

A multirepo is declared by a `.shogunai-multirepo.json` file in a folder you choose, written by the `shogun multirepo` commands (or the desktop app), never by hand:

```json
{
  "version": 2,
  "repos": { "docs": "product-docs", "app": "product-app", "api": "product-api" },
  "worktreesRoot": "../product-worktrees",
  "defaultRepos": ["app", "api"],
  "agent": { "agentCommand": "claude", "useHeadroom": false },
  "workspace": { "alias": "workspace", "default": false }
}
```

- `version` is the declaration's format; ShogunAi writes `2`. A file without it comes from an earlier version, which left `useHeadroom` out when it was on, so its agent keeps Headroom.
- `repos` maps an alias (lowercase letters, digits and hyphens) to a repository's path, relative to the folder or absolute. The repositories don't need to share a parent folder.
- `worktreesRoot` is a common folder under which each repository's worktrees folder is proposed as `<worktreesRoot>/<alias>`.
- `defaultRepos` are the repositories `shogun new <id>` covers when run in the multirepo folder.
- `agent` is the command `shogun open --herdr` runs in the multirepo folder, `claude` by default, and whether Headroom wraps it (`useHeadroom`, off by default).
- `workspace` is optional: `false`, or an object with the `alias` of the [workspace repository](#a-workspace-repository) and whether it is a `default` repository. Files without it keep working.

The folder is outside every repository, or the top level of a **workspace repository** that versions what the repositories share (an `AGENTS.md`, the declaration) and ignores the nested repositories. It's never a subfolder of a repository, so the declaration never ends up inside a repository that isn't yours.

ShogunAi remembers the multirepos it knows in `~/.shogunai/multirepos.json`, a file of its own; `projects.json` and `settings.json` keep their format, so an earlier version keeps working with them.

```bash
cd ~/projects/product
shogun multirepo init                       # writes .shogunai-multirepo.json, worktreesRoot ../product-worktrees
shogun multirepo add-repo app product-app   # reuses product-app's project, or registers one
shogun multirepo add-repo api product-api
shogun multirepo defaults app,api
shogun multirepo agent claude
shogun multirepo show
```

Adding a repository reuses its ShogunAi project unchanged (settings, agents, local files). If it has none, a project is registered with the defaults the desktop app proposes. ShogunAi then proposes `<worktreesRoot>/<alias>` as the project's worktrees folder, so the disk ends up organised like Herdr's spaces (`product-worktrees/app/TASK-12`, `product-worktrees/api/TASK-12`). It asks in a terminal, accepts `--use-worktrees-root` or `--keep-worktrees-folder`, never changes it in `--json` mode, and doesn't ask when the folder already matches. Changing the folder only affects worktrees created afterwards; existing ones stay where they are.

`shogun multirepo remove-repo <alias>` and `shogun multirepo delete` only change the declaration: projects, worktrees, branches, ports and Herdr workspaces are left as they were.

## Groups

A **group** is a branch with exactly the same name, for example `feature/TASK-12`, in worktrees of two or more repositories of the multirepo, wherever each worktree lives. Prefixes aren't interpreted (`feature/TASK-9` and `fix/TASK-9` are different branches), and main checkouts aren't grouped. Worktrees that already exist with matching branches become a group in place: nothing is moved, renamed or changed, and running agents get the group's context at their next `SessionStart`.

```bash
cd ~/projects/product
shogun new TASK-12                  # app and api (the defaults) get feature/TASK-12
shogun new TASK-12 --repos docs     # later: docs joins the group
shogun status                       # every group, per repository, and the ungrouped worktrees
shogun open TASK-12 --herdr         # each worktree under its repository's space, with its own agent
shogun open --herdr                 # the multirepo's agent, in a Herdr workspace of its own
shogun clean --yes                  # remove the groups whose worktrees are all merged and idle
```

- **Creating.** `shogun new <id>` does in each repository what `shogun new <id>` does there today (the project's worktrees folder, the usual folder name, provisioning), except that the branch starts at that repository's `origin/<base>`. Repositories already on the branch are reported and left alone, an existing branch without a worktree is checked out, and a repository that fails doesn't undo the others. It opens nothing. Inside a repository, `shogun new <id>` without `--repos` still creates a single worktree.
- **Status.** In the multirepo folder, `shogun status` lists each group with, per repository, its path, changes, ahead/behind its own base, merged state and agents, then the ungrouped worktrees.
- **Opening.** `shogun open <id> --herdr` opens every worktree of the group as a single worktree opens, under its repository's space with its environment and its project's agent; `<id>` is a branch, or a task id when only one of `feature/<id>` and `fix/<id>` is a group.
- **Cleaning up.** A group is a candidate only when every worktree is clean and merged into its base and no agent runs in any of them. `--yes` removes its worktrees with each repository's usual cleanup and the same options as `shogun clean` (`--delete-branches`, `--stop-emulators`, `--down-compose-projects`…). After the groups, `shogun clean` also lists the loose worktrees of each repository (including the workspace repository) that belong to no group, when they are clean, merged and have no agent, and removes them under the same confirmation.

## Context for the agents

Agents don't need to message each other to know about the group: Claude Code's `SessionStart` hook gives each one its context.

- In a group worktree, the context names the group, the other repositories with the path of their worktree, the repositories not in the group with how to add them, and the `AGENTS.md` of the repositories that have an `openspec` folder.
- In the multirepo folder, it lists the repositories, points to the folder's `AGENTS.md` when there is one, and to `shogun status`, `shogun new` and `shogun open`. In a workspace repository, the main checkout's usual rules follow.

## In the desktop app

The desktop app shows the same multirepos and groups, computed the same way: a folder in the sidebar, a view grouped by change with each group's combined state and actions, group tags in the other views, and dialogs to add a multirepo, create a change and edit its settings, all writing through the same use cases as `shogun multirepo` and `shogun new`. See [Multirepos in the desktop app](desktop-app.md#multirepos).

## A workspace repository

When the multirepo folder is the top level of a Git repository, `shogun new`, `status`, `open` and `clean` run in its main checkout act on the multirepo, and the folder is **detected** as the multirepo's workspace repository with the alias `workspace`, with no declaration needed:

- It joins groups like any other repository: `shogun new TASK-12 --repos workspace,app` creates its worktree in `<worktreesRoot>/workspace/TASK-12`. It is not one of the `defaultRepos` unless you say so.
- `shogun multirepo show` lists it marked `detected`. `shogun multirepo workspace set [--alias <alias>] [--default]` declares it in the file (`"workspace": { "alias": … }`), and `shogun multirepo workspace clear` turns detection off (`"workspace": false`). A `repos` entry with the path `.` also makes the folder the workspace repository, under that alias.
- The folder is not a project of its own: the sidebar lists its worktrees under the multirepo, and `shogun project add` and `shogun app` run in the folder register the workspace repository once, as a member of the multirepo, instead of a loose project. With detection off, the folder is an ordinary project again.
- The multirepo agent (the one running in the folder) is shown in the multirepo view. Its Claude Code session records are kept in the folder's Git directory when it is a repository, and otherwise in a store under `~/.shogunai/agent-sessions/` keyed by the folder, so the `agent-state` hook works in a multirepo folder that isn't a repository too. Only an agent whose working directory is exactly the folder counts as the multirepo agent; one in a member repository belongs to that repository's worktree.

In its linked worktrees, commands behave as in any repository.
