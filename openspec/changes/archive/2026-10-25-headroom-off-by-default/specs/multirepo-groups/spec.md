## MODIFIED Requirements

### Requirement: Declare a multirepo
A multirepo SHALL be declared by a `.shogunai-multirepo.json` file in a folder outside every Git repository or at the top level of one, never inside one: `repos` mapping each alias to a repository's path (relative or absolute), `worktreesRoot` and an optional `defaultRepos`. The repositories SHALL NOT need to share a parent folder. Aliases SHALL use only lowercase letters, digits and hyphens. An `agent` in the declaration SHALL be ignored.

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

### Requirement: Configure a multirepo from the command line
From the multirepo folder or anywhere it applies, `shogun multirepo add-repo <alias> <path>` and `remove-repo <alias>` SHALL add and remove a repository, `defaults <alias>[,<alias>…]` (or `--clear`) SHALL set `defaultRepos` and `worktrees-root <dir>` the proposed worktrees folder, each validating its input and rewriting only the declaration. `agent <command> [--name <name>] [--headroom]` SHALL set the multirepo agent in the user's ShogunAi folder, never in the declaration.

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

#### Scenario: Agent stays on this machine
- **WHEN** the user sets the multirepo agent of a multirepo whose declaration is versioned in a workspace repository
- **THEN** `.shogunai-multirepo.json` is unchanged, and a clone of that repository on another machine opens `claude`

#### Scenario: Unknown default
- **WHEN** the user runs `shogun multirepo defaults app,web`
- **THEN** the command says `web` isn't a repository of the multirepo and exits with code 2

### Requirement: Open the multirepo agent in Herdr
`shogun open --herdr` without a target in the folder of an approved multirepo SHALL open a focused Herdr workspace of its own in the multirepo folder, outside every repository's space, and run the multirepo agent set on this machine there; when Herdr already has that workspace open it SHALL focus it without typing anything.

#### Scenario: Open
- **WHEN** the user runs `shogun open --herdr` in `~/projects/product` and the multirepo agent is `claude`
- **THEN** a focused Herdr workspace opens in `~/projects/product` labelled `product` and runs `claude`

#### Scenario: Already open
- **WHEN** Herdr already has a workspace whose pane is in `~/projects/product`
- **THEN** it's focused and nothing is typed into it

#### Scenario: Herdr not installed or failing
- **WHEN** Herdr isn't installed or its command fails
- **THEN** the command reports it as it does for a worktree and exits with code 1

#### Scenario: Unapproved declaration
- **WHEN** the user runs `shogun open --herdr` in a folder whose declaration isn't registered or approved
- **THEN** no multirepo agent is opened, nothing from the declaration is typed into Herdr, and the command behaves as outside a multirepo
