# Spec Delta

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
- **THEN** it's valid, creating a group from the multirepo folder requires `--repos`, and the multirepo agent is `claude`

#### Scenario: Agent in the declaration
- **WHEN** the declaration has `"agent": {"agentCommand": "touch /tmp/marker; claude"}`
- **THEN** that command is never run, and the multirepo agent is the one set on this machine or `claude`

### Requirement: Reject an invalid multirepo declaration
ShogunAi SHALL reject a `.shogunai-multirepo.json` whose JSON is invalid, whose alias is invalid, whose repository path doesn't exist or isn't a Git repository, whose `worktreesRoot` is missing, whose `defaultRepos` names an unknown alias, whose repository path or `worktreesRoot` contains control or Unicode formatting characters, or whose folder is inside a Git repository without being its top level, naming the problem and the file and exiting with code 1.

#### Scenario: Unknown default alias
- **WHEN** `defaultRepos` contains `web`, which isn't a key of `repos`
- **THEN** every command that reads the declaration prints that `web` isn't a declared repository of the file and exits with code 1

#### Scenario: Repository missing
- **WHEN** `repos.api` points to a folder that doesn't exist or isn't a Git repository
- **THEN** the command names the alias, the path and the file, and exits with code 1

#### Scenario: Control characters in a path
- **WHEN** `repos.api` is `"product-api\u001b[2K\r"`
- **THEN** the command says the path of `api` contains control characters, without printing them raw, and exits with code 1

### Requirement: Know the multirepos of the machine
ShogunAi SHALL keep the list of known multirepos in `~/.shogunai/multirepos.json`, a file of its own that holds the paths of their declarations and, per multirepo, its approval and its local settings, and SHALL find the approved multirepo of a directory that is the multirepo folder or inside it, or inside one of its repositories or their worktrees, wherever they are. A repository SHALL belong to at most one multirepo.

#### Scenario: From a repository outside the multirepo folder
- **WHEN** a command runs inside a worktree of `~/code/backend`, which is declared by the approved multirepo `~/work/platform`
- **THEN** it finds the multirepo `~/work/platform`

#### Scenario: Declaration moved or deleted
- **WHEN** a path in `~/.shogunai/multirepos.json` no longer has a declaration
- **THEN** that entry is ignored and commands in the repositories it declared behave as outside a multirepo

#### Scenario: Declaration not known
- **WHEN** a command runs under a `.shogunai-multirepo.json` that isn't in `~/.shogunai/multirepos.json`
- **THEN** it behaves as outside a multirepo

#### Scenario: Repository already in another multirepo
- **WHEN** the user adds to a multirepo a repository that another known multirepo declares
- **THEN** the command names the other multirepo, changes nothing and exits with code 1

### Requirement: Configure a multirepo from the command line
From the multirepo folder or anywhere it applies, `shogun multirepo add-repo <alias> <path>` and `remove-repo <alias>` SHALL add and remove a repository, `defaults <alias>[,<alias>…]` (or `--clear`) SHALL set `defaultRepos` and `worktrees-root <dir>` the proposed worktrees folder, each validating its input and rewriting only the declaration. `agent <command> [--name <name>] [--no-headroom]` SHALL set the multirepo agent in the user's ShogunAi folder, never in the declaration.

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

#### Scenario: Agent stays on this machine
- **WHEN** the user sets the multirepo agent of a multirepo whose declaration is versioned in a workspace repository
- **THEN** `.shogunai-multirepo.json` is unchanged, and a clone of that repository on another machine opens `claude`

#### Scenario: Unknown default
- **WHEN** the user runs `shogun multirepo defaults app,web`
- **THEN** the command says `web` isn't a repository of the multirepo and exits with code 2

### Requirement: Show a multirepo from the command line
`shogun multirepo show [--json]` SHALL print the multirepo's folder, whether it's approved, its proposed worktrees folder, each repository with its alias, path, project, worktrees folder and base branch, the default repositories and the multirepo agent, and say when the declaration holds an `agent` that is ignored. It SHALL also show an unapproved multirepo found in the current folder or a parent.

#### Scenario: Show
- **WHEN** the user runs `shogun multirepo show --json` in the multirepo folder
- **THEN** the output is one JSON object with `path`, `approved`, `worktreesRoot`, `repos` (each with `alias`, `path`, `project`, `worktreesFolder` and `base`), `defaultRepos` and `agent`

#### Scenario: Ignored declared agent
- **WHEN** the declaration still holds an `agent`
- **THEN** the output says it's ignored and that `shogun multirepo agent` sets the agent on this machine, and with `--json` includes `ignoredDeclaredAgent: true`

#### Scenario: Unapproved multirepo
- **WHEN** the user runs `shogun multirepo show` under a declaration that isn't approved
- **THEN** it's shown with `approved` false and a line naming `shogun multirepo trust`

#### Scenario: Not a multirepo
- **WHEN** the user runs any `shogun multirepo` subcommand other than `init` and `trust` where no multirepo applies and no declaration is found in the current folder or a parent
- **THEN** the command says no multirepo was found and exits with code 1

### Requirement: Open the multirepo agent in Herdr
`shogun open --herdr` without a target in the folder of an approved multirepo SHALL open a focused Herdr workspace of its own in the multirepo folder, outside every repository's space, and run the multirepo agent set on this machine there; when Herdr already has that workspace open it SHALL focus it without typing anything.

#### Scenario: Open
- **WHEN** the user runs `shogun open --herdr` in `~/projects/product` and the multirepo agent is `claude`
- **THEN** a focused Herdr workspace opens in `~/projects/product` labelled `product` and runs `headroom wrap claude`

#### Scenario: Already open
- **WHEN** Herdr already has a workspace whose pane is in `~/projects/product`
- **THEN** it's focused and nothing is typed into it

#### Scenario: Herdr not installed or failing
- **WHEN** Herdr isn't installed or its command fails
- **THEN** the command reports it as it does for a worktree and exits with code 1

#### Scenario: Unapproved declaration
- **WHEN** the user runs `shogun open --herdr` in a folder whose declaration isn't registered or approved
- **THEN** no multirepo agent is opened, nothing from the declaration is typed into Herdr, and the command behaves as outside a multirepo
