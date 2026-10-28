# Spec Delta

## MODIFIED Requirements

### Requirement: ShogunAi's own writes keep a multirepo approved
Creating a multirepo and changing its declaration through ShogunAi (`shogun multirepo …` subcommands, including `env set`, `env unset` and `workspace detect`, the desktop app's "Add multirepo" and multirepo settings) SHALL leave the written content approved. A subcommand or setting that writes SHALL refuse an unapproved multirepo, naming `shogun multirepo trust`, and change nothing.

#### Scenario: Add a repository
- **WHEN** the user runs `shogun multirepo add-repo docs product-docs` in an approved multirepo
- **THEN** the declaration includes `docs` and the multirepo is still approved

#### Scenario: Write to an unapproved multirepo
- **WHEN** the declaration changed after a pull and the user runs `shogun multirepo add-repo docs product-docs`
- **THEN** the command says the multirepo needs review with `shogun multirepo trust`, changes nothing and exits with code 1

#### Scenario: Edit a shared variable
- **WHEN** the user runs `shogun multirepo env set app API_URL 'http://localhost:${port:api.http}'` in an approved multirepo
- **THEN** the multirepo is still approved and `app`'s worktrees receive `API_URL` at their next resolution

#### Scenario: Shared variables edited in an unapproved multirepo
- **WHEN** a pull changed `env.app.API_URL` and the user runs `shogun multirepo env set app API_URL x` without reviewing it
- **THEN** the command says the multirepo needs review with `shogun multirepo trust`, changes nothing and exits with code 1
