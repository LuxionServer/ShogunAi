# multirepo-desktop Specification

## Purpose
Shows multirepos and their groups in the desktop app, and lets the user add and configure a multirepo and create a change across several repositories without the command line, writing the same declaration as `shogun multirepo`.

## ADDED Requirements

### Requirement: Multirepo folder in the sidebar
The sidebar SHALL show each known multirepo as a collapsible folder named after it, containing its repositories' projects, with its collapsed state remembered across restarts. Selecting the multirepo SHALL open the multirepo view, and selecting one of its projects that project's usual view.

#### Scenario: Multirepo listed
- **WHEN** the multirepo `product` has `app`, `api` and `docs`, and the project `tools` belongs to no multirepo
- **THEN** the sidebar shows `▾ product` with `api`, `app` and `docs` inside, and `tools` at the top level, in alphabetical order

#### Scenario: Open the multirepo view
- **WHEN** the user selects `product`
- **THEN** the content area shows the multirepo view of `product`

#### Scenario: Collapse
- **WHEN** the user collapses `product` and restarts the app
- **THEN** `product` is still collapsed

### Requirement: Multirepo activity in the sidebar
The multirepo's entry SHALL show the sum of its projects' active worktrees and the agent indicator when an agent runs in any of their worktrees, so the activity stays visible when the folder is collapsed.

#### Scenario: Agent in a repository of the multirepo
- **WHEN** `app` has two active worktrees and `api` one with an agent running
- **THEN** `product` shows 3 and the agent indicator

### Requirement: Multirepo view grouped by change
The multirepo view SHALL list its repositories' worktrees grouped by change by default: one group per group of the multirepo, then an "Ungrouped" block with the remaining worktrees. Rows SHALL be the usual worktree rows with the repository's alias in their first column. The view SHALL also offer grouping by state, and remember the chosen grouping.

#### Scenario: Grouped by change
- **WHEN** `product` has the groups `feature/TASK-1` (app, api) and `feature/TASK-2` (app, docs) and a loose `api` worktree
- **THEN** the view shows `TASK-1` with an `app` row and an `api` row, `TASK-2` with an `app` row and a `docs` row, and "Ungrouped" with the loose `api` row

#### Scenario: Grouped by state
- **WHEN** the user switches the multirepo view to group by state
- **THEN** its worktrees are grouped by state as in "Active", each row still showing its alias

### Requirement: Group header with a combined state
Each change group's header SHALL show as its title the group's branch without the prefix up to its last `/` (`TASK-12`), with the full branch as detail, its repositories' aliases and a combined state: "Merged" only when every worktree of the group is merged, and otherwise the most urgent state among its worktrees in the order of the worktree states.

#### Scenario: One worktree needs the user
- **WHEN** `app`'s worktree on `feature/TASK-1` is "Agent working" and `api`'s is "Needs you"
- **THEN** the header shows `TASK-1`, the detail `feature/TASK-1` and "Needs you"

#### Scenario: Partly merged
- **WHEN** `app`'s worktree of the group is "Merged" and `api`'s is "Stopped"
- **THEN** the header shows "Stopped", not "Merged"

#### Scenario: All merged
- **WHEN** every worktree of the group is "Merged"
- **THEN** the header shows "Merged"

### Requirement: Group header actions
Each change group's header SHALL offer "Open all in Herdr", which opens every worktree of the group as `shogun open <id> --herdr` does, "Add repository", which creates a worktree on the group's branch in a repository of the multirepo not yet in it, as `shogun new <id> --repos <alias>` does, and "Clean up group", enabled only when the group is a cleanup candidate and confirmed before removing it as `shogun clean --yes` would.

#### Scenario: Open all
- **WHEN** the user activates "Open all in Herdr" on `feature/TASK-1`
- **THEN** `app` and `api` open in Herdr under their repositories' spaces with their own environments and agents

#### Scenario: Add a repository to a group
- **WHEN** the user activates "Add repository" on `feature/TASK-1` and picks `docs`
- **THEN** `docs`'s worktree is created on `feature/TASK-1` in `docs`'s project worktrees folder and appears in the group

#### Scenario: Group not ready to clean up
- **WHEN** `api`'s worktree of the group isn't merged
- **THEN** "Clean up group" is disabled and its tooltip says `api` isn't merged

#### Scenario: Clean up a group
- **WHEN** the user activates "Clean up group" on a candidate and confirms
- **THEN** its worktrees are removed and the dialog listed them and their Herdr workspaces before confirming, with the same option to delete merged branches as "Clean up"

### Requirement: Open the multirepo agent from the app
The multirepo view SHALL offer "Open multirepo agent in Herdr", which opens the multirepo agent as `shogun open --herdr` does in the multirepo folder.

#### Scenario: Open the multirepo agent
- **WHEN** the user activates it in `product`'s view
- **THEN** the multirepo agent's Herdr workspace opens in the multirepo folder, or is focused if open

### Requirement: Group tag in other views
In every view other than the multirepo view, the row of a worktree that belongs to a group SHALL show a tag with the group's title and the other repositories of the group (e.g. `TASK-12 · +api`), and activating the tag SHALL open the multirepo view with that group shown and selected.

#### Scenario: Tag in a repository's view
- **WHEN** `app`'s view lists its worktree on `feature/TASK-12`, a group with `app` and `api`
- **THEN** the row shows `TASK-12 · +api`

#### Scenario: Follow the tag
- **WHEN** the user activates the tag
- **THEN** the multirepo view opens with that group expanded and scrolled into view

### Requirement: Add a multirepo from the app
The user SHALL be able to add a multirepo by dropping a folder on the sidebar or choosing it with "Add multirepo". Before confirming, the app SHALL list the Git repositories directly inside the folder, and any added from elsewhere, with their aliases, which already are projects, the proposed worktrees folder of each (optional) and the groups detected by branch. Confirming SHALL write the declaration as `shogun multirepo init` and `add-repo` do.

#### Scenario: Detected repositories
- **WHEN** the user drops `~/projects/product`, which contains `product-app`, `product-api`, `product-docs` and a folder `notes`
- **THEN** the dialog lists the three repositories with proposed aliases, marks `product-app` as already a project, proposes `~/projects/product-worktrees/app` and `.../api` as worktrees folders, and lists the group `feature/TASK-7` detected in `app` and `api`

#### Scenario: Confirm
- **WHEN** the user keeps `app` and `api`, accepts the proposed folder only for `api` and confirms
- **THEN** `~/projects/product/.shogunai-multirepo.json` declares them, `product-api` is registered with worktrees in `~/projects/product-worktrees/api`, `product-app`'s project is unchanged, and the sidebar shows the folder

#### Scenario: Folder inside a repository
- **WHEN** the dropped folder is inside a Git repository
- **THEN** the dialog says a multirepo can't be declared inside a repository and offers no confirmation

### Requirement: Multirepo settings
The multirepo SHALL have a settings screen to add, remove and rename repositories, choose the default repositories, set the proposed worktrees folder and edit the multirepo agent with the same agent editor projects use, and to delete the multirepo, saving to the declaration as the `shogun multirepo` subcommands do.

#### Scenario: Change the default repositories
- **WHEN** the user unchecks `api` in the default repositories and saves
- **THEN** `defaultRepos` is `["app"]` and `shogun multirepo show` reports it

#### Scenario: Delete the multirepo
- **WHEN** the user deletes the multirepo and confirms
- **THEN** the declaration is removed, its projects go back to the top level of the sidebar unchanged, and no worktree is touched

#### Scenario: Changed from the command line
- **WHEN** `shogun multirepo add-repo docs product-docs` runs while the app is open
- **THEN** the app shows `docs` in the multirepo after its next refresh

### Requirement: New change from the app
The multirepo view SHALL offer "New change", a dialog like "New worktree" with the change id, feature or fix, fetching first, and one checkbox per repository with the default repositories checked, that creates the group as `shogun new <id> --repos` does and reports each repository's result.

#### Scenario: Create a change
- **WHEN** the user enters `TASK-20`, keeps `app` and `api` checked and confirms
- **THEN** both worktrees are created on `feature/TASK-20` in their projects' worktrees folders, the group appears in the multirepo view and nothing is opened

#### Scenario: One repository fails
- **WHEN** `api`'s post-creation command fails
- **THEN** the dialog reports `api`'s failure, `app` stays created and appears in the group
