# worktree-environment Specification

## Purpose
Defines the per-worktree environment: the variables a repository declares for each worktree, how their templates resolve, how ports are reserved and kept per worktree, and the optional environment file written into each worktree for tools that read dotenv files.

## Requirements

### Requirement: Declare the worktree environment
The system SHALL read the optional `env` field of `.shogunai.json` as an object whose keys are environment variable names and whose values are string templates. A repository without the field declares an empty environment. A non-object `env`, a non-string value, or a key that isn't a valid variable name (letters, digits and `_`, not starting with a digit) SHALL make the file invalid.

#### Scenario: Variables declared
- **WHEN** `.shogunai.json` contains `{ "env": { "PORT": "${port}", "APP_SUFFIX": "-${worktree}" } }`
- **THEN** the repository's environment declares `PORT` and `APP_SUFFIX` with those templates

#### Scenario: No environment
- **WHEN** `.shogunai.json` has no `env` field, or the repository has no `.shogunai.json`
- **THEN** every worktree's environment is empty and nothing is reserved or written

#### Scenario: Invalid declaration
- **WHEN** `env` is `{ "PORT": 3000 }` or `{ "1PORT": "x" }`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`

### Requirement: Resolve variable templates
The system SHALL resolve each template for a given worktree, replacing `${worktree}` with the worktree's folder name, `${port}` with the port reserved for that variable, `${port:<key>}` with the port reserved for that key (shared by every variable using it), and `$$` with `$`. Any other `${…}` placeholder SHALL make the file invalid.

#### Scenario: Worktree name
- **WHEN** `APP_SUFFIX` is `-${worktree}` and the worktree's folder is `TASK-123`
- **THEN** `APP_SUFFIX` resolves to `-TASK-123`

#### Scenario: Shared port key
- **WHEN** `API_PORT` is `${port:api}` and `API_URL` is `http://localhost:${port:api}`
- **THEN** both use the same reserved port

#### Scenario: One port per variable
- **WHEN** `PORT` and `DEBUG_PORT` are both `${port}`
- **THEN** they resolve to two different ports

#### Scenario: Literal dollar
- **WHEN** a template is `price$$5`
- **THEN** it resolves to `price$5`

#### Scenario: Unknown placeholder
- **WHEN** a template contains `${branch}`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json` and the placeholder

### Requirement: Reserve ports per worktree
The system SHALL reserve each port the first time a worktree needs it, choosing the lowest port in the requested range that no worktree on the machine has reserved, in any repository whose reservations ShogunAi has stored, and that is free on the machine at that moment. Variable ports use the range 20000 to 29999. Once reserved, a port SHALL stay the worktree's own, even if it is in use when read again. Running out of ports SHALL fail with an error.

#### Scenario: First reservation
- **WHEN** a worktree needs a port and no worktree has reserved one
- **THEN** it gets the lowest free port from 20000 up

#### Scenario: Unique among worktrees
- **WHEN** another worktree of the repository already reserved 20000
- **THEN** the new worktree gets a different port

#### Scenario: Unique across repositories
- **WHEN** a worktree of another repository on the same machine already reserved 20000, even if nothing listens on it
- **THEN** the new worktree gets a different port

#### Scenario: Removed worktree of another repository
- **WHEN** a worktree of another repository that had reserved 20000 has been removed
- **THEN** 20000 can be reserved again

#### Scenario: Port busy on the machine
- **WHEN** 20000 isn't reserved by any worktree but another program is listening on it
- **THEN** it's skipped and the next free port is reserved

#### Scenario: Stable reservation
- **WHEN** a worktree's environment is resolved again, even while its dev server is listening on its reserved port
- **THEN** it gets the same port as before

### Requirement: Keep reservations with the worktree
The system SHALL store a worktree's reservations in Git's administrative directory for that worktree (`.git/worktrees/<id>/` for a linked worktree, the repository's `.git/` for the main one), never in the working tree, so they disappear when Git removes or prunes the worktree. Reservations whose key is no longer used by the declaration SHALL be released the next time the environment is resolved.

#### Scenario: Worktree removed
- **WHEN** a worktree with reserved ports is removed with `git worktree remove` and a new worktree is created
- **THEN** the removed worktree's ports can be reserved again

#### Scenario: Worktree created by another tool
- **WHEN** a worktree was created with `git worktree add` and its environment is asked for
- **THEN** its ports are reserved and stored the same way

#### Scenario: Variable removed from the declaration
- **WHEN** `DEBUG_PORT` is removed from `env` and the environment is resolved again
- **THEN** its port is released and the other variables keep theirs

#### Scenario: Working tree untouched
- **WHEN** a worktree's environment is resolved and no `envFile` is declared
- **THEN** no file is created or changed in the worktree's folder

### Requirement: Write the environment file
When `.shogunai.json` declares `envFile` as a path relative to the worktree root, the system SHALL write the worktree's resolved variables to that path in dotenv format, replacing its previous contents. It SHALL NOT write over a file tracked by Git, nor a path outside the worktree, and a non-string or absolute `envFile` SHALL make the file invalid.

#### Scenario: File written
- **WHEN** `envFile` is `.env.shogunai` and the worktree's `PORT` resolves to 20001
- **THEN** `.env.shogunai` in the worktree contains `PORT=20001`

#### Scenario: Tracked file
- **WHEN** `envFile` names a file that Git tracks in the worktree
- **THEN** it isn't written and the operation fails with an error naming the file

#### Scenario: Outside the worktree
- **WHEN** `envFile` is `../shared.env` or an absolute path
- **THEN** the file is reported as invalid and nothing is written

### Requirement: Providers add variables to the environment
A provider declared by the repository SHALL be able to reserve ports in its own range and add variables to every worktree's environment, delivered the same way as declared variables. A variable with the same name declared in `env` SHALL take precedence over the provider's.

#### Scenario: Provider variable
- **WHEN** the repository declares the emulator provider and no `env`
- **THEN** each worktree's environment contains the provider's `ANDROID_SERIAL`

#### Scenario: Declared variable wins
- **WHEN** the repository declares the emulator provider and `env` sets `ANDROID_SERIAL` to `emulator-5600`
- **THEN** the worktree's environment has `ANDROID_SERIAL=emulator-5600`
