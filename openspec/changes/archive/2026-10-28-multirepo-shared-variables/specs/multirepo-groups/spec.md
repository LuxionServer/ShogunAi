# Spec Delta

## MODIFIED Requirements

### Requirement: Declare a multirepo
A multirepo SHALL be declared by a `.shogunai-multirepo.json` file in a folder outside every Git repository or at its top level: `repos` mapping each alias to a repository's path (relative or absolute), `worktreesRoot`, an optional `name`, an optional `defaultRepos` and an optional `env` with the variables it shares with its repositories' worktrees (see `multirepo-environment`). The repositories SHALL NOT need to share a parent folder. Aliases SHALL use only lowercase letters, digits and hyphens, and a `name` SHALL have at most 80 characters and no control characters. An `agent` is ignored.

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

### Requirement: Show a multirepo from the command line
`shogun multirepo show [--json]` SHALL print the multirepo's name and folder, whether it's approved, its proposed worktrees folder, each repository with its alias, path, project, worktrees folder and base branch, the default repositories, the shared variables of each repository with their templates and the multirepo agent, and say when the declaration holds an `agent` that is ignored. It SHALL also show an unapproved multirepo found in the current folder or a parent.

#### Scenario: Show
- **WHEN** the user runs `shogun multirepo show --json` in the multirepo folder
- **THEN** the output is one JSON object with `name`, `path`, `approved`, `worktreesRoot`, `repos` (each with `alias`, `path`, `project`, `worktreesFolder` and `base`), `defaultRepos`, `env` (each alias with its variables and templates, empty when there are none) and `agent`

#### Scenario: Ignored declared agent
- **WHEN** the declaration still holds an `agent`
- **THEN** the output says it's ignored and that `shogun multirepo agent` sets the agent on this machine, and with `--json` includes `ignoredDeclaredAgent: true`

#### Scenario: Unapproved multirepo
- **WHEN** the user runs `shogun multirepo show` under a declaration that isn't approved
- **THEN** it's shown with `approved` false and a line naming `shogun multirepo trust`

#### Scenario: Not a multirepo
- **WHEN** the user runs any `shogun multirepo` subcommand other than `init` and `trust` where no multirepo applies and no declaration is found in the current folder or a parent
- **THEN** the command says no multirepo was found and exits with code 1

#### Scenario: Shared variables
- **WHEN** the declaration's `env` gives `app` the variable `API_URL`
- **THEN** the text output lists `API_URL` with its template under `app`
