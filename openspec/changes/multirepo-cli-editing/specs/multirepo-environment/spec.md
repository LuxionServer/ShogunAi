# Spec Delta

## ADDED Requirements

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
Renaming a repository, or returning a workspace repository with another alias to detection, SHALL move its `env` entry to the new alias and rewrite every `${port:<old>.<key>}` reference of the declaration to `${port:<new>.<key>}`, keeping each template otherwise as written.

#### Scenario: Rename a referenced repository
- **WHEN** `env.app.API_URL` is `http://localhost:${port:api.http}` and the user renames `api` to `backend`
- **THEN** `env.app.API_URL` is `http://localhost:${port:backend.http}` and the multirepo is still approved

#### Scenario: Rename a repository with variables
- **WHEN** `env.app` declares `API_URL` and the user renames `app` to `web`
- **THEN** `env.web.API_URL` holds the same template and `env` has no `app` entry

### Requirement: Removing a repository keeps shared variables valid
Removing a repository SHALL drop its own `env` entry, and SHALL be refused, naming the variables, while another repository's shared variable references it.

#### Scenario: Repository referenced by another
- **WHEN** `env.app.API_URL` uses `${port:api.http}` and the user runs `shogun multirepo remove-repo api`
- **THEN** the command names `app`'s `API_URL` as referencing `api`, changes nothing and exits with code 1

#### Scenario: Repository with its own variables
- **WHEN** `env.docs` declares `SITE_URL`, nothing references `docs` and the user removes `docs`
- **THEN** `docs` leaves `repos` and `env` has no `docs` entry
