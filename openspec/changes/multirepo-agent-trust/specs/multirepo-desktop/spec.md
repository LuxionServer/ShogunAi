# Spec Delta

## MODIFIED Requirements

### Requirement: Add a multirepo from the app
The user SHALL be able to add a multirepo by dropping a folder on the sidebar or choosing it with "Add multirepo". Before confirming, the app SHALL list the Git repositories directly inside the folder, and any added from elsewhere, with their aliases, which already are projects, the proposed worktrees folder of each (optional) and the groups detected by branch. When the folder already has a declaration, the dialog SHALL also list its declared repositories with their resolved paths, its worktrees root and its workspace repository, marking paths outside the folder's parent. Confirming SHALL write the declaration as `shogun multirepo init` and `add-repo` do, and register and approve it.

#### Scenario: Detected repositories
- **WHEN** the user drops `~/projects/product`, which contains `product-app`, `product-api`, `product-docs` and a folder `notes`
- **THEN** the dialog lists the three repositories with proposed aliases, marks `product-app` as already a project, proposes `~/projects/product-worktrees/app` and `.../api` as worktrees folders, and lists the group `feature/TASK-7` detected in `app` and `api`

#### Scenario: Confirm
- **WHEN** the user keeps `app` and `api`, accepts the proposed folder only for `api` and confirms
- **THEN** `~/projects/product/.shogunai-multirepo.json` declares them, `product-api` is registered with worktrees in `~/projects/product-worktrees/api`, `product-app`'s project is unchanged, the multirepo is approved, and the sidebar shows the folder

#### Scenario: Folder with an existing declaration
- **WHEN** the user drops a cloned `~/projects/product` whose declaration lists `api` as `/srv/other/api`
- **THEN** the dialog lists `api` with `/srv/other/api` marked as outside `~/projects`, and only confirming registers and approves it

#### Scenario: Folder inside a repository
- **WHEN** the dropped folder is inside a Git repository
- **THEN** the dialog says a multirepo can't be declared inside a repository and offers no confirmation

### Requirement: Multirepo settings
The multirepo SHALL have a settings screen to add, remove and rename repositories, choose the default repositories, set the proposed worktrees folder and edit the multirepo agent with the same agent editor projects use, and to delete the multirepo, saving to the declaration as the `shogun multirepo` subcommands do and the multirepo agent to the user's ShogunAi folder. The screen SHALL say when the declaration holds an ignored `agent`, and SHALL NOT save while the multirepo needs review.

#### Scenario: Change the default repositories
- **WHEN** the user unchecks `api` in the default repositories and saves
- **THEN** `defaultRepos` is `["app"]` and `shogun multirepo show` reports it

#### Scenario: Change the agent
- **WHEN** the user sets the multirepo agent to `codex` and saves
- **THEN** `.shogunai-multirepo.json` is unchanged and `shogun open --herdr` in the multirepo folder runs `headroom wrap codex`

#### Scenario: Delete the multirepo
- **WHEN** the user deletes the multirepo and confirms
- **THEN** the declaration is removed, its projects go back to the top level of the sidebar unchanged, and no worktree is touched

#### Scenario: Changed from the command line
- **WHEN** `shogun multirepo add-repo docs product-docs` runs while the app is open
- **THEN** the app shows `docs` in the multirepo after its next refresh

#### Scenario: Needs review
- **WHEN** the multirepo's declaration changed outside ShogunAi
- **THEN** the settings screen points to the review and saves nothing
