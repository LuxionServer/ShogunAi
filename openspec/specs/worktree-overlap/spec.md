# worktree-overlap Specification

## Purpose
Tells which files several active worktrees of one repository change at once, each against its own base, so parallel changes find out before their pull requests collide.

## Requirements

### Requirement: Files a worktree changes
The system SHALL consider changed by a worktree every file that differs between the working tree and the commit where its branch left its base, which covers the branch's commits, staged and unstaged changes and deletions, plus every untracked file Git doesn't ignore. Both paths of a rename SHALL count. The base SHALL be the worktree's base as `shogun status` resolves it: its recorded base, or the main branch when none is recorded, the recorded one no longer exists or the worktree is detached.

#### Scenario: Commits and uncommitted changes
- **WHEN** `feature/TASK-123` has a commit that changes `src/App.kt`, an unstaged edit to `README.md` and a new untracked file `src/New.kt`
- **THEN** its changed files are `README.md`, `src/App.kt` and `src/New.kt`

#### Scenario: Changes of the base are not the branch's
- **WHEN** `develop` changed `build.gradle.kts` after `feature/TASK-123` was created from it and the branch didn't touch that file
- **THEN** `build.gradle.kts` is not among the branch's changed files

#### Scenario: Ignored files
- **WHEN** a worktree has a `build/` folder ignored by Git
- **THEN** none of its files counts as changed

### Requirement: Files changed by more than one worktree
`shogun overlap` SHALL list every file changed by two or more non-prunable worktrees of the repository containing the current directory, each file with the worktrees that change it: their path, branch (or none when detached) and the base each one was compared with. Files SHALL be sorted by path. Worktrees with different bases SHALL be reported together.

#### Scenario: Two worktrees on the same file
- **WHEN** the worktrees of `feature/TASK-1` and `feature/TASK-2`, both with base `develop`, change `core/Engine.kt`, and only `feature/TASK-1` changes `README.md`
- **THEN** the output lists `core/Engine.kt` with both worktrees and their base `develop`, and doesn't list `README.md`

#### Scenario: Different bases
- **WHEN** `feature/TASK-1` with base `develop` and `feature/TASK-3` with base `epic/redesign` both change `core/Engine.kt`
- **THEN** `core/Engine.kt` is listed with both worktrees, one shown with base `develop` and the other with base `epic/redesign`

#### Scenario: No overlap
- **WHEN** no file is changed by more than one worktree
- **THEN** the command prints `No file is changed by more than one worktree.` and exits with code 0

#### Scenario: Overlaps are not a failure
- **WHEN** some files are changed by more than one worktree
- **THEN** the command exits with code 0

### Requirement: Overlaps of one worktree
`shogun overlap <target>` SHALL list only the overlapping files that the worktree of `<target>` (task id, path or branch, resolved as `shogun base` does) changes. Without a target, inside a linked worktree, the target SHALL be that worktree; in the main checkout, every overlap of the repository SHALL be listed.

#### Scenario: Agent before its pull request
- **WHEN** an agent runs `shogun overlap` inside the worktree of `feature/TASK-1`, which shares `core/Engine.kt` with `feature/TASK-2`, while `feature/TASK-2` and `feature/TASK-3` also share `docs/guide.md`
- **THEN** only `core/Engine.kt` is listed

#### Scenario: Scoped with nothing shared
- **WHEN** the user runs `shogun overlap TASK-1` and no other worktree changes the files of `feature/TASK-1`
- **THEN** the command prints `No file of feature/TASK-1 is changed by another worktree.` and exits with code 0

#### Scenario: Main checkout
- **WHEN** the user runs `shogun overlap` in the main checkout
- **THEN** every overlapping file of the repository is listed

#### Scenario: Unknown target
- **WHEN** the user runs `shogun overlap TASK-999` and no worktree matches it
- **THEN** the error is printed on standard error and the command exits with code 1

### Requirement: Worktrees that can't be compared
A worktree SHALL be skipped, without failing the command, when it is prunable, when no base can be resolved for it, or when reading its changes fails. Each skipped worktree SHALL be reported on standard error with its reason, and in the JSON output.

#### Scenario: No base
- **WHEN** the repository has no recorded base for a worktree and no main branch can be resolved
- **THEN** that worktree is reported as skipped because it has no base, and the others are still compared

#### Scenario: Git read fails
- **WHEN** `git merge-base` fails for a worktree whose branch has no history in common with its base
- **THEN** that worktree is reported as skipped with the Git error, and the others are still compared

### Requirement: Files whose overlaps the repository expects
`shogun overlap` SHALL leave out the files matched by the patterns of `overlap.ignore` in the repository's `.shogunai.json`, and `--all` SHALL include them. Nothing SHALL be left out when the field isn't declared.

#### Scenario: Roadmap declared
- **WHEN** `.shogunai.json` declares `"overlap": { "ignore": ["docs/roadmap*.md"] }` and two worktrees change `docs/roadmap.md` and `docs/roadmap.es.md`
- **THEN** neither file is listed

#### Scenario: Show them anyway
- **WHEN** the same repository is checked with `shogun overlap --all`
- **THEN** `docs/roadmap.md` and `docs/roadmap.es.md` are listed

#### Scenario: Invalid declaration
- **WHEN** `.shogunai.json` has `overlap.ignore` set to something other than a list of strings
- **THEN** the command fails with an error that names `.shogunai.json` and exits with code 1

### Requirement: Overlaps as JSON
`shogun overlap --json` SHALL print `{"repository", "worktree", "files", "skipped"}`: `worktree` is the target worktree's path or `null` when every overlap is listed; each entry of `files` is `{"path", "worktrees": [{"path", "branch", "base"}]}`, with `branch` `null` for a detached worktree; each entry of `skipped` is `{"path", "branch", "reason"}`.

#### Scenario: Overlap in JSON
- **WHEN** `feature/TASK-1` and `feature/TASK-2`, both with base `develop`, change `core/Engine.kt`, and the user runs `shogun overlap --json` in the main checkout
- **THEN** `worktree` is `null` and `files` has one entry with `path` `core/Engine.kt` and two worktrees, each with `base` `develop`

#### Scenario: Nothing to report in JSON
- **WHEN** no file is changed by more than one worktree
- **THEN** `files` and `skipped` are empty lists and the exit code is 0
