## MODIFIED Requirements

### Requirement: Declare a multirepo
A multirepo SHALL be declared by a `.shogunai-multirepo.json` file in a folder outside every Git repository or at its top level: `repos` mapping each alias to a repository's path (relative or absolute), `worktreesRoot`, an optional `name`, an optional `defaultRepos` and an optional `env` with the variables it shares with its repositories' worktrees (see `multirepo-environment`). The repositories SHALL NOT need to share a parent folder. An `agent` is ignored.

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
- **WHEN** the declaration has no `defaultRepos` and the user never set a multirepo agent
- **THEN** it's valid, creating a group from the multirepo folder requires `--repos`, and the multirepo agent is `claude` without Headroom

#### Scenario: Agent in the declaration
- **WHEN** the declaration has `"agent": {"agentCommand": "touch /tmp/marker; claude"}`
- **THEN** that command is never run, and the multirepo agent is the one set on this machine or `claude`

#### Scenario: Shared variables
- **WHEN** the declaration has `"env": { "app": { "API_URL": "http://localhost:${port:api.http}" } }`
- **THEN** it's a valid multirepo whose `app` worktrees receive `API_URL`

### Requirement: Open a group's worktrees in Herdr
`shogun open <id-or-branch> --herdr` in the multirepo folder SHALL open every worktree of the group whose branch is `<branch>`, or `feature/<id>` or `fix/<id>` when only one of them is a group, as `shogun open --herdr` opens a single worktree: under its repository's space, with its own environment and its project's default agent, focusing the ones with a live agent and starting the agent in the open ones without one, in their idle pane or in a new pane as for a single worktree.

#### Scenario: Open a group
- **WHEN** the user runs `shogun open TASK-123 --herdr` in `~/projects/product` and `feature/TASK-123` is a group with `app` and `api`
- **THEN** `app` opens under `product-app`'s space with `app`'s environment and agent, `api` under `product-api`'s space with its own, and the command reports each

#### Scenario: Partly open
- **WHEN** `app` is already open in Herdr with a live agent
- **THEN** its agent is focused without typing anything and `api` is opened

#### Scenario: Open without an agent
- **WHEN** `app`'s workspace is open in Herdr with a single pane in `app`'s worktree, no live agent and its shell at its prompt
- **THEN** `app`'s agent is started in that pane, no pane is created, and `app` is reported as `started-agent` with that pane

#### Scenario: Open without an agent and a busy workspace
- **WHEN** `app`'s workspace is open in Herdr without a live agent but has several panes, or its only pane runs a program
- **THEN** `app`'s agent is started in a new pane of that workspace with `app`'s environment, the existing panes are kept, and `app` is reported as `started-agent`

#### Scenario: Ambiguous id
- **WHEN** both `feature/TASK-5` and `fix/TASK-5` are groups and the user runs `shogun open TASK-5 --herdr`
- **THEN** the command lists both branches, opens nothing and exits with code 2

#### Scenario: Unknown group
- **WHEN** no group matches `TASK-9`
- **THEN** the command says so and exits with code 1

#### Scenario: One worktree fails
- **WHEN** opening `api` fails in Herdr, or its agent doesn't start in time
- **THEN** `app` is still opened, the failure names `api` and the reason, and the command exits with code 1

#### Scenario: Agent option
- **WHEN** the user passes `--agent` while opening a group
- **THEN** the command says each worktree uses its project's agent and exits with code 2

#### Scenario: Result as JSON
- **WHEN** the user runs `shogun open TASK-123 --herdr --json` and `app` is focused while `api` gets its agent started
- **THEN** `app`'s entry has `result` `focused` and `api`'s has `result` `started-agent`, each with its `pane_id` and `agent`

### Requirement: Open the multirepo agent in Herdr
`shogun open --herdr` without a target in the folder of an approved multirepo SHALL open a focused Herdr workspace of its own in the multirepo folder, outside every repository's space, and run the multirepo agent set on this machine there. It SHALL verify the agent started as for a worktree, and SHALL report `opened`, `started-agent` or `focused` with the agent's pane.

#### Scenario: Open
- **WHEN** the user runs `shogun open --herdr` in `~/projects/product` and the multirepo agent is `claude`
- **THEN** a focused Herdr workspace opens in `~/projects/product` labelled `product` and runs `claude`

#### Scenario: Already open
- **WHEN** Herdr already has a workspace whose pane is in `~/projects/product` and one of its panes has a live agent
- **THEN** that agent's pane is focused and nothing is typed into it

#### Scenario: Open without an agent
- **WHEN** Herdr already has that workspace open with a single pane in `~/projects/product`, no live agent and its shell at its prompt
- **THEN** `claude` runs in that pane, no pane is created, and the result is `started-agent`

#### Scenario: Open without an agent and a busy workspace
- **WHEN** Herdr already has that workspace open without a live agent, but with several panes or a single pane running a program
- **THEN** `claude` runs in a new pane of that workspace in `~/projects/product`, the existing panes are kept, and the result is `started-agent`

#### Scenario: Agent doesn't start
- **WHEN** Herdr doesn't detect the multirepo agent in time
- **THEN** the command reports it as it does for a worktree and exits with code 1

#### Scenario: Herdr not installed or failing
- **WHEN** Herdr isn't installed or its command fails
- **THEN** the command reports it as it does for a worktree and exits with code 1

#### Scenario: Unapproved declaration
- **WHEN** the user runs `shogun open --herdr` in a folder whose declaration isn't registered or approved
- **THEN** no multirepo agent is opened, nothing from the declaration is typed into Herdr, and the command behaves as outside a multirepo

## ADDED Requirements

### Requirement: Multirepo aliases and names
In a multirepo declaration, aliases SHALL use only lowercase letters, digits and hyphens, and a `name` SHALL have at most 80 characters and no control characters.

#### Scenario: Alias with capital letters
- **WHEN** a declaration has the alias `App`
- **THEN** the declaration is invalid and the problem names the alias

#### Scenario: Name too long
- **WHEN** a declaration's `name` has 81 characters, or a control character
- **THEN** the declaration is invalid and the problem names the `name`

### Requirement: Report each worktree of an opened group
Opening a group's worktrees in Herdr SHALL report each worktree as `opened`, `started-agent`, `focused` or failed, with its agent's pane. A worktree that fails SHALL NOT stop the others.

#### Scenario: Failure doesn't stop the others
- **WHEN** opening `api` fails in Herdr, or its agent doesn't start in time
- **THEN** `app` is still opened, the failure names `api` and the reason, and the command exits with code 1

#### Scenario: Results as JSON
- **WHEN** the user runs `shogun open TASK-123 --herdr --json` and `app` is focused while `api` gets its agent started
- **THEN** `app`'s entry has `result` `focused` and `api`'s has `result` `started-agent`, each with its `pane_id` and `agent`

### Requirement: Reuse the multirepo agent's open workspace
When Herdr already has the multirepo's workspace open, `shogun open --herdr` without a target SHALL focus its live agent without typing anything, or, when no pane of it has a live agent, SHALL start the multirepo agent in the workspace's only pane when that pane is in the multirepo folder with its shell at its prompt and nothing else in the foreground, or in a new pane of that workspace otherwise, without closing any pane.

#### Scenario: Live agent focused
- **WHEN** Herdr already has a workspace whose pane is in `~/projects/product` and one of its panes has a live agent
- **THEN** that agent's pane is focused and nothing is typed into it

#### Scenario: Idle pane reused
- **WHEN** Herdr already has that workspace open with a single pane in `~/projects/product`, no live agent and its shell at its prompt
- **THEN** `claude` runs in that pane, no pane is created, and the result is `started-agent`

#### Scenario: New pane in a busy workspace
- **WHEN** Herdr already has that workspace open without a live agent, but with several panes or a single pane running a program
- **THEN** `claude` runs in a new pane of that workspace in `~/projects/product`, the existing panes are kept, and the result is `started-agent`
