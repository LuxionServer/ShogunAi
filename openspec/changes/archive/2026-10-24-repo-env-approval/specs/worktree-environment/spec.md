## MODIFIED Requirements

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

## ADDED Requirements

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
