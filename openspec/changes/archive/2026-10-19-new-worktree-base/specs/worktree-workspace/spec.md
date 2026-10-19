## ADDED Requirements

### Requirement: Choose where a new branch starts
The "New branch" mode of the "New worktree" dialog SHALL let the user choose the commit the new branch starts at: the project's main branch, selected by default, the base repository's current branch, any local branch or any `origin/` branch. When no main branch can be resolved, the base repository's current branch SHALL be the default. The worktree SHALL be created with its branch starting at the chosen branch's commit at creation time.

#### Scenario: Default start point
- **WHEN** the project's main branch is `develop`, `origin/develop` exists and the base repository has `feature/old` checked out
- **THEN** the dialog preselects `origin/develop`, and confirming creates the new branch at the commit `origin/develop` points to

#### Scenario: Start from the repository's current branch
- **WHEN** the user chooses the base repository's current branch as the start point
- **THEN** the new branch starts at the base repository's `HEAD`, as before this change

#### Scenario: No main branch
- **WHEN** no main branch can be resolved for the project
- **THEN** the dialog preselects the base repository's current branch

#### Scenario: Start point disappeared
- **WHEN** the chosen branch was deleted after the dialog listed it
- **THEN** the dialog shows that the start point doesn't name a commit, and no worktree or branch is created

### Requirement: Fetch before creating a worktree
When the base repository has an `origin` remote, the "New worktree" dialog SHALL offer "Fetch from origin first", whose last value is remembered per project on this machine and is off by default. When it is on, the app SHALL fetch from `origin` before creating, in either mode, and SHALL use the updated branches. A fetch SHALL never wait for credentials.

#### Scenario: Fetch moves the start point
- **WHEN** the option is on, the start point is `origin/develop` and the remote has a newer commit on `develop`
- **THEN** the app fetches first and the new branch starts at the newer commit

#### Scenario: Fetch fails
- **WHEN** the option is on and the fetch fails, for example without network or without credentials
- **THEN** the dialog shows Git's error with "Retry" and "Create without fetching", and nothing is created until the user chooses one

#### Scenario: Option remembered
- **WHEN** the user turns the option on, creates a worktree, and later opens the dialog for the same project
- **THEN** the option is on

#### Scenario: Repository without origin
- **WHEN** the base repository has no `origin` remote
- **THEN** the dialog doesn't show the option

### Requirement: Create a worktree from a remote branch
The "Existing branch" mode SHALL also list each `origin/` branch that has no local branch with the same name, marked as remote. Choosing one SHALL create a local branch with that name tracking the remote branch, and the worktree on it, in the folder the local branch would get. If the creation fails before post-creation commands run, the local branch it created SHALL be deleted.

#### Scenario: Remote-only branch
- **WHEN** `origin/feature/TASK-9` exists and there is no local `feature/TASK-9`
- **THEN** it is listed as remote, and choosing it creates a local `feature/TASK-9` tracking `origin/feature/TASK-9` in the worktree folder `TASK-9`

#### Scenario: Branch that exists locally and remotely
- **WHEN** both `feature/TASK-9` and `origin/feature/TASK-9` exist
- **THEN** only the local branch is listed

#### Scenario: Creation from a remote branch fails
- **WHEN** copying a local file fails after the worktree was added from `origin/feature/TASK-9`
- **THEN** the worktree is removed and the local `feature/TASK-9` the creation made is deleted
