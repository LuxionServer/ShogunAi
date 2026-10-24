## MODIFIED Requirements

### Requirement: Declare a multirepo
A multirepo SHALL be declared by a `.shogunai-multirepo.json` file in a folder outside every Git repository or at the top level of one, never inside one: `repos` mapping each alias to a repository's path (relative or absolute), `worktreesRoot`, an optional `defaultRepos`, an optional `agent` and the `version` of the declaration format. The repositories SHALL NOT need to share a parent folder. Aliases SHALL use only lowercase letters, digits and hyphens.

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
- **WHEN** the declaration has `version` `2`, no `defaultRepos` and no `agent`
- **THEN** it's valid, creating a group from the multirepo folder requires `--repos`, and the multirepo agent is `claude` without Headroom

#### Scenario: Agent without the Headroom option
- **WHEN** the declaration has `version` `2` and `agent` `{ "agentCommand": "codex" }`
- **THEN** the multirepo agent runs `codex` without Headroom

### Requirement: Configure a multirepo from the command line
From the multirepo folder or anywhere it applies, `shogun multirepo add-repo <alias> <path>` and `remove-repo <alias>` SHALL add and remove a repository, `defaults <alias>[,<alias>…]` (or `--clear`) SHALL set `defaultRepos`, `agent <command> [--name <name>] [--headroom]` SHALL set the multirepo agent and `worktrees-root <dir>` the proposed worktrees folder, each validating its input and rewriting only the declaration.

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
- **THEN** the multirepo agent is launched as `codex --full-auto` and shown as `Codex`; with `--headroom` as `headroom wrap codex --full-auto`

#### Scenario: Legacy option
- **WHEN** the user runs `shogun multirepo agent codex --no-headroom`
- **THEN** the multirepo agent is launched as `codex`, the same as without the option

#### Scenario: Contradictory options
- **WHEN** the user runs `shogun multirepo agent codex --headroom --no-headroom`
- **THEN** the command says the options can't be combined, changes nothing and exits with code 2

#### Scenario: Unknown default
- **WHEN** the user runs `shogun multirepo defaults app,web`
- **THEN** the command says `web` isn't a repository of the multirepo and exits with code 2

### Requirement: Open the multirepo agent in Herdr
`shogun open --herdr` without a target in the multirepo folder SHALL open a focused Herdr workspace of its own in the multirepo folder, outside every repository's space, and run the multirepo agent there; when Herdr already has that workspace open it SHALL focus it without typing anything.

#### Scenario: Open
- **WHEN** the user runs `shogun open --herdr` in `~/projects/product` and the multirepo agent is `claude`
- **THEN** a focused Herdr workspace opens in `~/projects/product` labelled `product` and runs `claude`

#### Scenario: Already open
- **WHEN** Herdr already has a workspace whose pane is in `~/projects/product`
- **THEN** it's focused and nothing is typed into it

#### Scenario: Herdr not installed or failing
- **WHEN** Herdr isn't installed or its command fails
- **THEN** the command reports it as it does for a worktree and exits with code 1

## ADDED Requirements

### Requirement: Declarations written by earlier versions keep Headroom
A declaration without `version` was written by an earlier version, which left `useHeadroom` out when it was on: its agent SHALL be read as wrapped with Headroom unless it says `useHeadroom: false`, and a missing agent as `claude` wrapped with Headroom. Every declaration ShogunAi writes SHALL carry `version` `2` and the agent's `useHeadroom`.

#### Scenario: Legacy agent
- **WHEN** a declaration without `version` has `agent` `{ "agentCommand": "codex" }`
- **THEN** the multirepo agent runs `headroom wrap codex`

#### Scenario: Legacy declaration without an agent
- **WHEN** a declaration without `version` has no `agent`
- **THEN** the multirepo agent runs `headroom wrap claude`

#### Scenario: Legacy agent with Headroom off
- **WHEN** a declaration without `version` has `agent` `{ "agentCommand": "codex", "useHeadroom": false }`
- **THEN** the multirepo agent runs `codex`

#### Scenario: Rewriting a legacy declaration
- **WHEN** the user runs `shogun multirepo defaults app` on a declaration without `version` and without an agent
- **THEN** the rewritten declaration has `version` `2` and an agent with `agentCommand` `claude` and `useHeadroom` `true`, so the multirepo agent still runs `headroom wrap claude`
