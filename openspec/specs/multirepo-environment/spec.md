# multirepo-environment Specification

## Purpose
Lets a multirepo give its repositories' worktrees variables that connect them to each other, such as the port of the backend a group's app talks to, declared once in `.shogunai-multirepo.json` and delivered with each worktree's own environment.

## Requirements

### Requirement: Declare shared variables per repository
The optional `env` field of `.shogunai-multirepo.json` SHALL map aliases of the multirepo's repositories to objects of variable names and string templates, which the multirepo adds to the worktrees of that repository. Names and values SHALL follow the rules of a repository's own `env` (see `worktree-environment`). An unknown alias, a non-object entry, or a name or value those rules reject SHALL make the declaration invalid, naming the alias and the variable.

#### Scenario: Variables for one repository
- **WHEN** the declaration has `"env": { "app": { "API_URL": "http://localhost:${port:api.http}" } }` and declares `app` and `api`
- **THEN** the worktrees of `app` receive `API_URL` and the worktrees of `api` receive no shared variable

#### Scenario: No shared variables
- **WHEN** the declaration has no `env`
- **THEN** every worktree's environment is the same as outside a multirepo

#### Scenario: Unknown alias
- **WHEN** `env` has an entry for `web`, which isn't a repository of the multirepo
- **THEN** every command that reads the declaration says `web` isn't a declared repository of the file and exits with code 1

#### Scenario: Reserved name
- **WHEN** `env.app` declares `NODE_OPTIONS`
- **THEN** the declaration is reported as invalid, naming `app`, `NODE_OPTIONS` and the file

### Requirement: Shared variable templates
A shared template SHALL accept `${worktree}` (the folder name of the worktree receiving it), `$$` and `${port:<alias>.<key>}`, the port that repository `<alias>` reserves for its shared key `<key>`, declared there as `${port:<key>}`. `${port}`, `${port:<key>}` without an alias, a reference to an alias that isn't a repository of the multirepo, and any other placeholder SHALL make the declaration invalid, naming the variable and the placeholder.

#### Scenario: Cross-repository port
- **WHEN** `api`'s `.shogunai.json` declares `SERVER_PORT` as `${port:http}`, its worktree `TASK-7` reserved 20003 for it, and `env.app.API_URL` is `http://localhost:${port:api.http}`
- **THEN** `API_URL` in `app`'s worktree on the same branch is `http://localhost:20003`

#### Scenario: Worktree name
- **WHEN** `env.app.APP_SUFFIX` is `-${worktree}` and `app`'s worktree folder is `TASK-7`
- **THEN** `APP_SUFFIX` resolves to `-TASK-7`

#### Scenario: Port of its own
- **WHEN** `env.app` declares `DEBUG_PORT` as `${port}` or as `${port:debug}`
- **THEN** the declaration is reported as invalid, saying ports a repository uses itself are declared in its own `.shogunai.json`

#### Scenario: Unknown repository in a reference
- **WHEN** `env.app.API_URL` uses `${port:billing.http}` and `billing` isn't a repository of the multirepo
- **THEN** the declaration is reported as invalid, naming `API_URL` and `billing`

### Requirement: Which worktree a reference resolves against
A `${port:<alias>.<key>}` reference SHALL resolve against the linked worktree of `<alias>` checked out on the same branch as the worktree receiving the variable, and against `<alias>`'s main checkout when there is none, including when the receiving worktree is a main checkout or has no branch.

#### Scenario: Partner in the group
- **WHEN** `app` and `api` both have worktrees on `feature/TASK-7`
- **THEN** `app`'s worktree receives the port reserved by `api`'s worktree on `feature/TASK-7`

#### Scenario: Loose worktree
- **WHEN** `app` has a worktree on `feature/TASK-8` and `api` has none on that branch
- **THEN** `app`'s worktree receives the port reserved by `api`'s main checkout

#### Scenario: Main checkout
- **WHEN** the environment of `app`'s main checkout is resolved
- **THEN** it receives the port reserved by `api`'s main checkout

#### Scenario: Partner joins later
- **WHEN** `app`'s worktree on `feature/TASK-8` received the port of `api`'s main checkout, and then `api` gets a worktree on `feature/TASK-8`
- **THEN** the next time `app`'s environment is resolved it receives the port of `api`'s new worktree

### Requirement: Referenced ports are the referenced worktree's own
Resolving a worktree's environment with reservation SHALL reserve a referenced port in the referenced worktree, exactly as resolving that worktree's own environment would, so both worktrees see the same number. Resolving without reservation SHALL only read it. The referenced port SHALL stay the referenced worktree's reservation, released with it.

#### Scenario: Partner not resolved yet
- **WHEN** `api`'s worktree on `feature/TASK-7` has never had its environment resolved and `app`'s worktree on the same branch is opened
- **THEN** `api`'s worktree gets its ports reserved, and `shogun env` in it later prints the same `SERVER_PORT` that `app` received in `API_URL`

#### Scenario: Read only
- **WHEN** the environment of `app`'s worktree is read without reservation and `api`'s partner worktree has reserved nothing
- **THEN** nothing is reserved and `API_URL` is reported as not resolved

### Requirement: Shared variables are delivered with the worktree's environment
Shared variables SHALL be part of a worktree's resolved environment, delivered wherever its own variables are (`shogun env`, the `SessionStart` hook, opening in the terminal or Herdr, the environment file), only while the multirepo applies (see `multirepo-trust`). A variable the repository's own `env` declares SHALL win over a shared one of the same name, and a shared one SHALL win over a provider's.

#### Scenario: Delivered to the session
- **WHEN** Claude Code starts a session in `app`'s worktree on `feature/TASK-7` and `API_URL` resolves to `http://localhost:20003`
- **THEN** `export API_URL='http://localhost:20003'` is appended to `$CLAUDE_ENV_FILE` and the context lists `API_URL` among the worktree's variables

#### Scenario: Repository's own variable wins
- **WHEN** `app`'s `.shogunai.json` declares `API_URL` and `env.app` also declares `API_URL`
- **THEN** the worktree receives the value from `app`'s `.shogunai.json`

#### Scenario: Multirepo not approved
- **WHEN** the multirepo's declaration changed outside ShogunAi and isn't approved
- **THEN** `app`'s worktrees receive no shared variable

#### Scenario: Repository not approved
- **WHEN** the multirepo is approved and `app`'s own `.shogunai.json` isn't
- **THEN** `app`'s worktrees receive the shared variables and none of `app`'s declared ones

### Requirement: Report shared variables that can't be resolved
A shared variable whose reference can't be resolved, because the referenced repository isn't approved, doesn't declare that key, its port wasn't reserved while reading without reservation, or its worktree can't be read, SHALL be left out of the environment without failing it. `shogun env` SHALL name it and the reason on standard error, and the `SessionStart` hook SHALL name it in its context.

#### Scenario: Key not declared
- **WHEN** `env.app.API_URL` uses `${port:api.http}` and `api`'s `.shogunai.json` has no `${port:http}`
- **THEN** `shogun env` in `app`'s worktree prints the other variables, says on standard error that `API_URL` couldn't be resolved because `api` doesn't declare the port `http`, and exits with code 0

#### Scenario: Referenced repository not approved
- **WHEN** `api`'s `.shogunai.json` isn't approved on this machine
- **THEN** `API_URL` is left out, the note names `shogun trust` in `api`, and no port is reserved in `api`'s worktree

#### Scenario: In the session context
- **WHEN** a session starts in `app`'s worktree and `API_URL` can't be resolved
- **THEN** the context says `API_URL` isn't available and why, and the session starts normally

#### Scenario: Invalid shared variables
- **WHEN** the multirepo's `env` is invalid and the user runs `shogun env` in `app`'s worktree
- **THEN** `app`'s own and providers' variables are printed, standard error names the declaration and the problem, and the command exits with code 0

### Requirement: List shared variables from the command line
`shogun multirepo env [--json]` SHALL print the multirepo's shared variables grouped by repository alias, each with its name and template as written, and say when there are none. With `--json` it SHALL print one object with the `env` object of `shogun multirepo show --json`.

#### Scenario: Variables declared
- **WHEN** the declaration's `env` gives `app` the variable `API_URL` as `http://localhost:${port:api.http}` and the user runs `shogun multirepo env`
- **THEN** the output lists `app` and under it `API_URL=http://localhost:${port:api.http}`, and the command exits with code 0

#### Scenario: No variables
- **WHEN** the declaration has no `env`
- **THEN** the command says the multirepo shares no variables and exits with code 0

### Requirement: Set a shared variable from the command line
`shogun multirepo env set <alias> <name> <template> [--json]` SHALL add the variable to `env.<alias>`, or replace its template, after checking it with the rules of the declaration's `env`, and write nothing else. An unknown alias SHALL exit with code 2 listing the aliases; an invalid name or template SHALL exit with code 1 naming the variable and the problem, changing nothing.

#### Scenario: Add a variable
- **WHEN** the user runs `shogun multirepo env set app API_URL 'http://localhost:${port:api.http}'` in an approved multirepo with `app` and `api`
- **THEN** `env.app.API_URL` holds that template, the rest of the declaration is unchanged, and the multirepo is still approved

#### Scenario: Replace a variable
- **WHEN** `env.app.API_URL` exists and the user sets it to `http://127.0.0.1:${port:api.http}`
- **THEN** `env.app.API_URL` holds the new template and no other variable changes

#### Scenario: Invalid template
- **WHEN** the user runs `shogun multirepo env set app DEBUG_PORT '${port}'`
- **THEN** the command says ports a repository uses itself are declared in its own `.shogunai.json`, changes nothing and exits with code 1

#### Scenario: Unknown repository
- **WHEN** the user runs `shogun multirepo env set web API_URL x` and `web` isn't a repository of the multirepo
- **THEN** the command says `web` isn't a repository of the multirepo, lists its aliases, changes nothing and exits with code 2

### Requirement: Unset a shared variable from the command line
`shogun multirepo env unset <alias> <name> [--json]` SHALL remove the variable, remove `env.<alias>` when it was its last one and `env` when no alias is left, and write nothing else. A variable that isn't declared SHALL exit with code 1 naming it, changing nothing.

#### Scenario: Remove the last variable
- **WHEN** `env` only declares `app.API_URL` and the user runs `shogun multirepo env unset app API_URL`
- **THEN** the declaration has no `env` and the multirepo is still approved

#### Scenario: Variable not declared
- **WHEN** the user runs `shogun multirepo env unset app MISSING`
- **THEN** the command says `app` has no shared variable `MISSING`, changes nothing and exits with code 1

### Requirement: Shared variables ShogunAi can't rewrite
No write of ShogunAi to the declaration, from the command line or the multirepo settings, SHALL rewrite an `env` that holds a non-object entry or a non-string value: it SHALL be refused naming the problem and changing nothing, so rewriting never drops part of `env`.

#### Scenario: Non-string value
- **WHEN** `env.app.RETRIES` is the number `3` and the user runs `shogun multirepo env set app API_URL x`
- **THEN** the command names `env.app.RETRIES`, changes nothing and exits with code 1

#### Scenario: Other settings
- **WHEN** `env.app.RETRIES` is the number `3` and the user changes the multirepo's name in its settings
- **THEN** the settings name `env.app.RETRIES`, and the declaration is unchanged

### Requirement: Shared variables follow renamed repositories
Renaming a repository, declaring the workspace repository under another alias, or returning it to detection from another alias, SHALL move its `env` entry to the new alias and rewrite every `${port:<old>.<key>}` reference of the declaration to `${port:<new>.<key>}`, keeping each template otherwise as written.

#### Scenario: Rename a referenced repository
- **WHEN** `env.app.API_URL` is `http://localhost:${port:api.http}` and the user renames `api` to `backend`
- **THEN** `env.app.API_URL` is `http://localhost:${port:backend.http}` and the multirepo is still approved

#### Scenario: Rename a repository with variables
- **WHEN** `env.app` declares `API_URL` and the user renames `app` to `web`
- **THEN** `env.web.API_URL` holds the same template and `env` has no `app` entry

#### Scenario: Escaped text isn't a reference
- **WHEN** `env.app.HINT` is `$${port:api.http}`, which renders as that text, and the user renames `api` to `backend`
- **THEN** `env.app.HINT` is unchanged

### Requirement: Removing a repository keeps shared variables valid
Removing a repository, or turning the workspace repository off, SHALL drop its own `env` entry, and SHALL be refused, naming the variables, while another repository's shared variable references it.

#### Scenario: Repository referenced by another
- **WHEN** `env.app.API_URL` uses `${port:api.http}` and the user runs `shogun multirepo remove-repo api`
- **THEN** the command names `app`'s `API_URL` as referencing `api`, changes nothing and exits with code 1

#### Scenario: Repository with its own variables
- **WHEN** `env.docs` declares `SITE_URL`, nothing references `docs` and the user removes `docs`
- **THEN** `docs` leaves `repos` and `env` has no `docs` entry

#### Scenario: Workspace repository referenced
- **WHEN** `env.app.DOCS_URL` uses `${port:workspace.docs}` and the user runs `shogun multirepo workspace clear`
- **THEN** the command names `app`'s `DOCS_URL` as referencing `workspace`, changes nothing and exits with code 1
