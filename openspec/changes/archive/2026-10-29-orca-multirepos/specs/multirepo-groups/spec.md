## ADDED Requirements

### Requirement: Open a group's worktrees in Orca
`shogun open <id-or-branch> --orca` in the multirepo folder SHALL open every worktree of the group, found as `--herdr` finds it, as `shogun open --orca` opens one worktree: under its own repository, with its environment, its project's default agent and its group session name. Each worktree SHALL be reported as `started-agent`, `focused` or failed, with its terminal. A worktree that fails SHALL NOT stop the others.

#### Scenario: Open a group
- **WHEN** the user runs `shogun open TASK-123 --orca` in `~/projects/product` and `feature/TASK-123` is a group with `app` and `api`
- **THEN** `app`'s worktree opens in Orca under `product-app` with `app`'s environment and agent, `api`'s under `product-api` with its own, each in a focused terminal, and the command reports each with its terminal

#### Scenario: Partly open
- **WHEN** Orca already has a terminal with a live agent in `app`'s worktree
- **THEN** that terminal is focused without typing anything, `app` is reported as `focused`, and `api`'s agent is started

#### Scenario: Repositories grouped in Orca
- **WHEN** the multirepo was imported in Orca as a group, so Orca already knows `product-app` and `product-api`
- **THEN** each worktree opens under its repository inside that group and no repository is registered again

#### Scenario: Ambiguous or unknown id
- **WHEN** the id matches both `feature/TASK-5` and `fix/TASK-5`, or no group
- **THEN** the command behaves as `shogun open <id> --herdr` does: it lists both branches and exits with code 2, or says no group matches and exits with code 1, opening nothing

#### Scenario: One worktree fails
- **WHEN** opening `api` in Orca fails, or its agent doesn't start in time
- **THEN** `app` is still opened, the failure names `api` and the reason, and the command exits with code 1

#### Scenario: Orca not installed
- **WHEN** Orca isn't installed
- **THEN** the command says so once, names onorca.dev, opens nothing and exits with code 1

#### Scenario: Agent option
- **WHEN** the user passes `--agent` while opening a group in Orca
- **THEN** the command says each worktree uses its project's agent and exits with code 2

#### Scenario: Result as JSON
- **WHEN** the user runs `shogun open TASK-123 --orca --json` and `app` is focused while `api` gets its agent started
- **THEN** the object has the group's `branch` and a `repos` list where `app`'s entry has `result` `focused` and `api`'s has `result` `started-agent`, each with its `alias`, `path`, `terminal`, `agent`, `session_name` and `error`

### Requirement: Open the multirepo agent in Orca
`shogun open --orca` without a target in an approved multirepo's folder SHALL run the multirepo agent set on this machine in the Orca workspace whose path is the multirepo folder, with the multirepo's session name. It SHALL focus a live agent in that workspace without typing anything, or otherwise start the agent in a new focused terminal there and verify it started as for a worktree, reporting `started-agent` or `focused` with the terminal.

#### Scenario: Folder workspace without an agent
- **WHEN** the user runs `shogun open --orca` in `~/projects/product`, Orca has a folder workspace in `~/projects/product` with no live agent, and the multirepo agent is `claude`
- **THEN** a focused terminal opens in that workspace running `claude` with the session name `product`, and the result is `started-agent`

#### Scenario: Live agent
- **WHEN** a terminal of that workspace runs an agent Orca reports, including one that is `done`
- **THEN** that terminal is focused, nothing is typed or created, and the result is `focused`

#### Scenario: Several workspaces in the folder
- **WHEN** Orca has more than one workspace whose path is the multirepo folder
- **THEN** the one with a live agent is used, or else the first one Orca lists

#### Scenario: Agent doesn't start
- **WHEN** Orca reports no agent in the new terminal 10 seconds after it started
- **THEN** the command fails naming the terminal and the command, the terminal stays open, and the command exits with code 1

#### Scenario: Unapproved declaration
- **WHEN** the user runs `shogun open --orca` in a folder whose declaration isn't registered or approved
- **THEN** no multirepo agent is opened, nothing from the declaration reaches Orca, and the command behaves as outside a multirepo

#### Scenario: Result as JSON
- **WHEN** the user runs `shogun open --orca --json` in the multirepo folder and the agent is started
- **THEN** the object has `path`, `orca` `started-agent`, `terminal`, `agent` and `session_name`

### Requirement: Host the multirepo agent in Orca
ShogunAi SHALL NOT create an Orca workspace for a multirepo folder that isn't a Git repository, since only Orca's app can. When the folder is a Git repository Orca doesn't know, ShogunAi SHALL register it and use its main worktree. When the folder isn't a Git repository and Orca has no workspace for it, the open SHALL fail with an error naming the folder and how to add it in Orca, and SHALL change nothing in Orca.

#### Scenario: Folder isn't a Git repository and isn't in Orca
- **WHEN** the user runs `shogun open --orca` in `~/projects/product`, which isn't a Git repository, and Orca has no workspace there
- **THEN** the command says Orca has no workspace for `~/projects/product`, explains how to add the folder in Orca's app as a group with a folder workspace or with "Open as Folder", registers nothing, and exits with code 1

#### Scenario: Folder added with "Open as Folder"
- **WHEN** the folder isn't a Git repository and was added in Orca with "Open as Folder"
- **THEN** the multirepo agent opens in that folder's workspace

#### Scenario: Folder is a Git repository unknown to Orca
- **WHEN** `~/projects/product` is a Git repository that Orca hasn't registered
- **THEN** it's registered with Orca and the multirepo agent opens in its main worktree

#### Scenario: Orca not running
- **WHEN** Orca is installed but closed
- **THEN** Orca is started as for a worktree before looking for the folder's workspace
