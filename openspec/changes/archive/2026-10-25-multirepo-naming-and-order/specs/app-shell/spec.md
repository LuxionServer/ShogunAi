## ADDED Requirements

### Requirement: Reorder the sidebar by dragging
The user SHALL be able to drag a top-level entry of the sidebar (a project or a multirepo folder) to another place among the top-level entries, and a project inside a multirepo folder to another place inside that folder, in the expanded and in the compact sidebar. An entry SHALL NOT move between levels, and a short press without movement SHALL still select it.

#### Scenario: Drag a project up
- **WHEN** the sidebar lists `api`, `main-repo` and `mobile` and the user drags `mobile` above `api`
- **THEN** the sidebar lists `mobile`, `api` and `main-repo`

#### Scenario: Inside a multirepo folder
- **WHEN** the folder `product` holds `api`, `app` and `docs` and the user drags `docs` to the top of the folder
- **THEN** the folder holds `docs`, `api` and `app`, and the top-level order is unchanged

#### Scenario: Dropped outside its level
- **WHEN** the user drags `tools`, a top-level project, onto the projects inside `product`
- **THEN** `tools` stays at the top level, placed at the nearest position of its level, and doesn't join the multirepo

#### Scenario: Click without dragging
- **WHEN** the user presses and releases `api` without moving the pointer
- **THEN** `api` is selected and the order doesn't change

### Requirement: Reorder the sidebar from the context menu
The context menu of every reorderable sidebar entry SHALL offer "Move up" and "Move down", which move it one place within its level and are unavailable at the first and last place, and "Sort alphabetically", which drops the manual order of that level.

#### Scenario: Move down
- **WHEN** the sidebar lists `api`, `main-repo` and `mobile` and the user chooses "Move down" on `api`
- **THEN** the sidebar lists `main-repo`, `api` and `mobile`

#### Scenario: First entry
- **WHEN** the user opens the context menu of the first entry of its level
- **THEN** "Move up" isn't offered and "Move down" is

#### Scenario: Back to alphabetical order
- **WHEN** the top level was reordered and the user chooses "Sort alphabetically" on one of its entries
- **THEN** the top-level entries are listed alphabetically again, and the order inside each multirepo folder is kept

### Requirement: The sidebar order persists
The manual order of each level SHALL be saved in the app's settings and restored on the next launch. Entries without a saved place SHALL be listed after the ordered ones, alphabetically among themselves, and saved places of entries that no longer exist SHALL be ignored.

#### Scenario: Restart after reordering
- **WHEN** the user moves `mobile` to the top and restarts the app
- **THEN** `mobile` is still the first top-level entry

#### Scenario: New project after reordering
- **WHEN** the top level was reordered as `mobile`, `api`, `main-repo` and the user adds the project `billing`
- **THEN** the sidebar lists `mobile`, `api`, `main-repo` and `billing`

#### Scenario: Removed project
- **WHEN** the user removes `api` after reordering and later adds a project named `api` again
- **THEN** the new `api` is listed at the end of its level

## MODIFIED Requirements

### Requirement: Sidebar lists the projects
The sidebar SHALL list every saved project by name, alphabetically unless the user has reordered them, with an "Add project" action. Projects that belong to a multirepo SHALL be listed inside that multirepo's folder instead of at the top level. The selected entry SHALL be visually marked.

#### Scenario: Projects listed
- **WHEN** the app has the projects `api`, `mobile` and `main-repo` saved and the user hasn't reordered them
- **THEN** the sidebar lists `api`, `main-repo` and `mobile`, in that order, followed by "Add project"

#### Scenario: Projects of a multirepo
- **WHEN** `api` and `mobile` belong to the multirepo `product`
- **THEN** the sidebar lists `main-repo` and the folder `product` with `api` and `mobile` inside
