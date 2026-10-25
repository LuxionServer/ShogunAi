## ADDED Requirements

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

## MODIFIED Requirements

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

### Requirement: Show a multirepo from the command line
`shogun multirepo show [--json]` SHALL print the multirepo's name and folder, its proposed worktrees folder, each repository with its alias, path, project, worktrees folder and base branch, the default repositories and the multirepo agent.

#### Scenario: Show
- **WHEN** the user runs `shogun multirepo show --json` in the multirepo folder
- **THEN** the output is one JSON object with `name`, `path`, `worktreesRoot`, `repos` (each with `alias`, `path`, `project`, `worktreesFolder` and `base`), `defaultRepos` and `agent`

#### Scenario: Not a multirepo
- **WHEN** the user runs any `shogun multirepo` subcommand other than `init` where no multirepo applies
- **THEN** the command says no multirepo was found and exits with code 1
