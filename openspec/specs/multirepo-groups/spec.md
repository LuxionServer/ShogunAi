# multirepo-groups Specification

## Purpose
Lets one change span several repositories: a multirepo declares which repositories belong together, a group is the same branch in several of them, each worktree keeps its own agent, and a multirepo agent oversees every group. Groups are computed from Git and the declaration alone, so they work the same with worktrees created outside ShogunAi.

## Requirements

### Requirement: Declare a multirepo
A multirepo SHALL be declared by a `.shogunai-multirepo.json` file in a folder outside every Git repository or at the top level of one, never inside one: `repos` mapping each alias to a repository's path (relative or absolute), `worktreesRoot`, an optional `name`, an optional `defaultRepos` and an optional `agent`. The repositories SHALL NOT need to share a parent folder. Aliases SHALL use only lowercase letters, digits and hyphens.

#### Scenario: Repositories side by side
- **WHEN** `~/projects/product/.shogunai-multirepo.json` declares `docs`, `app` and `api` as `product-docs`, `product-app` and `product-api`, `worktreesRoot` `../product-worktrees` and `defaultRepos` `app` and `api`
- **THEN** `~/projects/product` is a multirepo with three repositories

#### Scenario: Repositories in different places
- **WHEN** `~/work/platform/.shogunai-multirepo.json` declares `web` as `/src/web-client` and `backend` as `~/code/backend`
- **THEN** it's a valid multirepo whose repositories stay where they are

#### Scenario: Folder that is a workspace repository
- **WHEN** `~/projects/product` is itself a Git repository that versions the shared `AGENTS.md` and ignores the nested repositories, and holds the declaration
- **THEN** it's a valid multirepo and the declaration can be versioned in it

#### Scenario: No default repositories or agent
- **WHEN** the declaration has no `defaultRepos` and no `agent`
- **THEN** it's valid, creating a group from the multirepo folder requires `--repos`, and the multirepo agent is `claude`

### Requirement: Reject an invalid multirepo declaration
ShogunAi SHALL reject a `.shogunai-multirepo.json` whose JSON is invalid, whose alias is invalid, whose repository path doesn't exist or isn't a Git repository, whose `worktreesRoot` is missing, whose `defaultRepos` names an unknown alias, or whose folder is inside a Git repository without being its top level, naming the problem and the file and exiting with code 1.

#### Scenario: Unknown default alias
- **WHEN** `defaultRepos` contains `web`, which isn't a key of `repos`
- **THEN** every command that reads the declaration prints that `web` isn't a declared repository of the file and exits with code 1

#### Scenario: Repository missing
- **WHEN** `repos.api` points to a folder that doesn't exist or isn't a Git repository
- **THEN** the command names the alias, the path and the file, and exits with code 1

### Requirement: Know the multirepos of the machine
ShogunAi SHALL keep the list of known multirepos in `~/.shogunai/multirepos.json`, a file of its own that holds only the paths of their declarations, and SHALL find the multirepo of a directory that is the multirepo folder or inside it, or inside one of its repositories or their worktrees, wherever they are. A repository SHALL belong to at most one multirepo.

#### Scenario: From a repository outside the multirepo folder
- **WHEN** a command runs inside a worktree of `~/code/backend`, which is declared by `~/work/platform`
- **THEN** it finds the multirepo `~/work/platform`

#### Scenario: Declaration moved or deleted
- **WHEN** a path in `~/.shogunai/multirepos.json` no longer has a declaration
- **THEN** that entry is ignored and commands in the repositories it declared behave as outside a multirepo

#### Scenario: Repository already in another multirepo
- **WHEN** the user adds to a multirepo a repository that another known multirepo declares
- **THEN** the command names the other multirepo, changes nothing and exits with code 1

### Requirement: Create a multirepo from the command line
`shogun multirepo init [--worktrees-root <dir>] [--name <name>]` SHALL turn the current folder into a multirepo: write a `.shogunai-multirepo.json` with no repositories, `worktreesRoot` set to the given folder or, by default, `../<folder>-worktrees`, and the given `name`, and add it to the known multirepos. When the folder already has a valid declaration, it SHALL register it instead (see "Register an existing multirepo"). It SHALL refuse a folder inside a Git repository without being its top level.

#### Scenario: Initialize
- **WHEN** the user runs `shogun multirepo init` in `~/projects/product`
- **THEN** `~/projects/product/.shogunai-multirepo.json` is written with `worktreesRoot` `../product-worktrees`, the multirepo is known, and the command prints the file's path

#### Scenario: Initialize with a name
- **WHEN** the user runs `shogun multirepo init --name Product` in `~/projects/product-workspace`
- **THEN** the declaration has `name` `Product` and `worktreesRoot` `../product-workspace-worktrees`

#### Scenario: Inside a repository
- **WHEN** the folder is a subfolder of a Git repository
- **THEN** the command says a multirepo is never declared inside a repository, writes nothing and exits with code 1

#### Scenario: Top level of a workspace repository
- **WHEN** the folder is the top level of a Git repository
- **THEN** the multirepo is created there

#### Scenario: Already a multirepo
- **WHEN** the folder already has a valid `.shogunai-multirepo.json`
- **THEN** the command doesn't rewrite it and registers it as an existing multirepo instead of refusing

### Requirement: Configure a multirepo from the command line
From the multirepo folder or anywhere it applies, `shogun multirepo add-repo <alias> <path>` and `remove-repo <alias>` SHALL add and remove a repository, `defaults <alias>[,<alias>…]` (or `--clear`) SHALL set `defaultRepos`, `agent <command> [--name <name>] [--no-headroom]` SHALL set the multirepo agent and `worktrees-root <dir>` the proposed worktrees folder, each validating its input and rewriting only the declaration.

#### Scenario: Add a repository
- **WHEN** the user runs `shogun multirepo add-repo app product-app` and `product-app` is a Git repository's main worktree
- **THEN** `repos.app` is `product-app`, stored relative to the multirepo folder, or absolute when it's outside it

#### Scenario: Add an invalid repository
- **WHEN** the alias is taken or invalid, or the path isn't a Git repository's main worktree
- **THEN** the command says why, changes nothing and exits with code 1

#### Scenario: Remove a repository
- **WHEN** the user runs `shogun multirepo remove-repo api`
- **THEN** `api` leaves `repos` and `defaultRepos`, and its project, worktrees and branches are left as they are

#### Scenario: Set the agent
- **WHEN** the user runs `shogun multirepo agent "codex --full-auto" --name Codex`
- **THEN** the multirepo agent is launched as `headroom wrap codex --full-auto` and shown as `Codex`; with `--no-headroom` as `codex --full-auto`

#### Scenario: Unknown default
- **WHEN** the user runs `shogun multirepo defaults app,web`
- **THEN** the command says `web` isn't a repository of the multirepo and exits with code 2

### Requirement: Reuse the projects already registered
Adding a repository to a multirepo SHALL use the ShogunAi project already registered for it, matched by canonical path, keeping its settings, agents and local files; when there is none, it SHALL register one with the defaults the desktop app proposes for a new project. The project list SHALL keep its format.

#### Scenario: Registered project enters a multirepo
- **WHEN** `product-app` already has a project with main branch `develop`, two agents and secret files, and the user adds it to a multirepo and declines the proposed worktrees folder
- **THEN** the project is unchanged and the multirepo uses its main branch, agents and secret files

#### Scenario: Repository without a project
- **WHEN** `product-api` has no project and the user adds it to a multirepo
- **THEN** a project `product-api` is registered with the desktop app's defaults, its worktrees folder being the proposed one when the user accepts it

#### Scenario: Older version reads the project list
- **WHEN** a version of ShogunAi without multirepos reads the project list after a multirepo was configured
- **THEN** it lists the same projects with the same settings

### Requirement: Propose a worktrees folder per repository
Adding a repository to a multirepo SHALL propose `<worktreesRoot>/<alias>` as its project's worktrees folder and SHALL change it only when the user accepts, saving it in the project's usual worktrees folder setting. The command line SHALL ask when it runs in a terminal and accept `--use-worktrees-root` or `--keep-worktrees-folder` instead. Changing the folder SHALL only affect worktrees created afterwards.

#### Scenario: Accept the proposal
- **WHEN** the user adds `app` to a multirepo whose `worktreesRoot` is `~/projects/product-worktrees` and accepts
- **THEN** `app`'s project keeps its worktrees in `~/projects/product-worktrees/app`, so `shogun new TASK-12` there creates `~/projects/product-worktrees/app/TASK-12`

#### Scenario: Existing worktrees stay
- **WHEN** `app`'s project had worktrees in `~/projects/product-app-worktrees` and its folder changes
- **THEN** those worktrees stay where they are and keep working, and only new ones go to the new folder

#### Scenario: Folder already matches
- **WHEN** `app`'s project already keeps its worktrees in `<worktreesRoot>/app`
- **THEN** nothing is asked and the project is left as it was

#### Scenario: Decline or not asked
- **WHEN** the user declines, passes `--keep-worktrees-folder`, or runs with `--json` without either option
- **THEN** the project's worktrees folder is left as it was

### Requirement: Delete a multirepo
`shogun multirepo delete` SHALL remove the declaration and forget the multirepo, and SHALL leave every project, worktree, branch, reservation and Herdr workspace as it was.

#### Scenario: Multirepo deleted
- **WHEN** the user runs `shogun multirepo delete` in `~/projects/product` while two groups have worktrees open in Herdr
- **THEN** `.shogunai-multirepo.json` is removed, the multirepo is no longer known, the projects and the worktrees stay as they were with their Herdr workspaces, and each repository behaves as before it joined

### Requirement: Show a multirepo from the command line
`shogun multirepo show [--json]` SHALL print the multirepo's name and folder, its proposed worktrees folder, each repository with its alias, path, project, worktrees folder and base branch, the default repositories and the multirepo agent.

#### Scenario: Show
- **WHEN** the user runs `shogun multirepo show --json` in the multirepo folder
- **THEN** the output is one JSON object with `name`, `path`, `worktreesRoot`, `repos` (each with `alias`, `path`, `project`, `worktreesFolder` and `base`), `defaultRepos` and `agent`

#### Scenario: Not a multirepo
- **WHEN** the user runs any `shogun multirepo` subcommand other than `init` where no multirepo applies
- **THEN** the command says no multirepo was found and exits with code 1

### Requirement: A multirepo folder that is a repository
When the multirepo folder is the top level of a Git repository, `shogun new`, `status`, `open` and `clean` run in its main checkout SHALL act on the multirepo, that repository MAY be declared as one more repository with the path `.`, and `shogun hook session-start` there SHALL print the multirepo's context followed by the usual rules of a main checkout. In that repository's linked worktrees, commands SHALL behave as in any repository.

#### Scenario: New in a workspace repository
- **WHEN** `~/projects/product` is a Git repository and a multirepo with `defaultRepos` `app` and `api`, and the user runs `shogun new TASK-12` there
- **THEN** `app` and `api` get their worktrees and the workspace repository gets none

#### Scenario: Workspace repository in a group
- **WHEN** the workspace repository is declared as `workspace` with path `.` and the user runs `shogun new TASK-12 --repos workspace,app`
- **THEN** the workspace repository and `app` each get a worktree on `feature/TASK-12`, in their projects' worktrees folders

#### Scenario: Linked worktree of the workspace repository
- **WHEN** the user runs `shogun new TASK-13` inside a linked worktree of the workspace repository
- **THEN** a single worktree of the workspace repository is created, as before

#### Scenario: Session in a workspace repository
- **WHEN** Claude Code starts a session in the main checkout of the workspace repository
- **THEN** the context describes the multirepo and then gives the main checkout's usual rules

### Requirement: Each repository keeps its own project and base branch
Every repository of a multirepo SHALL keep its own `.shogunai.json` and ShogunAi project, which apply to its worktrees exactly as before. The base branch of each repository SHALL be its project's main branch, or the detected main branch when it has none, resolved preferably on `origin`.

#### Scenario: Different provisioning per repository
- **WHEN** `app` declares the Gradle and emulator providers and `api` declares the Docker Compose provider
- **THEN** `app`'s worktree of a group is provisioned with the Gradle and emulator providers and `api`'s with Docker Compose, each as `shogun provision` would

#### Scenario: Base branch per repository
- **WHEN** `app`'s project has main branch `develop` and `api` has no main branch configured and its `origin/HEAD` points to `main`
- **THEN** the group's `app` worktree starts at `origin/develop` and its `api` worktree at `origin/main`

### Requirement: A group is a branch shared by repositories
A group SHALL be a branch name that has worktrees in two or more repositories of the multirepo, with exactly the same name, wherever each worktree lives and whoever created it. Branch names SHALL be compared whole, without interpreting prefixes. A group SHALL have no state of its own.

#### Scenario: Existing worktrees grouped
- **WHEN** `product-app` has a worktree at `~/projects/product-app-worktrees/TASK-7` and `product-api` one at `~/old/api-task-7` created by Herdr, both on `feature/TASK-7`
- **THEN** both form the group `feature/TASK-7` in place

#### Scenario: Worktrees created before the multirepo in its worktrees folder
- **WHEN** `app`'s and `api`'s projects already keep worktrees in `~/projects/product-worktrees/app` and `.../api`, each has `TASK-1` on `feature/TASK-1` created by `shogun new`, and the user then declares the multirepo with `worktreesRoot` `../product-worktrees`
- **THEN** both worktrees form the group `feature/TASK-1` at the same paths with the same names, the proposed worktrees folders already match so nothing is asked or changed, and `shogun new TASK-2` creates `.../app/TASK-2` and `.../api/TASK-2` next to them

#### Scenario: Main checkouts
- **WHEN** the main checkouts of `app` and `api` are both on `develop`
- **THEN** they don't form a group; only linked worktrees are grouped

#### Scenario: Branch in a single repository
- **WHEN** only `product-app` has a worktree on `feature/TASK-8`
- **THEN** it isn't a group and appears among the ungrouped worktrees

#### Scenario: Different prefixes
- **WHEN** `app` has `feature/TASK-9` and `api` has `fix/TASK-9`
- **THEN** they are two different branches and neither is a group

### Requirement: Grouping never changes a worktree
Joining or leaving a group, or a multirepo being created, changed or deleted, SHALL NOT move, rename or modify any worktree, nor change its name, environment, reservations, devices, Compose project, agent sessions or Herdr workspace.

#### Scenario: Existing worktree joins a group
- **WHEN** `~/old/api-task-7`, with a reserved port, a Compose project and an open Herdr workspace, becomes part of the group `feature/TASK-7`
- **THEN** it stays at the same path with the same environment, Compose project and Herdr workspace

#### Scenario: Running session after the update
- **WHEN** a Claude Code session was already running in a worktree that becomes part of a group
- **THEN** it keeps working, and the group's context reaches it at its next `SessionStart`

### Requirement: Create a group from the command line
`shogun new <id> --repos <alias>[,<alias>…] [--fix] [--fetch]` SHALL create, in each named repository, what `shogun new <id> [--fix]` creates there today, in that project's worktrees folder and with the usual name, except that the branch starts at `origin/<base>`, after fetching each repository with `--fetch`. It SHALL open nothing, and print each created worktree's absolute path on standard output and one line per repository on standard error.

#### Scenario: Fetch first
- **WHEN** the user runs `shogun new TASK-123 --fetch` in the multirepo folder
- **THEN** each repository is fetched from `origin` before its worktree is created

#### Scenario: Existing branch without a worktree
- **WHEN** `feature/TASK-123` already exists in `api` and has no worktree
- **THEN** `api`'s worktree is created on that branch without moving it

#### Scenario: Unknown alias
- **WHEN** the user runs `shogun new TASK-123 --repos app,web`
- **THEN** the command says `web` isn't a repository of the multirepo, lists the declared aliases, creates nothing and exits with code 2

#### Scenario: Options that don't apply to a group
- **WHEN** the user combines `--repos`, or creates a group from the multirepo folder, with `--branch`, `--from` or `--worktrees-root`
- **THEN** the command says the option doesn't apply to a group and exits with code 2

### Requirement: Where a group is created from
`shogun new <id>` SHALL create or extend a group when run in the multirepo folder, using `defaultRepos` when `--repos` isn't given, or with `--repos` inside any of its repositories or their worktrees. Inside a repository or one of its worktrees without `--repos` it SHALL create a single worktree exactly as before.

#### Scenario: Default repositories
- **WHEN** the user runs `shogun new TASK-12` in `~/projects/product`, whose `defaultRepos` are `app` and `api` and whose projects keep worktrees in `~/projects/product-worktrees/app` and `.../api`
- **THEN** `~/projects/product-worktrees/app/TASK-12` and `~/projects/product-worktrees/api/TASK-12` are created on `feature/TASK-12`, each from its repository's `origin/<base>` and provisioned, nothing is opened, and both paths are printed

#### Scenario: Explicit repositories from inside a repository
- **WHEN** the user runs `shogun new TASK-123 --repos app,docs` inside `product-app`
- **THEN** `app` and `docs` each get a worktree on `feature/TASK-123`

#### Scenario: Inside a repository without --repos
- **WHEN** the user runs `shogun new TASK-123` inside `product-app`
- **THEN** a single worktree is created as before, starting at the current worktree's HEAD

#### Scenario: No repositories to create
- **WHEN** the user runs `shogun new TASK-123` in the multirepo folder and it declares no `defaultRepos`
- **THEN** the command says to pass `--repos` and exits with code 2

### Requirement: Adding repositories to a group is idempotent
Running `shogun new <id>` for a branch that some repositories already have SHALL create only the worktrees that are missing, and SHALL report as already in the group, without touching them, the repositories that already have a worktree on the branch, wherever it lives.

#### Scenario: Add a repository later
- **WHEN** `app` and `api` have worktrees on `feature/TASK-123` and the user runs `shogun new TASK-123 --repos docs`
- **THEN** `docs` gets its worktree on `feature/TASK-123` and `app` and `api` are left as they are

#### Scenario: Worktree elsewhere already on the branch
- **WHEN** `api` already has `~/old/api-task-123` on `feature/TASK-123`
- **THEN** `api` is reported as already in the group with that path and no second worktree is created

#### Scenario: Destination taken
- **WHEN** `api`'s project folder already contains `TASK-123` and it isn't a worktree on the branch
- **THEN** that repository fails as `shogun new` does today when the destination exists, and the other repositories are still created

### Requirement: A failing repository doesn't undo the others
When creating the worktree of one repository fails, ShogunAi SHALL handle that repository as a single `shogun new` does today (rolling back a failed creation, keeping a worktree whose post-creation command failed), keep the other repositories, report the failure naming the repository, and exit with code 1.

#### Scenario: One repository fails
- **WHEN** `shogun new TASK-123` creates `app` but copying `api`'s declared files fails
- **THEN** `app` stays created, `api`'s worktree and new branch are removed, standard error names `api` and the failure, and the command exits with code 1

#### Scenario: Post-creation command fails
- **WHEN** `api`'s post-creation command fails
- **THEN** `api`'s worktree is kept and reported as failed with its path, as `shogun new` does, and the command exits with code 1

#### Scenario: No base branch
- **WHEN** a repository has no main branch configured and none can be detected
- **THEN** that repository fails saying its base branch couldn't be resolved, and the others are still created

### Requirement: JSON result of group creation
`shogun new <id> --json` creating or extending a group SHALL print one JSON object with the `branch` and a `repos` list with each repository's `alias`, `path`, `result` (`created`, `existing` or `failed`), the post-creation commands it skipped and, for a failure, its error.

#### Scenario: Mixed result
- **WHEN** `shogun new TASK-123 --json` creates `app`, finds `api` already on the branch and fails on `docs`
- **THEN** the object has `branch` `feature/TASK-123` and lists `app` as `created`, `api` as `existing` with its path and `docs` as `failed` with its error, and the command exits with code 1

### Requirement: Open a group's worktrees in Herdr
`shogun open <id-or-branch> --herdr` in the multirepo folder SHALL open every worktree of the group whose branch is `<branch>`, or `feature/<id>` or `fix/<id>` when only one of them is a group, as `shogun open --herdr` opens a single worktree: under its repository's space, with its own environment and its project's default agent, focusing the ones already open. A worktree that fails SHALL NOT stop the others.

#### Scenario: Open a group
- **WHEN** the user runs `shogun open TASK-123 --herdr` in `~/projects/product` and `feature/TASK-123` is a group with `app` and `api`
- **THEN** `app` opens under `product-app`'s space with `app`'s environment and agent, `api` under `product-api`'s space with its own, and the command reports each

#### Scenario: Partly open
- **WHEN** `app` is already open in Herdr
- **THEN** it's focused without typing anything and `api` is opened

#### Scenario: Ambiguous id
- **WHEN** both `feature/TASK-5` and `fix/TASK-5` are groups and the user runs `shogun open TASK-5 --herdr`
- **THEN** the command lists both branches, opens nothing and exits with code 2

#### Scenario: Unknown group
- **WHEN** no group matches `TASK-9`
- **THEN** the command says so and exits with code 1

#### Scenario: One worktree fails
- **WHEN** opening `api` fails in Herdr
- **THEN** `app` is still opened, the failure names `api` and Herdr's message, and the command exits with code 1

#### Scenario: Agent option
- **WHEN** the user passes `--agent` while opening a group
- **THEN** the command says each worktree uses its project's agent and exits with code 2

### Requirement: Open the multirepo agent in Herdr
`shogun open --herdr` without a target in the multirepo folder SHALL open a focused Herdr workspace of its own in the multirepo folder, outside every repository's space, and run the multirepo agent there; when Herdr already has that workspace open it SHALL focus it without typing anything.

#### Scenario: Open
- **WHEN** the user runs `shogun open --herdr` in `~/projects/product` and the multirepo agent is `claude`
- **THEN** a focused Herdr workspace opens in `~/projects/product` labelled `product` and runs `headroom wrap claude`

#### Scenario: Already open
- **WHEN** Herdr already has a workspace whose pane is in `~/projects/product`
- **THEN** it's focused and nothing is typed into it

#### Scenario: Herdr not installed or failing
- **WHEN** Herdr isn't installed or its command fails
- **THEN** the command reports it as it does for a worktree and exits with code 1

### Requirement: Tell a group worktree's agent about its group
`shogun hook session-start` in a worktree that belongs to a group SHALL add to its context the group's branch, the worktree's own alias, every other repository of the group with its worktree path, the multirepo's repositories not in the group with how to add them, and the path of the `AGENTS.md` of each repository that has an `openspec` folder, taken from its worktree in the group or else its main checkout.

#### Scenario: Session in a group worktree
- **WHEN** Claude Code starts a session in `api`'s worktree on `feature/TASK-123` and `app` also has one
- **THEN** the context says this is `api` in the group `feature/TASK-123`, lists `app` with the path of its worktree, and says `docs` can be added with `shogun new TASK-123 --repos docs`

#### Scenario: Where OpenSpec lives
- **WHEN** only `product-app` has an `openspec` folder
- **THEN** the context points to the `AGENTS.md` of `app`'s worktree in the group, or of `product-app`'s main checkout when `app` isn't in the group

#### Scenario: Worktree outside a group
- **WHEN** the session starts in a worktree that isn't in a group
- **THEN** the context is the same as before

### Requirement: Tell the multirepo agent about the multirepo
`shogun hook session-start` in the multirepo folder SHALL print a context naming the multirepo, its repositories with their paths, the multirepo folder's `AGENTS.md` when it exists, and saying that `shogun status` lists the groups, `shogun new <id>` creates one and `shogun open <id> --herdr` opens its worktrees, without writing to `CLAUDE_ENV_FILE`.

#### Scenario: Session in the multirepo folder
- **WHEN** Claude Code starts a session in `~/projects/product`
- **THEN** the context lists `docs`, `app` and `api` with their paths and the three commands, nothing is written to `$CLAUDE_ENV_FILE`, and the command exits with code 0

#### Scenario: Shared instructions
- **WHEN** `~/projects/product/AGENTS.md` exists
- **THEN** the context says to read it for what all the repositories share

### Requirement: Show groups from the command line
`shogun status` in the multirepo folder SHALL list every group by branch with, per repository, its alias, path, uncommitted changes, commits ahead and behind its base, whether it's merged into the base and the agents running in it, then the ungrouped worktrees of each repository, and with `--json` print the same data as one JSON object. Inside a repository it SHALL behave as before.

#### Scenario: Multirepo folder
- **WHEN** the user runs `shogun status` in `~/projects/product` with groups `feature/TASK-1` (app, api) and `feature/TASK-2` (app, docs) and a loose worktree of `api`
- **THEN** both groups are listed with one line per repository showing its path, changes, ahead/behind, merged state and agents, followed by the loose `api` worktree under the ungrouped ones

#### Scenario: JSON
- **WHEN** the user runs `shogun status --json` in the multirepo folder
- **THEN** the output is one JSON object with a `groups` list, each with `branch` and a `repos` list with each repository's `alias`, `path`, `base`, `uncommittedChanges`, `ahead`, `behind`, `merged` and `agents`, and an `ungrouped` list

#### Scenario: Status of one repository unavailable
- **WHEN** the status of one repository's worktree can't be read
- **THEN** that repository says why and the rest of the group is still shown

### Requirement: Select groups to clean up
`shogun clean` in the multirepo folder SHALL list as candidates the groups whose every worktree is clean and merged into its base, or has a missing folder, and has no agent running in it; remove nothing without `--yes`; and report each other group with the reason it's kept.

#### Scenario: Merged group
- **WHEN** the group `feature/TASK-1` has `app` and `api`, both clean and merged into their bases, and no agent runs in them
- **THEN** `shogun clean` lists the group with its two worktrees and says to add `--yes`, removing nothing

#### Scenario: Partly merged group
- **WHEN** `app`'s worktree on `feature/TASK-2` is merged but `api`'s isn't
- **THEN** the group isn't a candidate and the output says it's kept because `api` isn't merged

#### Scenario: Agent in a worktree of the group
- **WHEN** every worktree of `feature/TASK-1` is merged but an agent runs in `api`'s
- **THEN** the group is kept and the output names the agent and the repository

#### Scenario: JSON
- **WHEN** the user runs `shogun clean --json` in the multirepo folder
- **THEN** the output is one JSON object listing the candidate groups with their worktrees, and the kept groups with their reasons

### Requirement: Remove a group whole
`shogun clean --yes` in the multirepo folder SHALL remove every worktree of each candidate group, wherever it lives, with its repository's existing cleanup, accepting the same options as `shogun clean`: `--delete-branches` to delete the merged branches and the provider flags (Compose projects, emulators, simulators, Gradle daemons, DerivedData).

#### Scenario: Confirmed
- **WHEN** the user runs `shogun clean --yes` and `feature/TASK-1` is a candidate
- **THEN** both worktrees are removed, their Herdr workspaces are closed, the branches are kept, and each removal is reported

#### Scenario: Delete branches
- **WHEN** the user runs `shogun clean --yes --delete-branches`
- **THEN** the group's merged branches are also deleted in each repository

#### Scenario: A removal fails
- **WHEN** removing one worktree of a candidate fails
- **THEN** the other worktrees are still removed, the failure is reported on standard error and the command exits with code 1

#### Scenario: Provider cleanup flags
- **WHEN** the user runs `shogun clean --yes --stop-emulators --down-compose-projects` and `feature/TASK-1` is removed
- **THEN** the emulator of `app`'s worktree is stopped and the Compose project of `api`'s worktree is taken down, as `shogun clean` with those flags does in each repository

### Requirement: Nothing changes without a multirepo
With no known multirepo, every command, the desktop app and the files under `~/.shogunai` SHALL behave exactly as before; a newer version SHALL only add the file of known multirepos, which earlier versions ignore.

#### Scenario: No multirepo
- **WHEN** the user updates ShogunAi and never creates a multirepo
- **THEN** `shogun new`, `status`, `open`, `clean`, the hooks and the desktop app behave as before, and `~/.shogunai/projects.json` and `settings.json` are unchanged

### Requirement: Workspace repository of a multirepo
A multirepo folder that is the top level of a Git repository SHALL be treated as its workspace repository, with the alias `workspace`, unless the declaration says otherwise. The declaration MAY hold `workspace`: an object with an optional `alias` (and `default`, whether it joins `defaultRepos`), or `false` to turn detection off. A workspace repository's worktrees folder SHALL be `<worktreesRoot>/<alias>`.

#### Scenario: Detected without declaration
- **WHEN** `~/projects/product` is the top level of a Git repository and its declaration has no `workspace` field and no repository with the path `.`
- **THEN** the workspace repository is `workspace`, its worktrees go in `~/projects/product-worktrees/workspace` and it isn't in `defaultRepos`

#### Scenario: Declared alias
- **WHEN** the declaration has `"workspace": { "alias": "docs-root", "default": true }`
- **THEN** the workspace repository is `docs-root` and `shogun new TASK-12` in the multirepo folder also creates its worktree

#### Scenario: Detection turned off
- **WHEN** the declaration has `"workspace": false` and the folder is a Git repository
- **THEN** the folder isn't a repository of the multirepo, `--repos workspace` is rejected as unknown and its agent isn't attributed to a repository

#### Scenario: Folder isn't a repository
- **WHEN** the multirepo folder isn't a Git repository
- **THEN** there is no workspace repository, whatever the `workspace` field says

#### Scenario: Already declared as a repository
- **WHEN** the declaration lists the folder in `repos` with the path `.`
- **THEN** that alias is the workspace repository and `workspace` isn't added as a second one

#### Scenario: Invalid workspace field
- **WHEN** `workspace.alias` is invalid or collides with a key of `repos`
- **THEN** every command that reads the declaration names the problem and the file, and exits with code 1

### Requirement: Show and declare the workspace repository
`shogun multirepo show` SHALL list the workspace repository, marked `detected` when the declaration doesn't state it, with `workspace` in its `--json` object. `shogun multirepo workspace set [--alias <alias>] [--default]` SHALL declare it, and `workspace clear` SHALL set `"workspace": false`.

#### Scenario: Show a detected workspace
- **WHEN** the user runs `shogun multirepo show` in a multirepo whose folder is a Git repository and whose declaration says nothing about it
- **THEN** the output lists `workspace` as detected, with the command to declare it

#### Scenario: Declare it
- **WHEN** the user runs `shogun multirepo workspace set --default`
- **THEN** the declaration holds `"workspace": { "alias": "workspace", "default": true }` and nothing else changes

#### Scenario: Turn it off
- **WHEN** the user runs `shogun multirepo workspace clear`
- **THEN** the declaration holds `"workspace": false` and the folder is no longer a repository of the multirepo

#### Scenario: Not a repository
- **WHEN** the user runs `workspace set` in a multirepo folder that isn't a Git repository
- **THEN** the command says there is no workspace repository, changes nothing and exits with code 1

### Requirement: Create a group with the workspace repository
`shogun new <id> --repos <aliases>` in the multirepo folder SHALL accept the workspace repository's alias, declared or only detected, and create its worktree on the group's branch in `<worktreesRoot>/<alias>/<id>`, with its base branch and provisioning resolved as for any repository.

#### Scenario: Detected only
- **WHEN** the user runs `shogun new TASK-12 --repos workspace,app` in a multirepo whose folder is a Git repository
- **THEN** `workspace` and `app` each get a worktree on `feature/TASK-12`, the workspace repository's in `~/projects/product-worktrees/workspace/TASK-12`

#### Scenario: Unknown alias lists the workspace
- **WHEN** the user passes `--repos web`
- **THEN** the declared aliases listed in the error include the workspace repository's

#### Scenario: Worktree inside a repository where members are ignored
- **WHEN** the workspace repository ignores the nested member repositories
- **THEN** its worktree contains only what it versions, and the members stay in the multirepo folder

### Requirement: Multirepo agent state and session records
A session of the multirepo agent SHALL be attributed to the multirepo when its working directory is exactly the multirepo folder, never a subfolder. Its Claude Code session records SHALL be written by `shogun hook agent-state` in the folder's Git directory when it is a Git repository and otherwise in a store under `~/.shogunai/` keyed by the folder.

#### Scenario: Folder is not a repository
- **WHEN** a Claude Code session runs in a multirepo folder that isn't a Git repository and its hooks fire
- **THEN** `agent-state` records the session in the store under `~/.shogunai/` and the multirepo's agent shows its state

#### Scenario: Folder is a repository
- **WHEN** the multirepo folder is a Git repository
- **THEN** the records go to its Git directory as for any worktree

#### Scenario: Subfolder is a member repository
- **WHEN** an agent runs in `~/projects/product/product-app`
- **THEN** it's attributed to `app`'s worktree and not to the multirepo agent

#### Scenario: Session ended
- **WHEN** the session ends or its process is gone
- **THEN** its record is removed from the store as for a worktree's

### Requirement: Clean loose worktrees from the multirepo folder
`shogun clean` in the multirepo folder SHALL also list, after the groups, the worktrees of each member repository and of the workspace repository that belong to no group and are eligible for cleaning as in a single repository, and SHALL remove them under the same confirmation and options as the groups.

#### Scenario: Loose worktree of a member
- **WHEN** `api` has a merged worktree on `feature/TASK-3` that no other repository shares
- **THEN** `shogun clean` in the multirepo folder lists it under `api` after the groups

#### Scenario: Group worktrees aren't listed twice
- **WHEN** a worktree belongs to a group
- **THEN** it's listed only as part of that group

#### Scenario: Workspace repository
- **WHEN** the workspace repository has a loose merged worktree
- **THEN** it's listed under `workspace` and removed like any other

#### Scenario: Nothing to clean
- **WHEN** there are no eligible groups and no loose worktrees
- **THEN** the command says there is nothing to clean and exits with code 0

### Requirement: The multirepo folder is not a project of its own
`shogun app` and `shogun project add` run in, or given, a multirepo folder that is a Git repository SHALL NOT register it as a project: they SHALL act on the workspace repository's project only when the workspace repository is declared or detected, and otherwise report that the folder is a multirepo. A project already registered for the folder SHALL be treated as the workspace repository's.

#### Scenario: App opened from the multirepo folder
- **WHEN** the user runs `shogun app` in the multirepo folder
- **THEN** the app opens the multirepo and no project is registered for the folder

#### Scenario: Project add in the folder
- **WHEN** the user runs `shogun project add` in the multirepo folder with the workspace detected
- **THEN** the project of the workspace repository is registered once, as a repository of the multirepo

#### Scenario: Detection off
- **WHEN** the declaration has `"workspace": false`
- **THEN** both commands register the folder as an ordinary project

### Requirement: Display name of a multirepo
A multirepo SHALL be named by the optional `name` of its declaration when it's set and not blank, and by its folder's name otherwise. That name SHALL be used wherever ShogunAi names the multirepo: `shogun multirepo show`, the multirepo context printed to agents and the Herdr workspace opened for the multirepo. The aliases, the default `worktreesRoot` and every path SHALL keep depending on the folder only.

#### Scenario: Declared name
- **WHEN** `~/projects/product-workspace/.shogunai-multirepo.json` declares `name` `Product`
- **THEN** `shogun multirepo show` prints `Product  ~/projects/product-workspace` on its first line, and the multirepo context printed by `shogun hook session-start` names it `Product`

#### Scenario: No name
- **WHEN** the declaration has no `name`, or a blank one
- **THEN** the multirepo is named `product-workspace`, after its folder, as before

#### Scenario: Older version reads the declaration
- **WHEN** a version of ShogunAi without display names reads a declaration with `name`
- **THEN** it ignores the field and names the multirepo after its folder

### Requirement: Name a multirepo from the command line
`shogun multirepo name <name>` SHALL set the declaration's `name` and `shogun multirepo name --clear` SHALL remove it, rewriting only the declaration. A blank name SHALL be rejected as a usage error.

#### Scenario: Set the name
- **WHEN** the user runs `shogun multirepo name "Product"` in the multirepo folder
- **THEN** the declaration's `name` is `Product` and the command prints the multirepo as `show` does

#### Scenario: Clear the name
- **WHEN** the user runs `shogun multirepo name --clear`
- **THEN** the declaration has no `name` and the multirepo is named after its folder again

#### Scenario: Blank name
- **WHEN** the user runs `shogun multirepo name "  "`
- **THEN** the command says the name can't be blank, changes nothing and exits with code 2

### Requirement: Forget multirepos whose folder is gone
`shogun multirepo init` SHALL remove from `~/.shogunai/multirepos.json` every entry whose folder no longer exists, keeping the entries whose folder exists even when its declaration is missing or invalid.

#### Scenario: Folder moved away
- **WHEN** `~/.shogunai/multirepos.json` lists `~/projects/product/.shogunai-multirepo.json`, `~/projects/product` no longer exists, and the user runs `shogun multirepo init` anywhere
- **THEN** that entry is removed from `~/.shogunai/multirepos.json`

#### Scenario: Declaration deleted but folder present
- **WHEN** an entry's folder exists but has no declaration
- **THEN** the entry is kept, and it's still ignored as before

### Requirement: Register an existing multirepo
`shogun multirepo init` in a folder that already has a declaration SHALL validate it, add it to the known multirepos and register a project for each declared repository that has none, as `add-repo` does, proposing `<worktreesRoot>/<alias>` with the same options; it SHALL leave the declaration unchanged. `--worktrees-root` and `--name` SHALL be rejected there. A repository another known multirepo declares SHALL make it fail without changes.

#### Scenario: Folder moved
- **WHEN** `~/projects/product` moved to `~/work/product` with its repositories side by side, and the user runs `shogun multirepo init` in `~/work/product`
- **THEN** the multirepo `~/work/product` is known, the entry for `~/projects/product` is dropped because that folder is gone, each declared repository without a project at its new path gets one, and the command says it registered an existing multirepo

#### Scenario: Already known
- **WHEN** the folder's declaration is already known and every repository has a project
- **THEN** nothing changes and the command says the multirepo is already registered, exiting with code 0

#### Scenario: Invalid declaration
- **WHEN** the existing declaration is invalid, for example a repository path that doesn't exist
- **THEN** the command names the problem and the file, registers nothing and exits with code 1

#### Scenario: Options for a new declaration
- **WHEN** the user runs `shogun multirepo init --name Product` in a folder that already has a declaration
- **THEN** the command says to use `shogun multirepo name` or `worktrees-root` instead, changes nothing and exits with code 2

#### Scenario: Repository claimed by another multirepo
- **WHEN** a declared repository is also declared by another known multirepo whose folder still exists
- **THEN** the command names the other multirepo, registers nothing and exits with code 1
