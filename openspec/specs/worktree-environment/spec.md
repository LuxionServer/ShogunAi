# worktree-environment Specification

## Purpose
Defines the per-worktree environment: the variables a repository declares for each worktree, how their templates resolve, how ports are reserved and kept per worktree, and the optional environment file written into each worktree for tools that read dotenv files.

## Requirements

### Requirement: Declare the worktree environment
The system SHALL read the optional `env` field of `.shogunai.json` as an object whose keys are environment variable names and whose values are string templates. A repository without the field declares an empty environment. A non-object `env`, a non-string value, a key that isn't a valid variable name (letters, digits and `_`, not starting with a digit), a reserved name, or a template containing a line break, another control character or an invisible Unicode format character SHALL make the file invalid. Reserved names are those that change how shells, language runtimes, the dynamic loader, Git, package managers, compilers, build tools, network clients or coding agents behave, compared without regard to case.

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

#### Scenario: Names that change other tools
- **WHEN** `env` declares `XDG_CONFIG_HOME`, `PAGER`, `EDITOR`, `LESSOPEN`, `SSH_ASKPASS`, `HTTPS_PROXY`, `NODE_TLS_REJECT_UNAUTHORIZED`, `npm_config_registry`, `PIP_INDEX_URL`, `GOFLAGS`, `RUSTC_WRAPPER`, `JAVA_OPTS`, `CC`, `DOCKER_HOST`, `KUBECONFIG`, `ANTHROPIC_BASE_URL` or `CLAUDE_CONFIG_DIR`
- **THEN** the file is reported as invalid with an error that names the variable as reserved, whether or not the repository was approved

#### Scenario: Reserved name in another case
- **WHEN** `env` declares `https_proxy` or `Path`
- **THEN** the file is reported as invalid with an error that names the variable as reserved

#### Scenario: Ordinary application names
- **WHEN** `env` declares `PORT`, `DATABASE_URL`, `NODE_ENV`, `VITE_API_URL` and `ANDROID_SERIAL`
- **THEN** the declaration is valid

#### Scenario: Line break in a value
- **WHEN** `env` declares `{ "APP_NAME": "demo\nexport OTHER=1" }`
- **THEN** the file is reported as invalid with an error that names `APP_NAME`

#### Scenario: Invisible characters in a value
- **WHEN** a template contains an escape character, a carriage return or a bidirectional override such as U+202E
- **THEN** the file is reported as invalid with an error that names the variable

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
When `.shogunai.json` declares `envFile` as a path relative to the worktree root and the repository is approved (see `repository-trust`), the system SHALL write the worktree's resolved variables to that path in dotenv format, replacing its previous contents, with every value written so that sourcing the file from a shell never expands or runs anything in it. It SHALL NOT write the file while the repository isn't approved, over a file tracked by Git, nor to a path whose real location, following symbolic links, is outside the worktree. A non-string or absolute `envFile`, or one containing a control or invisible format character, SHALL make the file invalid.

#### Scenario: File written
- **WHEN** `envFile` is `.env.shogunai`, the repository is approved and the worktree's `PORT` resolves to 20001
- **THEN** `.env.shogunai` in the worktree contains `PORT=20001`

#### Scenario: Not approved
- **WHEN** `envFile` is `.env.shogunai` and the repository isn't approved
- **THEN** no environment file is written

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

### Requirement: Providers add variables to the environment
A provider declared by the repository SHALL be able to reserve ports in its own range and add variables to every worktree's environment, delivered the same way as declared variables. A provider's port MAY be on demand: then resolving the environment SHALL only read it, never reserve it, and the provider's variables SHALL appear once it has been acquired. A variable with the same name declared in `env` SHALL take precedence over the provider's.

#### Scenario: Provider variable
- **WHEN** the repository declares the emulator provider and no `env`, and a worktree has started its emulator
- **THEN** that worktree's environment contains the provider's `ANDROID_SERIAL`

#### Scenario: On-demand port not acquired
- **WHEN** the repository declares the emulator provider and a worktree's environment is resolved with reservation before its emulator was ever started
- **THEN** no console port is reserved and the other variables are delivered without `ANDROID_SERIAL`

#### Scenario: Declared variable wins
- **WHEN** the repository declares the emulator provider and `env` sets `ANDROID_SERIAL` to `emulator-5600`
- **THEN** the worktree's environment has `ANDROID_SERIAL=emulator-5600`

### Requirement: Providers keep resources with the worktree
A provider SHALL be able to keep a per-worktree resource other than a port (such as a simulator's UDID) with the worktree's reservations, readable without creating it. An on-demand resource SHALL be created only when it is acquired, checked and recreated when it no longer exists only when it is acquired again, and released by removing it from the reservations. The resource SHALL be stored with the worktree's ports, so removing the worktree's Git administrative data drops it.

#### Scenario: Created once
- **WHEN** a worktree acquires a provider's resource twice
- **THEN** the resource is created the first time and reused the second

#### Scenario: Read without creating
- **WHEN** a worktree's environment is resolved, with or without reservation, and its on-demand resource doesn't exist yet
- **THEN** nothing is created and no variable for it is returned

#### Scenario: Resource gone
- **WHEN** the recorded resource no longer exists
- **THEN** the next acquisition creates a new one and records it, and resolving the environment alone creates nothing

#### Scenario: Released
- **WHEN** a worktree's resource is released
- **THEN** it's removed from the worktree's reservations and the worktree's other ports and resources are kept

### Requirement: Declared variables apply only once approved
The variables a repository declares in `env` SHALL be part of a worktree's resolved environment only while the repository is approved on this machine (see `repository-trust`). While it isn't, resolving the environment SHALL leave them out, reserve no port for them, and report their names as awaiting approval; variables added by providers SHALL still be delivered.

#### Scenario: Approved repository
- **WHEN** the repository declares `PORT` as `${port}` and is approved
- **THEN** the worktree's environment has `PORT` with its reserved port

#### Scenario: Not approved
- **WHEN** the repository declares `PORT` as `${port}` and isn't approved
- **THEN** the worktree's environment has no `PORT`, no port is reserved for it, and `PORT` is reported as awaiting approval

#### Scenario: Provider variables without approval
- **WHEN** the repository declares the emulator provider and `env`, isn't approved, and a worktree has started its emulator
- **THEN** the worktree's environment has the provider's `ANDROID_SERIAL` and none of the declared variables

#### Scenario: Declared override not approved
- **WHEN** the repository declares the emulator provider and `env` sets `ANDROID_SERIAL` to `emulator-5600`, and it isn't approved
- **THEN** the worktree's environment has the provider's `ANDROID_SERIAL`, not `emulator-5600`
