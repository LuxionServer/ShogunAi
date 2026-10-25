## ADDED Requirements

### Requirement: Register an existing multirepo from the app
Adding a multirepo from the app with a folder that already has a valid declaration SHALL show its declared repositories instead of the detected ones and, on confirmation, register it as `shogun multirepo init` does there, leaving the declaration unchanged. An invalid declaration SHALL be reported with its problem and offer no confirmation.

#### Scenario: Moved multirepo dropped on the sidebar
- **WHEN** the user drops `~/work/product`, whose declaration lists `app` and `api`, and the multirepo isn't known
- **THEN** the dialog says the folder is already a multirepo, lists `app` and `api` with their paths and whether each already is a project, and confirming makes the sidebar show the folder with both projects

#### Scenario: Already known
- **WHEN** the dropped folder is a multirepo the app already shows
- **THEN** the dialog says so and confirming only selects it in the sidebar

#### Scenario: Invalid declaration
- **WHEN** the dropped folder's declaration names a repository that doesn't exist
- **THEN** the dialog shows the problem and the file and offers no confirmation

### Requirement: The app forgets multirepos whose folder is gone
When the app reloads the known multirepos it SHALL remove from `~/.shogunai/multirepos.json` every entry whose folder no longer exists, as `shogun multirepo init` does.

#### Scenario: Folder deleted while the app is open
- **WHEN** `~/projects/product` is deleted and the app reloads
- **THEN** the folder `product` leaves the sidebar, its projects stay listed at the top level, and `~/.shogunai/multirepos.json` no longer lists it

## MODIFIED Requirements

### Requirement: Multirepo folder in the sidebar
The sidebar SHALL show each known multirepo as a collapsible folder named with its display name, containing its repositories' projects, with its collapsed state remembered across restarts. Selecting the multirepo SHALL open the multirepo view, titled with the same name, and selecting one of its projects that project's usual view.

#### Scenario: Multirepo listed
- **WHEN** the multirepo `product` has `app`, `api` and `docs`, the project `tools` belongs to no multirepo, and the user hasn't reordered the sidebar
- **THEN** the sidebar shows `▾ product` with `api`, `app` and `docs` inside, and `tools` at the top level, in alphabetical order

#### Scenario: Declared name
- **WHEN** the multirepo in `~/projects/product-workspace` declares `name` `Product`
- **THEN** the sidebar folder and the multirepo view's title read `Product`

#### Scenario: Open the multirepo view
- **WHEN** the user selects `product`
- **THEN** the content area shows the multirepo view of `product`

#### Scenario: Collapse
- **WHEN** the user collapses `product` and restarts the app
- **THEN** `product` is still collapsed

### Requirement: Multirepo settings
The multirepo SHALL have a settings screen to set its display name, add, remove and rename repositories, choose the default repositories, set the proposed worktrees folder and edit the multirepo agent with the same agent editor projects use, and to delete the multirepo, saving to the declaration as the `shogun multirepo` subcommands do.

#### Scenario: Set the name
- **WHEN** the user types `Product` in the name field and saves
- **THEN** the declaration's `name` is `Product`, the sidebar shows `Product`, and emptying the field and saving removes `name` so the folder's name is shown again

#### Scenario: Change the default repositories
- **WHEN** the user unchecks `api` in the default repositories and saves
- **THEN** `defaultRepos` is `["app"]` and `shogun multirepo show` reports it

#### Scenario: Delete the multirepo
- **WHEN** the user deletes the multirepo and confirms
- **THEN** the declaration is removed, its projects go back to the top level of the sidebar unchanged, and no worktree is touched

#### Scenario: Changed from the command line
- **WHEN** `shogun multirepo add-repo docs product-docs` runs while the app is open
- **THEN** the app shows `docs` in the multirepo after its next refresh
