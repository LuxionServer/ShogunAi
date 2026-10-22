# cli-project-registry Specification

## Purpose
Lets the user register, list and unregister projects from the command line, with the same defaults as the desktop app and without ever altering a project that already exists.

## Requirements

### Requirement: Register a project from the command line
`shogun project add [<path>]` SHALL register the Git repository containing `<path>` (the current directory by default, resolved to its main worktree) as a project, with the values the app's "Add project" form proposes for that repository: the repository folder's name as the project name, `<parent>/<name>-worktrees` as the worktrees folder, no local files, the default agent and the detected main branch left to detection. It SHALL print the project's name and repository path.

#### Scenario: Register the current repository
- **WHEN** the user runs `shogun project add` inside `~/projects/main-repo`, which is not registered
- **THEN** a project named `main-repo` with worktrees folder `~/projects/main-repo-worktrees` is added to the catalog and the command exits with code 0

#### Scenario: Register from a linked worktree
- **WHEN** the user runs `shogun project add` inside a linked worktree of `~/projects/main-repo`
- **THEN** the project registered is the one for `~/projects/main-repo`, not for the worktree

#### Scenario: Not a Git repository
- **WHEN** the user runs `shogun project add ~/projects/plain-folder` and the folder isn't inside a Git repository
- **THEN** nothing is registered, the command says no repository was found and exits with code 1

#### Scenario: The repository is left untouched
- **WHEN** a project is registered
- **THEN** no file inside the repository is created or modified

### Requirement: Registering is idempotent
`shogun project add` SHALL NOT duplicate a repository that is already registered, nor change that project's settings, and SHALL say that it was already registered. Repositories SHALL be compared by canonical path, so symbolic links and `/tmp` versus `/private/tmp` match.

#### Scenario: Already registered
- **WHEN** `~/projects/main-repo` is registered with a custom worktrees folder and main branch, and the user runs `shogun project add` inside it
- **THEN** the catalog is unchanged, the command prints that the project is already registered and exits with code 0

#### Scenario: Same repository through a different path
- **WHEN** the user runs `shogun project add` with a symbolic link that points to the registered repository
- **THEN** no second project is created

### Requirement: Existing catalog entries are preserved
Registering or removing a project SHALL keep every other entry of the projects file exactly as it was, and SHALL write entries in the same shape the desktop app writes, so a version of the app or CLI without this change keeps reading the file.

#### Scenario: Other projects survive
- **WHEN** the catalog holds three projects and the user registers a fourth
- **THEN** the first three keep their id, name and every setting

#### Scenario: Unreadable catalog
- **WHEN** the projects file can't be read
- **THEN** `project add` and `project remove` fail as an unexpected failure naming the file, as the other commands do, and replace nothing before keeping a copy of it

### Requirement: List the registered projects
`shogun project list` SHALL print one line per registered project with its name and repository path, and SHALL work outside a Git repository. With `--json` it SHALL print one JSON document with the same information.

#### Scenario: Several projects
- **WHEN** two projects are registered and the user runs `shogun project list`
- **THEN** each project's name and repository path is printed on its own line

#### Scenario: No projects
- **WHEN** no project is registered
- **THEN** the command says there are no projects and exits with code 0, and `--json` prints an empty list

### Requirement: Unregister a project from the command line
`shogun project remove [<path>]` SHALL remove the project of the repository containing `<path>` (the current directory by default) from the catalog, and SHALL NOT touch the repository, its worktrees or its files. A path that is no longer a repository SHALL still match a project registered with that path.

#### Scenario: Remove a project
- **WHEN** `~/projects/main-repo` is registered and the user runs `shogun project remove` inside it
- **THEN** the project is gone from the catalog and the repository and its worktrees stay on disk

#### Scenario: Remove a project whose folder is gone
- **WHEN** the user runs `shogun project remove ~/projects/deleted-repo` and a project was registered with that path
- **THEN** the project is removed even though the folder no longer exists

#### Scenario: Not registered
- **WHEN** the repository has no project
- **THEN** the command says so, changes nothing and exits with code 1

### Requirement: Machine-readable result of project commands
`project add` and `project remove` SHALL accept `--json` and print one document stating the action taken (`added`, `alreadyRegistered` or `removed`) and the project's id, name and repository path.

#### Scenario: JSON for an already registered project
- **WHEN** the user runs `shogun project add --json` in a registered repository
- **THEN** the document's action is `alreadyRegistered` and it carries the existing project's id
