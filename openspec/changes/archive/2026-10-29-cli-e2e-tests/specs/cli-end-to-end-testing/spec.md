# Spec Delta

## Purpose

End-to-end tests of the `shogun` command that run the installed command against real Git repositories in temporary folders, without fakes, so regressions that only show up with real Git, real files and the real launcher are caught before a release.

## ADDED Requirements

### Requirement: End-to-end tests run the installed command against real repositories
The end-to-end tests SHALL run the `shogun` command from the `:cli` distribution as a separate process, through the launcher script a user runs, against Git repositories created with the real `git` in a temporary folder, without replacing Git, the file system or the project catalog with fakes.

#### Scenario: A worktree is created by the real command
- **WHEN** a test runs `shogun new TASK-1` inside a temporary repository
- **THEN** the command's process exits with 0, prints the worktree's path, and `git worktree list` in that repository lists it on `feature/TASK-1`

### Requirement: End-to-end tests isolate the machine
Each end-to-end test SHALL give the command a home folder, a `~/.shogunai` folder and a Git global configuration inside its temporary folder, and SHALL NOT read or write the real user's home.

#### Scenario: The machine files land in the temporary home
- **WHEN** a test registers a project with `shogun project add`
- **THEN** the project catalog is written under the test's temporary home and the real user's `~/.shogunai` is untouched

### Requirement: End-to-end tests cover worktree creation and provisioning
The end-to-end tests SHALL cover creating worktrees on a new feature or fix branch and for an existing branch, and provisioning them with local files copied, linked and matched by patterns from `.shogunai.json` and `.worktreeinclude`.

#### Scenario: Declared local files reach a new worktree
- **WHEN** the base repository declares an anchored file and a pattern to copy, a folder to link and a `.worktreeinclude` pattern, with matching untracked files, and a test runs `shogun new`
- **THEN** the new worktree has copies of the copied and included files with their content, and the linked files resolve to the base repository's files

#### Scenario: Provisioning restores a missing file
- **WHEN** a declared file is deleted from a worktree and a test runs `shogun provision --check` and then `shogun provision`
- **THEN** the check names the file and exits with 3, and after provisioning the file is back and the check exits with 0

### Requirement: End-to-end tests cover opening worktrees in the runners
The end-to-end tests SHALL cover opening a worktree in Herdr and in Orca with fake executables placed where the command looks for them, and SHALL check the commands those executables receive.

#### Scenario: Opening in Herdr sends the worktree and its agent
- **WHEN** a test runs `shogun open TASK-1 --herdr` with a fake `herdr`
- **THEN** the fake receives a command to open the worktree's path and a command to run the project's agent in a pane, and `shogun` exits with 0

#### Scenario: Opening in Orca creates the agent's terminal
- **WHEN** a test runs `shogun open TASK-1 --orca` with a fake `orca`
- **THEN** the fake receives a command to create a terminal in the worktree running the project's agent, and `shogun` exits with 0

### Requirement: End-to-end tests cover cleaning up worktrees
The end-to-end tests SHALL cover removing a worktree and cleaning up worktrees whose branch is merged.

#### Scenario: A merged worktree is cleaned up
- **WHEN** a worktree's branch is merged into the main branch and a test runs `shogun clean` and then `shogun clean --yes --delete-branches`
- **THEN** the listing names the worktree without removing it, and after `--yes` its folder and its branch are gone

### Requirement: End-to-end tests cover multirepo groups
The end-to-end tests SHALL cover creating a multirepo of several temporary repositories, creating a group's worktrees, listing the group, opening it in Herdr and cleaning it up once its branches are merged.

#### Scenario: A group spans every default repository
- **WHEN** a test makes a multirepo of two repositories and runs `shogun new TASK-2` in its folder
- **THEN** each repository has a worktree on `feature/TASK-2` in its project's worktrees folder, and `shogun status` lists them as one group

### Requirement: End-to-end tests run in the test workflow on every operating system
The end-to-end tests SHALL run as part of `./gradlew test`, so the Tests workflow runs them on Linux, macOS and Windows; a test that cannot run on an operating system SHALL be skipped there with the reason in its code, not fail.

#### Scenario: The regular test task runs the end-to-end tests
- **WHEN** `./gradlew test` runs
- **THEN** the `:cli` distribution is installed and the end-to-end tests run against it
