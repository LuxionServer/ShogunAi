## ADDED Requirements

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
