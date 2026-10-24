## MODIFIED Requirements

### Requirement: Edit the worktree environment
The section SHALL list the variables of `env` with their name and template, let the user add, edit and remove them, and edit the environment file path. Names SHALL follow the variable name rule and SHALL NOT be reserved names, templates SHALL NOT contain line breaks, other control characters or invisible format characters, and the environment file SHALL be a relative path inside the worktree. The section SHALL explain the `${port}`, `${port:key}` and `${worktree}` templates.

#### Scenario: Add a port variable
- **WHEN** the user adds `PORT` with the template `${port}`, sets the environment file to `.env.shogunai` and saves
- **THEN** `.shogunai.json` contains `"env": { "PORT": "${port}" }` and `"envFile": ".env.shogunai"`

#### Scenario: Invalid variable name
- **WHEN** the user enters `1PORT` as a name
- **THEN** the field explains that names use letters, digits and `_`, not starting with a digit, and saving is blocked

#### Scenario: Environment file outside the worktree
- **WHEN** the user enters `../shared.env` as the environment file
- **THEN** the field explains it must be a path inside the worktree, and saving is blocked

#### Scenario: Reserved name
- **WHEN** the user enters `XDG_CONFIG_HOME` or `https_proxy` as a name
- **THEN** the field explains that the name is reserved because it changes how other tools behave, and saving is blocked

#### Scenario: Line break in a template
- **WHEN** the user pastes a template that contains a line break
- **THEN** the field explains that templates can't contain line breaks or control characters, and saving is blocked
