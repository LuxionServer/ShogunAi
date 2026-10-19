# Spec Delta

## MODIFIED Requirements

### Requirement: Declare the worktree environment
The system SHALL read the optional `env` field of `.shogunai.json` as an object whose keys are environment variable names and whose values are string templates. A repository without the field declares an empty environment. A non-object `env`, a non-string value, a key that isn't a valid variable name (letters, digits and `_`, not starting with a digit), or a reserved name SHALL make the file invalid. Reserved names are those that change how shells, language runtimes, the dynamic loader or Git behave.

#### Scenario: Variables declared
- **WHEN** `.shogunai.json` contains `{ "env": { "PORT": "${port}", "APP_SUFFIX": "-${worktree}" } }`
- **THEN** the repository's environment declares `PORT` and `APP_SUFFIX` with those templates

#### Scenario: No environment
- **WHEN** `.shogunai.json` has no `env` field, or the repository has no `.shogunai.json`
- **THEN** every worktree's environment is empty and nothing is reserved or written

#### Scenario: Invalid declaration
- **WHEN** `env` is `{ "PORT": 3000 }` or `{ "1PORT": "x" }`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`

#### Scenario: Reserved name
- **WHEN** `env` declares `PATH`, `BASH_ENV`, `NODE_OPTIONS`, `LD_PRELOAD`, `DYLD_INSERT_LIBRARIES` or `GIT_SSH_COMMAND`
- **THEN** the file is reported as invalid with an error that names the variable, and no session or environment file receives it

### Requirement: Write the environment file
When `.shogunai.json` declares `envFile` as a path relative to the worktree root, the system SHALL write the worktree's resolved variables to that path in dotenv format, replacing its previous contents, with every value written so that sourcing the file from a shell never expands or runs anything in it. It SHALL NOT write over a file tracked by Git, nor a path whose real location, following symbolic links, is outside the worktree, and a non-string or absolute `envFile` SHALL make the file invalid.

#### Scenario: File written
- **WHEN** `envFile` is `.env.shogunai` and the worktree's `PORT` resolves to 20001
- **THEN** `.env.shogunai` in the worktree contains `PORT=20001`

#### Scenario: Tracked file
- **WHEN** `envFile` names a file that Git tracks in the worktree
- **THEN** it isn't written and the operation fails with an error naming the file

#### Scenario: Outside the worktree
- **WHEN** `envFile` is `../shared.env` or an absolute path
- **THEN** the file is reported as invalid and nothing is written

#### Scenario: Through a symlinked folder
- **WHEN** `envFile` is `cfg/.zshenv` and the worktree tracks `cfg` as a symbolic link to a folder outside it
- **THEN** nothing is written outside the worktree and the operation fails with an error naming the file

#### Scenario: Value with shell syntax
- **WHEN** a variable resolves to `` a`id`$(id)$HOME ``
- **THEN** sourcing the environment file from `sh` sets the variable to exactly that text and runs no command
