## MODIFIED Requirements

### Requirement: Choose where a new branch starts
The "New branch" mode of the "New worktree" dialog SHALL let the user choose the commit the new branch starts at: the base repository's current branch, selected by default as `shogun new` does and marked as the same as `shogun new`, the project's main branch, any local branch or any `origin/` branch. The worktree SHALL be created with its branch starting at the chosen branch's commit at creation time.

#### Scenario: Default start point
- **WHEN** the project's main branch is `main`, `origin/main` exists and the base repository has `develop` checked out
- **THEN** the dialog preselects the base repository's current branch, labelled with `develop` and "same as shogun new", and confirming creates the new branch at the base repository's `HEAD` with `develop` recorded as its base

#### Scenario: Start from the repository's current branch
- **WHEN** the user keeps or chooses the base repository's current branch as the start point
- **THEN** the new branch starts at the base repository's `HEAD`, as `shogun new` does from the main checkout

#### Scenario: Main branch stays available
- **WHEN** the project's main branch resolves to `origin/main`
- **THEN** the second option of "Start from" is `origin/main`, marked as the main branch, and choosing it creates the new branch at the commit `origin/main` points to

#### Scenario: No main branch
- **WHEN** no main branch can be resolved for the project
- **THEN** the dialog preselects the base repository's current branch and lists the local and `origin/` branches after it

#### Scenario: Start point disappeared
- **WHEN** the chosen branch was deleted after the dialog listed it
- **THEN** the dialog shows that the start point doesn't name a commit, and no worktree or branch is created
