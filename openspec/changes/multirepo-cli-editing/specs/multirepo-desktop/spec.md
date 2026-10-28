# Spec Delta

## MODIFIED Requirements

### Requirement: Multirepo settings
The multirepo SHALL have a settings screen to set its name, add, remove and rename repositories, choose the defaults, set the proposed worktrees folder, edit the shared variables, edit the multirepo agent with the projects' agent editor and delete the multirepo, saving as the `shogun multirepo` subcommands do, with the agent in the user's ShogunAi folder. The screen SHALL say when the declaration holds an ignored `agent`, and SHALL NOT save while the multirepo needs review.

#### Scenario: Set the name
- **WHEN** the user types `Product` in the name field and saves
- **THEN** the declaration's `name` is `Product`, the sidebar shows `Product`, and emptying the field and saving removes `name` so the folder's name is shown again

#### Scenario: Change the default repositories
- **WHEN** the user unchecks `api` in the default repositories and saves
- **THEN** `defaultRepos` is `["app"]` and `shogun multirepo show` reports it

#### Scenario: Change the agent
- **WHEN** the user sets the multirepo agent to `codex` and saves
- **THEN** `.shogunai-multirepo.json` is unchanged and `shogun open --herdr` in the multirepo folder runs `codex`, without Headroom unless the user turned it on

#### Scenario: Delete the multirepo
- **WHEN** the user deletes the multirepo and confirms
- **THEN** the declaration is removed, its projects go back to the top level of the sidebar unchanged, and no worktree is touched

#### Scenario: Changed from the command line
- **WHEN** `shogun multirepo add-repo docs product-docs` runs while the app is open
- **THEN** the app shows `docs` in the multirepo after its next refresh

#### Scenario: Needs review
- **WHEN** the multirepo's declaration changed outside ShogunAi
- **THEN** the settings screen points to the review and saves nothing

### Requirement: Workspace repository in the multirepo settings
The multirepo settings SHALL show the workspace repository, marked as detected when the declaration doesn't state it, with a checkbox to declare it, a field for its alias, a checkbox to include it in the default repositories, an option to turn detection off and, when it is declared or turned off, "Return to detection", saving as `shogun multirepo workspace` does.

#### Scenario: Declare a detected workspace
- **WHEN** the settings of `product` show `workspace` as detected and the user checks "Declare" and saves
- **THEN** the declaration holds `"workspace": { "alias": "workspace" }` and the row is no longer marked as detected

#### Scenario: No workspace repository
- **WHEN** the multirepo folder isn't a Git repository
- **THEN** the settings show no workspace option

#### Scenario: New change with the workspace repository
- **WHEN** the workspace repository is declared as a default repository
- **THEN** "New change" lists it with its checkbox checked

#### Scenario: Return a turned off workspace to detection
- **WHEN** the declaration holds `"workspace": false` and the user activates "Return to detection"
- **THEN** the declaration has no `workspace` field, the settings show `workspace` as detected, and the multirepo is still approved

#### Scenario: Detected workspace
- **WHEN** the workspace repository is detected
- **THEN** the settings don't offer "Return to detection"

## ADDED Requirements

### Requirement: Shared variables in the multirepo settings
The multirepo settings SHALL list each shared variable with its repository, name and template, let the user edit its template, remove it and add one choosing the repository, and save them with the rest of the settings as `shogun multirepo env` does. An invalid row SHALL be marked with its problem and SHALL keep the settings from saving.

#### Scenario: Add a variable
- **WHEN** the user adds `API_URL` for `app` with the template `http://localhost:${port:api.http}` and saves
- **THEN** `env.app.API_URL` holds that template, `shogun multirepo env` lists it, and the multirepo is still approved

#### Scenario: Remove a variable
- **WHEN** the user removes `app`'s `API_URL`, its last shared variable, and saves
- **THEN** the declaration has no `env`

#### Scenario: Invalid row
- **WHEN** the user types `${port}` as a template
- **THEN** the row says ports a repository uses itself are declared in its own `.shogunai.json`, and "Save" stays disabled until it's fixed or removed

#### Scenario: Variables ShogunAi can't rewrite
- **WHEN** the declaration's `env` holds a non-string value
- **THEN** the section names the problem, says to fix the file by hand, and doesn't offer editing
