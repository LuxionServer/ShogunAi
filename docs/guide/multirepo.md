# Multirepos and groups

Some products live in several repositories: an app, its backend, its docs. A **multirepo** tells ShogunAi which repositories belong together, so one change can span several of them while each repository keeps its own worktree, its own environment and its own agent, and an agent for the whole multirepo coordinates them.

Nothing about a group is stored: ShogunAi computes groups from Git and the multirepo's declaration, so worktrees created by Herdr, Claude Code or plain `git worktree add` are grouped exactly like the ones ShogunAi creates.

## The multirepo

A multirepo is declared by a `.shogunai-multirepo.json` file in a folder you choose, written by the `shogun multirepo` commands (or the desktop app), never by hand:

```json
{
  "name": "Product",
  "repos": { "docs": "product-docs", "app": "product-app", "api": "product-api" },
  "worktreesRoot": "../product-worktrees",
  "defaultRepos": ["app", "api"],
  "workspace": { "alias": "workspace", "default": false },
  "env": { "app": { "API_URL": "http://localhost:${port:api.http}" } }
}
```

- `name` is optional: the name the multirepo is shown with in the sidebar, `shogun multirepo show`, the agents' context and Herdr. Without it, the multirepo is named after its folder, which is handy when a workspace repository's folder isn't named after the product. Paths and aliases never depend on it. It can have at most 80 characters and no control or text-direction characters.
- `repos` maps an alias (lowercase letters, digits and hyphens) to a repository's path, relative to the folder or absolute. The repositories don't need to share a parent folder.
- `worktreesRoot` is a common folder under which each repository's worktrees folder is proposed as `<worktreesRoot>/<alias>`.
- `defaultRepos` are the repositories `shogun new <id>` covers when run in the multirepo folder.
- `workspace` is optional: `false`, or an object with the `alias` of the [workspace repository](#a-workspace-repository) and whether it is a `default` repository. Files without it keep working.
- `env` is optional: the [variables the multirepo shares](#shared-variables) with each repository's worktrees.

The folder is outside every repository, or the top level of a **workspace repository** that versions what the repositories share (an `AGENTS.md`, the declaration) and ignores the nested repositories. It's never a subfolder of a repository, so the declaration never ends up inside a repository that isn't yours.

ShogunAi remembers the multirepos it knows in `~/.shogunai/multirepos.json`, a file of its own, together with their approval and the multirepo agent set on this machine; `projects.json` and `settings.json` keep their format, so an earlier version keeps working with them.

The multirepo agent, the command `shogun open --herdr` runs in the multirepo folder, is set with `shogun multirepo agent` (or the multirepo settings in the app) and kept on this machine; it's `claude`, without Headroom, until you set another (`--headroom` wraps it with `headroom wrap`). It's never read from the declaration: a declaration written by an earlier version may still hold an `agent`, which is ignored (`shogun multirepo show` says so) and dropped the next time ShogunAi writes the file.

If a multirepo's folder moves, run `shogun multirepo init` in its new place (or add the folder from the desktop app): instead of refusing, it shows the declaration for review as `shogun multirepo trust` does, and once you approve it, registers it as it is and registers a project for each declared repository that has none, proposing `<worktreesRoot>/<alias>` as `add-repo` does. Both `init` and the desktop app also forget the entries of `~/.shogunai/multirepos.json` whose folder no longer exists; an entry whose folder exists but has no declaration is kept and ignored. A multirepo on a volume that isn't mounted is therefore forgotten too, and `shogun multirepo init` there brings it back.

```bash
cd ~/projects/product
shogun multirepo init                       # writes .shogunai-multirepo.json, worktreesRoot ../product-worktrees
shogun multirepo name "Product"             # optional; --clear goes back to the folder's name
shogun multirepo add-repo app product-app   # reuses product-app's project, or registers one
shogun multirepo add-repo api product-api
shogun multirepo defaults app,api
shogun multirepo agent claude
shogun multirepo show
```

Adding a repository reuses its ShogunAi project unchanged (settings, agents, local files). If it has none, a project is registered with the defaults the desktop app proposes. ShogunAi then proposes `<worktreesRoot>/<alias>` as the project's worktrees folder, so the disk ends up organised like Herdr's spaces (`product-worktrees/app/TASK-12`, `product-worktrees/api/TASK-12`). It asks in a terminal, accepts `--use-worktrees-root` or `--keep-worktrees-folder`, never changes it in `--json` mode, and doesn't ask when the folder already matches. Changing the folder only affects worktrees created afterwards; existing ones stay where they are.

`shogun multirepo remove-repo <alias>` and `shogun multirepo delete` only change the declaration: projects, worktrees, branches, ports and Herdr workspaces are left as they were.

### Approving a multirepo

A declaration can arrive with a clone, for example in a workspace repository, so ShogunAi only uses a multirepo you approved on this machine. A `.shogunai-multirepo.json` that isn't registered, or that changed since you approved it, is ignored: commands behave as outside a multirepo and print a note naming the file and `shogun multirepo trust` (hooks stay silent).

```bash
shogun multirepo trust            # nearest declaration: shows it and asks
shogun multirepo trust --yes      # approve without asking
shogun multirepo trust --revoke   # forget it; the file stays
```

The review lists the declared name, each repository with the path it resolves to, the worktrees root, the workspace repository and the shared variables with their templates, and marks every path outside the multirepo folder's parent. The approval covers exactly that: if a pull or a manual edit changes the name, a repository's path, `worktreesRoot`, the workspace setting or a shared variable, the multirepo stops applying until you review it again. Changing `defaultRepos` doesn't need a new approval. What ShogunAi writes itself (`shogun multirepo init`, `add-repo`, `defaults`…, the desktop app) stays approved, and those commands refuse to write a multirepo that needs review. Multirepos registered before this check existed need one review after updating.

## Groups

A **group** is a branch with exactly the same name, for example `feature/TASK-12`, in worktrees of two or more repositories of the multirepo, wherever each worktree lives. Prefixes aren't interpreted (`feature/TASK-9` and `fix/TASK-9` are different branches), and main checkouts aren't grouped. Worktrees that already exist with matching branches become a group in place: nothing is moved, renamed or changed, and running agents get the group's context at their next `SessionStart`.

```bash
cd ~/projects/product
shogun new TASK-12                  # app and api (the defaults) get feature/TASK-12
shogun new TASK-12 --repos docs     # later: docs joins the group
shogun status                       # every group, per repository, and the ungrouped worktrees
shogun open TASK-12 --herdr         # each worktree under its repository's space, with its own agent
shogun open --herdr                 # the multirepo's agent, in a Herdr workspace of its own
shogun open TASK-12 --orca          # the same in Orca, each worktree under its own Orca repository
shogun open --orca                  # the multirepo's agent, in Orca's workspace for the folder
shogun clean --yes                  # remove the groups whose worktrees are all merged and idle
```

- **Creating.** `shogun new <id>` does in each repository what `shogun new <id>` does there today (the project's worktrees folder, the usual folder name, provisioning), except that the branch starts at that repository's `origin/<base>`. Repositories already on the branch are reported and left alone, an existing branch without a worktree is checked out, and a repository that fails doesn't undo the others. It opens nothing. Inside a repository, `shogun new <id>` without `--repos` still creates a single worktree.
- **Status.** In the multirepo folder, `shogun status` lists each group with, per repository, its path, changes, ahead/behind its own base (its recorded base, or the repository's main branch), merged state (into that base or the main branch) and agents, then the ungrouped worktrees.
- **Opening.** `shogun open <id> --herdr` opens every worktree of the group as a single worktree opens, under its repository's space with its environment and its project's agent: a worktree whose agent is live is focused, one whose workspace is open without an agent gets its agent started in a new pane, and each is reported with its agent's pane. `<id>` is a branch, or a task id when only one of `feature/<id>` and `fix/<id>` is a group. `shogun open --herdr` treats the multirepo agent's workspace the same way.
- **Opening in Orca.** `shogun open <id> --orca` opens every worktree of the group, one after another, as `shogun open --orca` opens one: under its own Orca repository (registered when Orca doesn't know it), with its environment, its project's agent and its group session name. Each is reported as started or focused with its Orca terminal, and one that fails doesn't stop the others. If you imported the multirepo in Orca as a group ("Group these repositories?"), its worktrees show nested under that group; ShogunAi never moves repositories between Orca's groups. `shogun open --orca` runs the multirepo agent in the Orca workspace whose path is the multirepo folder, focusing its live agent or starting the agent in a new terminal there. A multirepo folder that is a Git repository needs nothing: ShogunAi registers it with Orca when needed and uses its main worktree. A folder that isn't a Git repository can only get a workspace from Orca's app, once: add the folder as a project, accept importing its repositories as a group and add a folder workspace to that group; or add it and choose "Open as Folder". Until then `open --orca` fails explaining these steps and changes nothing in Orca.
- **Without a runner flag.** `shogun open <id>` and `shogun open` open in the [runner in use](../agents.md#choosing-the-runner), and the context Claude Code gets in the multirepo folder names it (`shogun open <id> --orca` when Orca is in use).
- **Session names.** When the agent is Claude Code, each session is started with a name: `<alias>-<id>` for each worktree of the group (`app-task-123`, `api-task-123`) and the multirepo's name for the multirepo agent, made unique with `-2`, `-3`… if a live session already has it. `open` reports the names (`as session app-task-123`, `session_name` in `--json`) and `shogun status` shows them on each repository's line (`agents=claude(working, app-task-123)`, `sessions` in `--json`), so the coordinating session can see which session runs in which repository and message each group member by name.
- **Cleaning up.** A group is a candidate only when every worktree is clean and merged into its base and no agent runs in any of them. `--yes` removes its worktrees with each repository's usual cleanup and the same options as `shogun clean` (`--delete-branches`, `--stop-emulators`, `--down-compose-projects`…). After the groups, `shogun clean` also lists the loose worktrees of each repository (including the workspace repository) that belong to no group, when they are clean, merged and have no agent, and removes them under the same confirmation.

## Shared variables

The repositories of a group often need each other's ports: the app worktree of `feature/TASK-12` should talk to the backend running in its own `api` worktree, not to whatever listens on a fixed port. The multirepo's `env` gives that link. It maps an alias to the variables the multirepo adds to that repository's worktrees, and a value can use `${port:<alias>.<key>}`, the port that repository reserves for its key `<key>`:

```json
// product-api/.shogunai.json
{ "env": { "SERVER_PORT": "${port:http}" } }

// .shogunai-multirepo.json
{ "env": { "app": { "API_URL": "http://localhost:${port:api.http}" } } }
```

- **Which port.** A worktree of `app` gets the port of `api`'s worktree on the same branch, its partner in the group. When `api` has no worktree on that branch (a loose `app` worktree, a group without `api`, or `app`'s main checkout), it gets the port of `api`'s main checkout. The port is `api`'s own reservation: resolving `app`'s environment reserves it in `api`'s worktree exactly as `api`'s own `shogun env` would, so both see the same number.
- **Templates.** Besides `${port:<alias>.<key>}`, values can use `${worktree}` (the folder of the worktree receiving the variable) and `$$`. A repository's own ports, `${port}` and `${port:<key>}`, are declared in its `.shogunai.json`, not here. Names and values follow the rules of a repository's `env`, [reserved names](configuration.md#reserved-variable-names) included.
- **Delivery.** Shared variables reach the worktree wherever its own variables do: `shogun env`, the `SessionStart` hook, opening in the terminal or Herdr, and the `envFile`. A variable the repository's own `.shogunai.json` declares wins over a shared one with the same name, and a shared one wins over a provider's.
- **Approval.** They apply while the multirepo is approved, whatever the receiving repository's approval. The referenced repository must be approved, since its ports are only reserved once it is.
- **When it can't be resolved.** If `api` isn't approved, doesn't declare the key, or its worktree can't be read, the variable is left out: `shogun env` says why on standard error and the session's context names it; everything else is delivered.
- **Refreshing.** A session keeps the value it started with. When `api`'s partner worktree is created after `app`'s session started, the next `shogun env`, new session or opening picks it up.

Shared variables are edited with `shogun multirepo env` or in the multirepo settings of the desktop app:

```bash
shogun multirepo env                                                      # list them
shogun multirepo env set app API_URL 'http://localhost:${port:api.http}'  # add or replace one
shogun multirepo env unset app API_URL                                    # remove it
```

Quote the template with single quotes so the shell leaves `${…}` alone. Each change is checked with the rules above before anything is written, and keeps the multirepo approved like every other write of ShogunAi; a multirepo that needs review must be reviewed first. Editing the file by hand still works, and is then reviewed with `shogun multirepo trust`.

Renaming a repository (or declaring the workspace repository under another alias) moves its shared variables and rewrites the `${port:<alias>.<key>}` references to it. Removing a repository, or turning the workspace repository off, drops its own shared variables, and is refused while another repository's variable references it: change or unset those first. An earlier version of ShogunAi that rewrites the declaration drops the shared variables.

## Context for the agents

Agents don't need to message each other to know about the group: Claude Code's `SessionStart` hook gives each one its context.

- In a group worktree, the context names the group, the other repositories with the path of their worktree, the repositories not in the group with how to add them, and the `AGENTS.md` of the repositories that have an `openspec` folder.
- In the multirepo folder, it lists the repositories, points to the folder's `AGENTS.md` when there is one, and to `shogun status`, `shogun new` and `shogun open`. In a workspace repository, the main checkout's usual rules follow.

## In the desktop app

The desktop app shows the same multirepos and groups, computed the same way: a folder in the sidebar, a view grouped by change with each group's combined state and actions, group tags in the other views, and dialogs to add a multirepo, create a change and edit its settings, shared variables included, all writing through the same use cases as `shogun multirepo` and `shogun new`. See [Multirepos in the desktop app](desktop-app.md#multirepos).

## A workspace repository

When the multirepo folder is the top level of a Git repository, `shogun new`, `status`, `open` and `clean` run in its main checkout act on the multirepo, and the folder is **detected** as the multirepo's workspace repository with the alias `workspace`, with no declaration needed:

- It joins groups like any other repository: `shogun new TASK-12 --repos workspace,app` creates its worktree in `<worktreesRoot>/workspace/TASK-12`. It is not one of the `defaultRepos` unless you say so.
- `shogun multirepo show` lists it marked `detected`. `shogun multirepo workspace set [--alias <alias>] [--default]` declares it in the file (`"workspace": { "alias": … }`), `shogun multirepo workspace clear` turns detection off (`"workspace": false`), and `shogun multirepo workspace detect` removes the field, so the folder is detected again as `workspace` (and no longer a default repository). A `repos` entry with the path `.` also makes the folder the workspace repository, under that alias.
- The folder is not a project of its own: the sidebar lists its worktrees under the multirepo, and `shogun project add` and `shogun app` run in the folder register the workspace repository once, as a member of the multirepo, instead of a loose project. With detection off, the folder is an ordinary project again.
- The multirepo agent (the one running in the folder) is shown in the multirepo view. Its Claude Code session records are kept in the folder's Git directory when it is a repository, and otherwise in a store under `~/.shogunai/agent-sessions/` keyed by the folder, so the `agent-state` hook works in a multirepo folder that isn't a repository too. Only an agent whose working directory is exactly the folder counts as the multirepo agent; one in a member repository belongs to that repository's worktree.

In its linked worktrees, commands behave as in any repository.
