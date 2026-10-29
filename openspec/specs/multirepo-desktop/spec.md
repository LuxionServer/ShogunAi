# multirepo-desktop Specification

## Purpose
Shows multirepos and their groups in the desktop app, and lets the user add and configure a multirepo and create a change across several repositories without the command line, writing the same declaration as `shogun multirepo`.

## Requirements

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
The multirepo view SHALL offer "Open multirepo agent in Herdr", which opens the multirepo agent as `shogun open --herdr` does in the multirepo folder, and SHALL tell apart, in English and Spanish, that the agent was opened, started in its already open workspace, or focused.

#### Scenario: Open the multirepo agent
- **WHEN** the user activates it in `product`'s view
- **THEN** the multirepo agent's Herdr workspace opens in the multirepo folder, or its live agent is focused if open

#### Scenario: Open workspace without an agent
- **WHEN** the multirepo's Herdr workspace is open but has no live agent
- **THEN** the agent is started in a new pane of it and the notice says the agent was started, not that it was focused

#### Scenario: Agent doesn't start
- **WHEN** Herdr doesn't detect the multirepo agent in time
- **THEN** the view shows the error naming the pane and the command

### Requirement: Group tag in other views
In every view other than the multirepo view, the row of a worktree that belongs to a group SHALL show a tag with the group's title and the other repositories of the group (e.g. `TASK-12 · +api`), and activating the tag SHALL open the multirepo view with that group shown and selected.

#### Scenario: Tag in a repository's view
- **WHEN** `app`'s view lists its worktree on `feature/TASK-12`, a group with `app` and `api`
- **THEN** the row shows `TASK-12 · +api`

#### Scenario: Follow the tag
- **WHEN** the user activates the tag
- **THEN** the multirepo view opens with that group expanded and scrolled into view

### Requirement: Add a multirepo from the app
The user SHALL be able to add a multirepo by dropping a folder on the sidebar or choosing it with "Add multirepo". Before confirming, the app SHALL list the Git repositories directly inside the folder, and any added from elsewhere, with their aliases, which already are projects, the proposed worktrees folder of each (optional) and the groups detected by branch. A folder's existing declaration SHALL be shown as `shogun multirepo trust` shows it. Confirming SHALL write the declaration as `shogun multirepo init` and `add-repo` do, and approve it.

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

### Requirement: New change from the app
The multirepo view SHALL offer "New change", a dialog like "New worktree" with the change id, feature or fix, fetching first, and one checkbox per repository with the default repositories checked, that creates the group as `shogun new <id> --repos` does and reports each repository's result.

#### Scenario: Create a change
- **WHEN** the user enters `TASK-20`, keeps `app` and `api` checked and confirms
- **THEN** both worktrees are created on `feature/TASK-20` in their projects' worktrees folders, the group appears in the multirepo view and nothing is opened

#### Scenario: One repository fails
- **WHEN** `api`'s post-creation command fails
- **THEN** the dialog reports `api`'s failure, and `app` and `api`'s kept worktree appear in the group

### Requirement: Multirepo agent in the multirepo view
The multirepo view SHALL show the multirepo agent running in the multirepo folder, with its state, next to "Open multirepo agent in Herdr". Only an agent whose working directory is exactly the multirepo folder SHALL count, and the sidebar entry of the multirepo SHALL reflect its activity.

#### Scenario: Agent working in the folder
- **WHEN** Claude Code runs in `~/projects/product` and is working
- **THEN** the multirepo view shows the agent as working and the multirepo's sidebar row shows activity

#### Scenario: Agent in a member repository
- **WHEN** an agent runs in `~/projects/product/product-app`
- **THEN** it shows under `app`'s worktree and not as the multirepo agent

#### Scenario: Agent managed by Herdr
- **WHEN** Herdr reports an agent whose directory is the multirepo folder
- **THEN** its state comes from Herdr as for a worktree's agent

### Requirement: Folder of a multirepo isn't listed as a project
The sidebar SHALL NOT list as a loose project one whose base repository is the multirepo folder; its worktrees SHALL appear under the multirepo as the workspace repository's, and the project SHALL keep its settings. With the declaration's `"workspace": false` it SHALL be listed as an ordinary project.

#### Scenario: Registered project for the folder
- **WHEN** a project is registered for `~/projects/product`, which is a multirepo folder and a Git repository
- **THEN** the sidebar shows no project outside the multirepo for it and its worktrees appear under the multirepo as `workspace`

#### Scenario: Add project from the folder
- **WHEN** the user adds `~/projects/product` as a project
- **THEN** the app explains that it's a multirepo and adds nothing

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

### Requirement: Register an existing multirepo from the app
Adding a multirepo from the app with a folder that already has a declaration SHALL show its review (each declared repository with its resolved path, the worktrees root and the workspace repository) and, on confirmation, approve it and register a project for each declared repository without one, as `shogun multirepo init` does there, leaving the declaration's repositories unchanged.

#### Scenario: Moved multirepo dropped on the sidebar
- **WHEN** the user drops `~/work/product`, whose declaration lists `app` and `api`, and the multirepo isn't known
- **THEN** the dialog says the folder already declares a multirepo and lists `app` and `api` with their paths, and confirming approves it and makes the sidebar show the folder with both projects

### Requirement: The app forgets multirepos whose folder is gone
When the app reloads the known multirepos it SHALL remove from `~/.shogunai/multirepos.json` every entry whose folder no longer exists, as `shogun multirepo init` does.

#### Scenario: Folder deleted while the app is open
- **WHEN** `~/projects/product` is deleted and the app reloads
- **THEN** the folder `product` leaves the sidebar, its projects stay listed at the top level, and `~/.shogunai/multirepos.json` no longer lists it

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

### Requirement: One representation of a multirepo in the sidebar
Every multirepo SHALL be shown as the same kind of row, approved or not and with or without repositories: a disclosure control in the row's leading slot followed by its name, styled as an item and never as a section header. Its repositories SHALL be indented one level, so their names start to the right of the multirepo's name, and top-level projects SHALL start where the views' labels start, after the leading slot.

#### Scenario: Children nested under the name
- **WHEN** the multirepo `product` is expanded with `api` and `app`, and `tools` is a top-level project
- **THEN** the names `api` and `app` start to the right of where `product` starts, and `tools` starts where `product` starts

#### Scenario: Multirepo without repositories
- **WHEN** the multirepo `product` isn't approved yet and lists no repository
- **THEN** it is shown as a collapsible row with its disclosure control and its review mark, like an approved one

### Requirement: Workspace repository labelled with its alias in the sidebar
Inside a multirepo's folder, the project of its workspace repository SHALL be labelled with its alias in the multirepo instead of its project name, so the multirepo's name never appears twice. Other repositories SHALL keep their project names. The label SHALL also decide its alphabetical place inside the folder, and reordering SHALL keep working on it.

#### Scenario: Workspace repository with the folder's name
- **WHEN** the multirepo `product` lives in `~/projects/product`, a Git repository registered as the project `product` with the alias `workspace`, and also holds `api`
- **THEN** the folder `product` lists `api` and `workspace`, and no entry inside it reads `product`

#### Scenario: Reorder the workspace repository
- **WHEN** the folder `product` lists `api` and `workspace` and the user drags `workspace` to the top
- **THEN** the folder lists `workspace` and `api`, and the order is kept after a restart
