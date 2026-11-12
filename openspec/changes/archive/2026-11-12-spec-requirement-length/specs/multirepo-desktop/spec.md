## MODIFIED Requirements

### Requirement: Add a multirepo from the app
The user SHALL be able to add a multirepo by dropping a folder on the sidebar or choosing it with "Add multirepo". Before confirming, the app SHALL list the Git repositories directly inside the folder, and any added from elsewhere, with their aliases, which already are projects, the proposed worktrees folder of each (optional) and the groups detected by branch. Confirming SHALL write the declaration as `shogun multirepo init` and `add-repo` do, and approve it.

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

### Requirement: New change from the app
The multirepo view SHALL offer "New change", a dialog like "New worktree" with the change id, feature or fix, fetching first, one checkbox per repository with the default repositories checked, and a selector of where to open the new worktrees. It SHALL create the group as `shogun new <id> --repos` does, report each repository's result and then open the group in the chosen runner as "Open all" does.

#### Scenario: Create a change
- **WHEN** the user enters `TASK-20`, keeps `app` and `api` checked, sets the selector to not opening and confirms
- **THEN** both worktrees are created on `feature/TASK-20` in their projects' worktrees folders, the group appears in the multirepo view and nothing is opened

#### Scenario: Selector starts on the runner in use
- **WHEN** Orca is the runner in use and installed, and the user opens "New change"
- **THEN** the selector starts on Orca

#### Scenario: Create and open in Orca
- **WHEN** the user picks Orca in the selector and confirms
- **THEN** both worktrees are created, then opened in Orca as "Open all in Orca" does, and the dialog reports each repository's creation and opening

#### Scenario: Create and open in Herdr
- **WHEN** the user picks Herdr in the selector and confirms
- **THEN** both worktrees are created, then opened in Herdr as "Open all in Herdr" does

#### Scenario: Runner not installed
- **WHEN** Orca isn't installed and Herdr is
- **THEN** the selector offers not opening and Herdr only, and with neither installed it isn't shown

#### Scenario: Opening fails after creating
- **WHEN** the worktrees are created but opening `api` in the chosen runner fails
- **THEN** the change stays created, `app` is opened, and the dialog shows `api`'s opening error

#### Scenario: One repository fails
- **WHEN** `api`'s post-creation command fails
- **THEN** the dialog reports `api`'s failure, and `app` and `api`'s kept worktree appear in the group, and only the worktrees that exist are opened when a runner was picked

#### Scenario: Unavailable local files in one repository
- **WHEN** `api` has a missing local file and the user confirms "New change" for `app` and `api` with a runner picked
- **THEN** the dialog reports `api`'s failure with the local files summary and `app` as not created because of `api`, in the selected language, no worktree is created and nothing is opened

## ADDED Requirements

### Requirement: Show a folder's existing declaration when adding it
When the folder added as a multirepo already has a declaration, the app SHALL show it as `shogun multirepo trust` shows it.

#### Scenario: Cloned declaration reviewed
- **WHEN** the user drops a cloned `~/projects/product` whose declaration lists `api` as `/srv/other/api`
- **THEN** the dialog lists `api` with `/srv/other/api` marked as outside `~/projects`, and only confirming registers and approves it

### Requirement: Where "New change" opens the worktrees
The selector of the "New change" dialog SHALL offer to open the new worktrees in Herdr or in Orca, or not to open them, starting on the runner in use when the selector offers it and on not opening them otherwise.

#### Scenario: Runner in use preselected
- **WHEN** Orca is the runner in use and installed, and the user opens "New change"
- **THEN** the selector starts on Orca

#### Scenario: No runner in use offered
- **WHEN** the runner in use isn't installed and the other runner is, and the user opens "New change"
- **THEN** the selector starts on not opening the worktrees
