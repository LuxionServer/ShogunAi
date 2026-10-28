# Spec Delta

## MODIFIED Requirements

### Requirement: Show and declare the workspace repository
`shogun multirepo show` SHALL list the workspace repository, marked `detected` when the declaration doesn't state it, with `workspace` in its `--json` object. `shogun multirepo workspace set [--alias <alias>] [--default]` SHALL declare it, `workspace clear` SHALL set `"workspace": false`, and `workspace detect` SHALL remove the `workspace` field so the folder is detected again.

#### Scenario: Show a detected workspace
- **WHEN** the user runs `shogun multirepo show` in a multirepo whose folder is a Git repository and whose declaration says nothing about it
- **THEN** the output lists `workspace` as detected, with the command to declare it

#### Scenario: Declare it
- **WHEN** the user runs `shogun multirepo workspace set --default`
- **THEN** the declaration holds `"workspace": { "alias": "workspace", "default": true }` and nothing else changes

#### Scenario: Turn it off
- **WHEN** the user runs `shogun multirepo workspace clear`
- **THEN** the declaration holds `"workspace": false` and the folder is no longer a repository of the multirepo

#### Scenario: Not a repository
- **WHEN** the user runs `workspace set` or `workspace detect` in a multirepo folder that isn't a Git repository
- **THEN** the command says there is no workspace repository, changes nothing and exits with code 1

#### Scenario: Back to detection after turning it off
- **WHEN** the declaration holds `"workspace": false` and the user runs `shogun multirepo workspace detect`
- **THEN** the declaration has no `workspace` field, `shogun multirepo show` lists `workspace` as detected, and the multirepo is still approved

#### Scenario: Back to detection from a declared alias
- **WHEN** the declaration holds `"workspace": { "alias": "docs-root", "default": true }` and the user runs `shogun multirepo workspace detect`
- **THEN** the workspace repository is `workspace`, it isn't in the default repositories, and the output says both

#### Scenario: Already detected
- **WHEN** the declaration has no `workspace` field and the user runs `shogun multirepo workspace detect`
- **THEN** the command says the workspace repository is already detected, changes nothing and exits with code 0

#### Scenario: Default alias taken
- **WHEN** `repos` declares a repository with the alias `workspace` and the user runs `shogun multirepo workspace detect`
- **THEN** the command says the alias `workspace` is taken and to keep the workspace repository declared with `workspace set --alias`, changes nothing and exits with code 1

#### Scenario: Folder declared in repos
- **WHEN** `repos` lists the folder with the path `.`
- **THEN** `workspace detect` says the workspace repository is declared in `repos`, changes nothing and exits with code 1
