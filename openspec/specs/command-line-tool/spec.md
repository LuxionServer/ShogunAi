# command-line-tool Specification

## Purpose
Defines the `shogun` command, which runs ShogunAi's worktree operations from a terminal, a script or an agent hook without opening the desktop app.

## Requirements

### Requirement: Find the repository and its project
Every command SHALL work on the Git repository that contains the current directory, or the path it's given, resolved to its main worktree even when run from a linked worktree. When the desktop app has a project for that repository, the command SHALL use its worktrees folder, local files and main branch.

#### Scenario: Run from a linked worktree
- **WHEN** the user runs `shogun status` inside a linked worktree of `~/projects/main-repo`
- **THEN** the command works on `~/projects/main-repo` and lists all its worktrees

#### Scenario: Repository with a project
- **WHEN** the repository has a project in the desktop app with worktrees folder `~/projects/worktrees` and main branch `develop`
- **THEN** `shogun new` creates worktrees under `~/projects/worktrees` and `shogun status` compares against `develop`

#### Scenario: Outside a Git repository
- **WHEN** the user runs a command outside any Git repository
- **THEN** it prints an error saying no repository was found and exits with code 1

### Requirement: Create a worktree from the command line
`shogun new <task-id> [--fix]` SHALL create a worktree on a new `feature/` (or `fix/`) branch, and `shogun new --branch <branch>` one from an existing local branch, with the same folder, provisioning, validation and rollback as the desktop app. On success it SHALL print the worktree's absolute path alone on standard output.

#### Scenario: New branch
- **WHEN** the user runs `shogun new TASK-123` in a repository with a project
- **THEN** a worktree on `feature/TASK-123` is created in the project's worktrees folder, provisioned, and its path is the only line on standard output

#### Scenario: Existing branch
- **WHEN** the user runs `shogun new --branch hotfix/login`
- **THEN** a worktree for the existing branch is created and provisioned, and its path is printed

#### Scenario: No project and no worktrees folder
- **WHEN** the repository has no project in the desktop app and the user runs `shogun new TASK-123` without `--worktrees-root`
- **THEN** the command explains that a worktrees folder is needed, creates nothing and exits with code 2

#### Scenario: Creation fails
- **WHEN** creation fails, e.g. because the destination folder already exists
- **THEN** the error is printed on standard error and the command exits with code 1

### Requirement: Provision an existing worktree from the command line
`shogun provision [<path>]` SHALL apply the project's local files and the repository's provisioning to the worktree at `<path>`, or the current directory when omitted, and print each copied file's relative path.

#### Scenario: Worktree created by another tool
- **WHEN** a worktree was created with `git worktree add` and the user runs `shogun provision <its path>`
- **THEN** the files the repository and the project declare are copied into it and listed

#### Scenario: Path is not a worktree of the repository
- **WHEN** `<path>` is the main worktree or not one of the repository's worktrees
- **THEN** nothing is copied and the command exits with code 1 explaining why

### Requirement: Show worktree status from the command line
`shogun status` SHALL print one line per worktree of the repository with its path, branch (or detached), origin (ShogunAi, Claude Code or other), uncommitted changes, divergence from its upstream and whether it's merged into the main branch, the same information the desktop app shows.

#### Scenario: Several worktrees
- **WHEN** the repository has the main worktree, a merged worktree and one with uncommitted changes
- **THEN** each one has a line, the merged one says it's merged and the other shows its number of changes

#### Scenario: Missing folder
- **WHEN** a worktree's folder no longer exists
- **THEN** its line says the folder is missing

### Requirement: Clean up worktrees from the command line
`shogun clean` SHALL select the same worktrees as the desktop app's cleanup (clean and merged, or with a missing folder; never the main or a locked one) and only list them. With `--yes` it SHALL remove them, and with `--delete-branches` also delete their local branches when merged, reporting what was removed, kept and failed.

#### Scenario: Without confirmation
- **WHEN** the user runs `shogun clean` and two worktrees qualify
- **THEN** both are listed, nothing is removed, and the output says to add `--yes`

#### Scenario: With confirmation
- **WHEN** the user runs `shogun clean --yes --delete-branches`
- **THEN** the listed worktrees are removed, their merged branches deleted, and the result is reported

#### Scenario: Nothing to clean
- **WHEN** no worktree qualifies
- **THEN** the command says so and exits with code 0

#### Scenario: A removal fails
- **WHEN** removing one of the worktrees fails
- **THEN** the others are still removed, the failure is reported on standard error and the command exits with code 1

### Requirement: Usage, version and exit codes
`shogun help` (or no arguments in the standalone distribution) SHALL print the commands and options; `shogun --version` the version. An unknown command or option, or a missing argument, SHALL print a short usage error on standard error and exit with code 2.

#### Scenario: Version
- **WHEN** the user runs `shogun --version`
- **THEN** it prints the same version as the desktop app it ships with

#### Scenario: Unknown command
- **WHEN** the user runs `shogun frobnicate`
- **THEN** it prints that the command is unknown, points to `shogun help` and exits with code 2

### Requirement: Install the command on each platform
The `shogun` command SHALL be available from each release: on macOS and Linux through the desktop app's launcher, which runs the command instead of the window when it receives arguments, and on any platform with Java 21 or later through a `shogun-<version>.zip` attached to the release.

#### Scenario: Desktop app launched with arguments
- **WHEN** the desktop app's executable is run with `status` as argument
- **THEN** it prints the status and exits without opening a window

#### Scenario: Desktop app launched without arguments
- **WHEN** the desktop app is opened normally
- **THEN** the window opens as before

#### Scenario: Release assets
- **WHEN** a release is published
- **THEN** it includes `shogun-<version>.zip` with launch scripts for Unix and Windows
