## MODIFIED Requirements

### Requirement: Multirepo settings
The multirepo SHALL have a settings screen to add, remove and rename repositories, choose the default repositories, set the proposed worktrees folder and edit the multirepo agent with the same agent editor projects use, and to delete the multirepo, saving to the declaration as the `shogun multirepo` subcommands do and the multirepo agent to the user's ShogunAi folder. The screen SHALL say when the declaration holds an ignored `agent`, and SHALL NOT save while the multirepo needs review.

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
