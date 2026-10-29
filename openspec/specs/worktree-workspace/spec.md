# worktree-workspace Specification

## Purpose
Covers managing the worktrees of the active project: listing, creating from new or existing branches, removing, reloading and repairing them after the repository moves.

## Requirements

### Requirement: List worktrees for the active project
The system SHALL show the active project's worktrees on the worktree management view using `ListWorktreesUseCase`, one row per worktree. Each row SHALL show the branch as its main label (or that the worktree is detached), and the worktree's folder name as secondary information. The main worktree SHALL be listed first and marked distinctly.

#### Scenario: Worktrees loaded successfully
- **WHEN** the worktree management view opens for a project
- **THEN** it invokes `ListWorktreesUseCase` and displays the returned worktrees, with the main worktree first and marked distinctly

#### Scenario: Listing fails
- **WHEN** `ListWorktreesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the view shows an error message derived from the `WorktreeError` instead of a worktree list

#### Scenario: Branch as the main label
- **WHEN** a worktree at `~/projects/main-repo-worktrees/TASK-123` has the branch `feature/TASK-123`
- **THEN** its row shows `feature/TASK-123` as the main label and `TASK-123` as secondary information, without the full path

### Requirement: Create a worktree for a task
The system SHALL let the user create a new worktree in the "New worktree" dialog by entering a task id and choosing a branch type (`feature` or `fix`), using `CreateWorktreeUseCase`. Before creating the worktree, the task id SHALL be normalized (trimmed, internal whitespace runs replaced with `-`) and validated as a Git-ref-safe identifier; if the normalized id is still invalid, the use case SHALL fail with `WorktreeError.InvalidTaskId` instead of attempting the Git command. While `CreateWorktreeUseCase` is running, the dialog SHALL show a loading indicator and disable its inputs, the new/existing-branch mode toggle and its "Create worktree" and "Cancel" actions until the call completes.

#### Scenario: Successful creation
- **WHEN** the user enters a non-empty task id in the dialog, selects a branch type, and confirms
- **THEN** the app invokes `CreateWorktreeUseCase(taskId, branchType)` and, on success, closes the dialog and adds the returned `Worktree` to the displayed list

#### Scenario: Task id with spaces is normalized instead of failing
- **WHEN** the user enters a task id containing spaces (e.g. `"TASK 123"`)
- **THEN** `CreateWorktreeUseCase` normalizes it to `"TASK-123"`, creates the worktree and branch using the normalized id, and the displayed `Worktree`/branch reflects the normalized id rather than failing with a Git error

#### Scenario: Task id is invalid even after normalization
- **WHEN** the user enters a task id that, after trimming and replacing spaces with `-`, still contains characters Git disallows in ref names, or that normalizes to an empty string
- **THEN** `CreateWorktreeUseCase` returns a failed `Result` with `WorktreeError.InvalidTaskId` and does not run any Git command

#### Scenario: Creation fails with a domain error
- **WHEN** `CreateWorktreeUseCase` returns a failed `Result` (e.g. `WorktreeError.WorktreeAlreadyExists`, `WorktreeError.LocalFilesUnavailable`, `WorktreeError.BaseRepositoryNotFound`, `WorktreeError.GitLfsNotFound`, `WorktreeError.InvalidTaskId`)
- **THEN** the dialog stays open with the values the user entered, shows an error message describing the failure inside the dialog, and no worktree is added to the list

#### Scenario: Creation fails because Git LFS is required but not installed
- **WHEN** `git worktree add` fails and its stderr indicates the repository's `post-checkout` hook could not find `git-lfs` on the user's `PATH`
- **THEN** `CreateWorktreeUseCase` removes the partially created worktree (`git worktree remove --force`) and the branch Git already created (`git branch -D`), both best-effort, and returns a failed `Result` with `WorktreeError.GitLfsNotFound` instead of `WorktreeError.GitCommandFailed`, so a subsequent retry with the same task id does not hit `WorktreeAlreadyExists` or a "branch already exists" error

#### Scenario: Task id field shows the normalized value while typing
- **WHEN** the user types a task id containing spaces into the dialog's task id field
- **THEN** the field displays the normalized value (spaces replaced with `-`) rather than the raw input with spaces

#### Scenario: Creation is in progress
- **WHEN** the user confirms worktree creation and `CreateWorktreeUseCase` has not yet returned
- **THEN** the dialog shows a loading indicator and its inputs, the mode toggle and its "Create worktree" and "Cancel" actions are disabled, preventing a duplicate submission until the call completes

### Requirement: Remove a worktree
The system SHALL let the user remove a non-main worktree, using `RemoveWorktreeUseCase`, with an option to also delete its local branch. The removal SHALL be started from the worktree's detail, its row's context menu or the delete shortcut. Starting it SHALL always show a confirmation dialog before any Git command runs, with a checkbox labeled to delete the local branch as well, unchecked by default; the branch is only deleted if the user checks it, as a separate step after the worktree is removed. When the removal fails because the worktree has uncommitted or untracked changes, the system SHALL update the same dialog to offer a force retry, reusing the user's branch-deletion choice, instead of leaving the user with no recourse. While `RemoveWorktreeUseCase` is running (initial attempt or force retry), the dialog SHALL show a loading indicator and disable both its confirm and cancel actions until the call completes.

#### Scenario: Confirmation dialog shown before removing
- **WHEN** the user activates "Delete worktree" for a non-main worktree from its detail, its context menu or the shortcut
- **THEN** the app shows a confirmation dialog with a "delete local branch" checkbox unchecked by default, and does not invoke `RemoveWorktreeUseCase` until the user confirms

#### Scenario: Successful removal without deleting the branch
- **WHEN** the user confirms the removal dialog with the "delete local branch" checkbox unchecked
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, force = false)` without a branch to delete and, on success, removes the worktree from the displayed list and dismisses the dialog

#### Scenario: Successful removal deleting the branch
- **WHEN** the user checks "delete local branch" in the confirmation dialog and confirms
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, branchToDelete = worktree.branch, force = false)` and, on success, removes the worktree from the displayed list and, unless the branch was kept, dismisses the dialog

#### Scenario: User cancels the confirmation dialog
- **WHEN** the user dismisses or cancels the confirmation dialog
- **THEN** no Git command is executed and the worktree remains in the displayed list

#### Scenario: Removal fails for a reason unrelated to uncommitted changes
- **WHEN** `RemoveWorktreeUseCase` returns a failed `Result` whose error does not indicate that `--force` would resolve it
- **THEN** the app shows an error message and keeps the worktree in the displayed list, without escalating the dialog to a force-retry state

#### Scenario: Removal fails because the worktree has uncommitted or untracked changes
- **WHEN** `RemoveWorktreeUseCase` returns a failed `Result` whose `WorktreeError.GitCommandFailed` indicates the worktree needs `--force` to be removed
- **THEN** the same confirmation dialog updates to ask whether to force-delete the worktree, preserving the "delete local branch" checkbox state the user had chosen, instead of only showing the raw error message

#### Scenario: User confirms force removal
- **WHEN** the user confirms the force-delete state of the dialog shown after a failed removal
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, branchToDelete, force = true)` using the branch-deletion choice already selected, and on success removes the worktree from the displayed list and dismisses the dialog

#### Scenario: User cancels force removal
- **WHEN** the user dismisses or cancels the dialog while it is in the force-retry state
- **THEN** the worktree remains in the displayed list and no further removal command is executed

#### Scenario: No branch checkbox for a detached worktree
- **WHEN** the worktree being removed has no associated branch (detached HEAD)
- **THEN** the confirmation dialog does not show the "delete local branch" checkbox

#### Scenario: Main worktree cannot be removed
- **WHEN** the user selects the main worktree or opens its row's context menu
- **THEN** neither the detail nor the context menu offers a delete action, and the delete shortcut does nothing

#### Scenario: Removal is in progress
- **WHEN** the user confirms removal (initial attempt or force retry) and `RemoveWorktreeUseCase` has not yet returned
- **THEN** the dialog shows a loading indicator and both its confirm and cancel actions are disabled until the call completes

### Requirement: Reload the worktree list
The system SHALL let the user manually trigger a fresh read of the active project's worktree list via a visible "Reload" action on the worktree management screen and via View > Reload, SHALL automatically re-run this read every time the user selects the project in the sidebar, and SHALL automatically re-run this read whenever the OS window regains focus while the worktree management screen is active, so that worktrees or branches deleted outside the app (e.g. via another Git client, or a branch deleted after merging a pull request) are reflected without restarting the app. Whenever the reload is triggered while the create-worktree form is in "Existing branch" mode, the system SHALL also reload the local branch list, so branches created or updated outside the app are reflected without restarting it.

#### Scenario: User manually reloads the worktree list
- **WHEN** the user activates the "Reload" action on the worktree management screen, or View > Reload while it is shown
- **THEN** the app invokes `ListWorktreesUseCase` again and updates the displayed list to match its result

#### Scenario: Manual reload also refreshes local branches in "Existing branch" mode
- **WHEN** the user activates the "Reload" action while the create-worktree form is in "Existing branch" mode
- **THEN** the app also invokes `ListLocalBranchesUseCase` again and updates the displayed branch options to match its result

#### Scenario: Manual reload does not fetch branches in "New branch" mode
- **WHEN** the user activates the "Reload" action while the create-worktree form is in "New branch" mode
- **THEN** the app does not invoke `ListLocalBranchesUseCase`

#### Scenario: Worktree screen re-entered after an external change
- **WHEN** the user selects another view in the sidebar and then selects the project again
- **THEN** the app invokes `ListWorktreesUseCase` again, so a worktree or branch removed externally while the user was away no longer appears in the displayed list

#### Scenario: Application window regains focus while the worktree screen is active
- **WHEN** the worktree management screen is the active view and the application window transitions from unfocused to focused (e.g. the user switches back from another app)
- **THEN** the app invokes `ListWorktreesUseCase` again, so a worktree or branch removed externally while the app was unfocused no longer appears in the displayed list

#### Scenario: Reload is in progress
- **WHEN** a reload (manual or triggered by selecting the project) is in progress
- **THEN** the screen shows a loading indicator and disables the manual "Reload" action until the reload completes

#### Scenario: Reload fails
- **WHEN** `ListWorktreesUseCase` returns a failed `Result` during a reload
- **THEN** the screen shows an error message derived from the failure, consistent with the initial load's error handling

### Requirement: List local branches eligible for a new worktree
The system SHALL let the user see all local branches, using `ListLocalBranchesUseCase`, which returns every local branch paired with whether it is already checked out in a worktree (including the base repository itself). In the dropdown, branches SHALL be visually grouped by domain — the segment of the branch name before the first `/`, or the localized "other" group ("otras" in Spanish) for branches without a `/` — with a clickable header shown for each distinct domain, in order of first appearance. Clicking a header SHALL collapse or expand the branches under that domain. The group containing the currently selected branch SHALL start expanded; other groups start collapsed. Once the user expands or collapses a group, that state SHALL persist across subsequent openings of the dropdown within the same screen instance, instead of resetting to collapsed every time.

#### Scenario: Local branches loaded successfully
- **WHEN** the user switches the create-worktree dialog to "Existing branch" mode
- **THEN** the app invokes `ListLocalBranchesUseCase` and displays every returned branch, each flagged with whether it is already checked out

#### Scenario: A branch already checked out is shown but disabled
- **WHEN** a local branch is currently checked out in the base repository or in another worktree
- **THEN** the branch still appears in the dropdown, rendered disabled with a note that it already has a worktree, instead of being omitted

#### Scenario: Listing local branches fails
- **WHEN** `ListLocalBranchesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows an error message derived from the `WorktreeError` instead of a branch list

#### Scenario: Branches are grouped by domain in the dropdown
- **WHEN** the "Existing branch" dropdown displays local branches such as `feature/TASK-1`, `feature/TASK-2`, `fix/TASK-3`, and `develop`
- **THEN** the dropdown shows a "feature" header followed by `feature/TASK-1` and `feature/TASK-2`, then a "fix" header followed by `fix/TASK-3`, then an "other" header followed by `develop`, instead of a single flat alphabetical list

#### Scenario: The group containing the selected branch starts expanded
- **WHEN** the "Existing branch" dropdown is opened and a branch is already selected
- **THEN** the domain group containing the selected branch is shown expanded, while other groups start collapsed

#### Scenario: A domain header can be collapsed and expanded
- **WHEN** the "Existing branch" dropdown is opened
- **THEN** the user can click a header (e.g. "feature") to expand it
- **WHEN** the user clicks an expanded header again
- **THEN** the branches under it are hidden again, without closing the dropdown

#### Scenario: Group expansion state is remembered across dropdown openings
- **WHEN** the user expands or collapses a domain group, closes the dropdown, and reopens it within the same worktree screen visit
- **THEN** each group's expanded/collapsed state matches what the user last set, instead of resetting to collapsed

### Requirement: Create a worktree from an existing branch
The system SHALL let the user create a new worktree for a branch that already exists locally, from the "Existing branch" mode of the "New worktree" dialog, using `CreateWorktreeFromBranchUseCase`, instead of only being able to create worktrees for brand-new branches. The worktree's directory is derived from the branch name: if the branch starts with a known `BranchType` prefix (`feature/` or `fix/`), that prefix is stripped first; the remaining string then has every `/` replaced with `-`. While `CreateWorktreeFromBranchUseCase` is running, the dialog SHALL show a loading indicator and disable its inputs, the new/existing-branch mode toggle and its actions until the call completes.

#### Scenario: Successful creation
- **WHEN** the user selects "Existing branch" mode in the dialog, picks a branch not already checked out from the list, and confirms
- **THEN** the app invokes `CreateWorktreeFromBranchUseCase(branch)`, which runs `git worktree add <path> <branch>` (without creating a new branch), copies the project's secret files into the new worktree, and on success closes the dialog and adds the returned `Worktree` to the displayed list

#### Scenario: Directory matches the new-branch flow for a known branch type prefix
- **WHEN** the requested branch starts with a known `BranchType` prefix, e.g. `feature/TASK-123`
- **THEN** the worktree directory is `<worktreesRoot>/TASK-123` (the prefix stripped), the same directory `CreateWorktreeUseCase` would have produced for that task id and branch type, instead of `<worktreesRoot>/feature-TASK-123`

#### Scenario: Creation fails because the branch doesn't exist
- **WHEN** the requested branch does not resolve to an existing local ref
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchNotFound` and no worktree is created

#### Scenario: Creation fails because the branch is already checked out elsewhere
- **WHEN** `git worktree add` fails because the requested branch is already checked out in another worktree or in the base repository
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchAlreadyCheckedOut` instead of `WorktreeError.GitCommandFailed`, and the dialog stays open showing an error message describing the conflict

#### Scenario: Creation fails because the destination directory already exists
- **WHEN** the derived branch name maps to a worktree directory that already exists
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.WorktreeAlreadyExists` without touching Git

#### Scenario: Creation fails because Git LFS is required but not installed
- **WHEN** `git worktree add` fails and its stderr indicates the repository's `post-checkout` hook could not find `git-lfs` on the user's `PATH`
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.GitLfsNotFound` instead of `WorktreeError.GitCommandFailed`

#### Scenario: Creation fails while copying secrets
- **WHEN** copying a secret file into the new worktree fails after `git worktree add` succeeded
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.SecretCopyFailed`

#### Scenario: Creation from an existing branch is in progress
- **WHEN** the user confirms creation from an existing branch and `CreateWorktreeFromBranchUseCase` has not yet returned
- **THEN** the dialog shows a loading indicator and its inputs, the mode toggle and its actions are disabled, preventing a duplicate submission until the call completes

### Requirement: Repair worktrees after moving the repository
The system SHALL re-link the base repository and its worktrees with `git worktree repair`, via `RepairWorktreesUseCase`, after the user saves an existing project with a different base repository path or worktrees root. The worktree management screen SHALL always use the project's current configuration.

#### Scenario: Repair after moving the repository
- **WHEN** the user saves an existing project whose base repository path or worktrees root changed
- **THEN** the worktree management screen runs `git worktree repair` from the new base repository before listing worktrees

#### Scenario: Worktrees moved along with the worktrees root
- **WHEN** a worktree reported by `git worktree list` no longer exists at its old path but exists at the same relative location under the new worktrees root
- **THEN** its new path is passed to `git worktree repair` so Git updates the link in both directions

#### Scenario: Repair fails
- **WHEN** `RepairWorktreesUseCase` returns a failed `Result` (e.g. `WorktreeError.BaseRepositoryNotFound` or `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows the error message and still lists the worktrees

#### Scenario: Screen uses the edited configuration
- **WHEN** the user edits a project's configuration and saves
- **THEN** the worktree management screen uses use cases built from the saved configuration, not a cached view model from before the edit

### Requirement: Show a loading indicator while loading local branches
The system SHALL show the same visual loading indicator used elsewhere on the worktree screen while `ListLocalBranchesUseCase` is in progress for the "Existing branch" create-worktree mode, instead of a plain text message.

#### Scenario: Branches are loading
- **WHEN** the user switches the create-worktree form to "Existing branch" mode and `ListLocalBranchesUseCase` has not yet returned
- **THEN** the screen shows the same loading indicator style used for reloading the worktree list, instead of only a "Loading branches..." text

### Requirement: Dismiss an error message
The system SHALL let the user manually dismiss an error message shown on the worktree management screen, in addition to it being replaced automatically by a subsequent successful action.

#### Scenario: User dismisses an error
- **WHEN** an error message is visible on the worktree management screen and the user activates its dismiss control
- **THEN** the error message is hidden and no further action is taken

### Requirement: Show feedback when a worktree is created successfully
The system SHALL show a visible, transient confirmation when a worktree is created successfully, in addition to adding it to the displayed list. The confirmation SHALL offer an action to copy the new worktree's launch command.

#### Scenario: Worktree created successfully
- **WHEN** a worktree is created successfully
- **THEN** the app shows a transient success message confirming the creation, which disappears automatically after a short delay

#### Scenario: Copy the launch command from the confirmation
- **WHEN** the user activates "Copy launch command" on the creation confirmation
- **THEN** the new worktree's launch command is copied to the clipboard, as with the worktree's own copy action

### Requirement: Show a format hint for the task id field
The system SHALL show a hint or example of the expected task id format next to the task id field, in addition to the existing invalid-format error message.

#### Scenario: Task id field is empty or unfocused
- **WHEN** the user has not yet entered an invalid task id
- **THEN** the field shows a hint with an example of a valid task id format

### Requirement: Open the new worktree dialog
The project view SHALL offer a "New worktree" action in its header that opens the "New worktree" dialog with the first field focused. Escape or "Cancel" SHALL close the dialog without creating anything, and reopening it SHALL start from empty fields.

#### Scenario: Open and cancel
- **WHEN** the user activates "New worktree", types a task id and presses Escape
- **THEN** the dialog closes, nothing is created, and opening it again shows an empty task id

#### Scenario: Enter creates
- **WHEN** the dialog has a valid task id and the user presses Enter
- **THEN** the worktree is created, same as activating "Create worktree"

### Requirement: Open the project's settings from its view
The worktree management view SHALL offer a "Project settings" action in its header that opens the project's configuration.

#### Scenario: Open settings
- **WHEN** the user activates "Project settings" in a project's worktree view
- **THEN** the content area shows that project's configuration, pre-filled with its current data

### Requirement: Unavailable branches can't be selected
The system SHALL prevent selecting, in the "Existing branch" dropdown, a branch that is already checked out in the base repository or in another worktree, whatever the input method (mouse, keyboard or assistive technologies), and SHALL keep the "Create worktree" action disabled while no available branch is selected.

#### Scenario: Pressing a disabled branch through accessibility
- **WHEN** an assistive technology triggers the press action of a branch rendered disabled because it already has a worktree
- **THEN** the selection doesn't change and the dropdown stays open

#### Scenario: Create action with an unavailable branch
- **WHEN** the selected branch is not among the loaded local branches or is already checked out
- **THEN** the "Create worktree" action is disabled

### Requirement: Created worktree whose post-create command fails
When the worktree is created but its post-create command fails, the "New worktree" dialog SHALL NOT offer to create it again. It SHALL show the error with its technical details, state that the worktree was created, add it to the list and offer to close the dialog and to copy its launch command.

#### Scenario: Post-create command fails
- **WHEN** the worktree is created and its post-create command exits with an error
- **THEN** the dialog shows the error and its technical details, says the worktree exists, "Create worktree" is no longer available and the worktree is in the list

#### Scenario: Copy the launch command after the failure
- **WHEN** the user activates "Copy launch command" in that dialog
- **THEN** the created worktree's launch command is copied

### Requirement: A failed creation leaves nothing behind
When a creation fails after Git created the worktree or its new branch, and before post-creation commands run, the system SHALL remove the worktree and delete the branch it created, best-effort, so retrying with the same task id works. A branch that existed before the creation SHALL never be deleted.

#### Scenario: No free port
- **WHEN** creating `TASK-123` fails while reserving its ports
- **THEN** the worktree folder and `feature/TASK-123` are gone, and creating `TASK-123` again succeeds once a port is free

#### Scenario: Existing branch
- **WHEN** creating a worktree from the existing branch `feature/TASK-9` fails while copying its files
- **THEN** the worktree is removed and `feature/TASK-9` is kept

### Requirement: Recognize the main worktree by its location
The system SHALL treat a worktree as the main one when its location is the base repository's, comparing resolved paths, so a trailing separator, a symbolic link, a case difference on macOS or a different separator on Windows doesn't change the result.

#### Scenario: Project saved with a trailing slash
- **WHEN** the project's base repository is saved as `~/projects/main-repo/`
- **THEN** the main worktree is listed as main and is never offered for cleanup

### Requirement: Task ids with a slash map to one folder
A task id containing `/` SHALL produce a worktree folder whose name replaces each `/` with `-`, as worktrees created from existing branches already do, while the branch keeps the `/`.

#### Scenario: Team prefix
- **WHEN** the user creates a worktree for `team/TASK-1`
- **THEN** the branch is `feature/team/TASK-1` and the folder is `<worktrees root>/team-TASK-1`

### Requirement: A created worktree appears once
When a creation finishes while the project's list is being refreshed, or after a refresh already listed the new worktree, the list SHALL show the worktree exactly once.

#### Scenario: Window focused during a slow creation
- **WHEN** a creation with a slow post-creation command is running, the user switches to another app and back, and the creation then finishes
- **THEN** the new worktree appears once and the app keeps working

#### Scenario: Reload during creation
- **WHEN** the user presses Cmd/Ctrl+R while a worktree is being created
- **THEN** the new worktree appears once when the creation finishes

### Requirement: Removal errors are shown in the removal dialog
When removing a worktree fails, the removal dialog SHALL stay open and show the error with its technical details. While the removal runs, the dialog SHALL NOT be dismissed by clicking outside it or pressing Escape. When the removed worktree was selected, the selection SHALL be cleared.

#### Scenario: Removal fails
- **WHEN** removing a worktree fails because a file in it is locked
- **THEN** the dialog shows the error, and the user can retry or close it

#### Scenario: Click outside during removal
- **WHEN** the user clicks outside the dialog while the removal runs
- **THEN** the dialog stays open until the removal ends

#### Scenario: Selected worktree removed
- **WHEN** the selected worktree is removed
- **THEN** no worktree is selected and the detail shows the project summary

### Requirement: Branch loading errors are shown in the create dialog
When loading the local branches for "From existing branch" fails, the create dialog SHALL show the error in the dialog with a way to retry, instead of saying there are no branches.

#### Scenario: Git fails while listing branches
- **WHEN** the dialog opens in existing-branch mode and listing branches fails
- **THEN** the dialog shows the error and a retry action, and doesn't show "No branches available"

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

### Requirement: Branch deletion is a separate step of removal
When the user chose to delete the local branch, the system SHALL delete it after the worktree is removed, as a step with its own result. The branch SHALL be deleted only if it is contained in the project's main branch at that moment; otherwise it SHALL be kept. A kept branch SHALL NOT make the removal fail.

#### Scenario: Merged branch
- **WHEN** the user removes a worktree choosing to delete its branch and the branch is contained in the main branch
- **THEN** the worktree and the branch are deleted and the dialog closes

#### Scenario: Squash or rebase merged branch
- **WHEN** the user removes a worktree choosing to delete its branch and the branch isn't contained in the main branch, for example because its pull request was squash merged
- **THEN** the worktree is removed and disappears from the list, the branch is kept, and the app says the worktree was removed and the branch was kept because it isn't merged into the main branch

#### Scenario: Branch deletion fails
- **WHEN** the worktree is removed and Git then refuses to delete the merged branch
- **THEN** the worktree is reported as removed and the branch as kept, with Git's reason

### Requirement: Force-delete a kept branch
After a removal keeps an unmerged branch, the system SHALL offer to delete it anyway. The offer SHALL name the branch, warn that its commits may be lost, and run nothing until the user confirms.

#### Scenario: Delete the kept branch
- **WHEN** the user confirms deleting the kept branch
- **THEN** the branch is deleted even though it isn't merged, and the app reports it as deleted

#### Scenario: Leave the kept branch
- **WHEN** the user dismisses the notice
- **THEN** the branch stays

### Requirement: Local folder errors
When a local files entry is a folder, a folder that doesn't exist in the base repository, or an entry inside it that can't be read, SHALL fail creation or provisioning with `WorktreeError.LocalFilesUnavailable` naming the entry, before any Git command that creates a branch or a worktree. A failure while copying the folder's files SHALL be handled like a failed copy of a file: `WorktreeError.SecretCopyFailed`, and on creation the partially created worktree is removed.

#### Scenario: Folder missing in the base repository
- **WHEN** the local files include `keys` and the base repository has no `keys` folder
- **THEN** creation fails with `WorktreeError.LocalFilesUnavailable` listing `keys` and no worktree or branch is created

#### Scenario: Copy of a folder's file fails
- **WHEN** copying a file of a local folder into the new worktree fails after `git worktree add` succeeded
- **THEN** the partially created worktree is removed, best-effort, and the result is a failed `Result` with `WorktreeError.SecretCopyFailed`

#### Scenario: Folder copy fails on an existing worktree
- **WHEN** copying a file of a local folder fails while provisioning an existing worktree
- **THEN** the worktree is not removed and the operation fails with `WorktreeError.SecretCopyFailed`

### Requirement: Linked local folder errors
When a local folder in link mode can't be linked because the worktree already has a real folder there that holds files ShogunAi didn't copy, creation SHALL fail with `WorktreeError.LinkedFolderOccupied` naming the entry and remove the partially created worktree, as a failed copy does; provisioning an existing worktree SHALL fail with the same error and leave the worktree in place.

#### Scenario: Branch tracks files in the folder
- **WHEN** the local files include `keys` in link mode and the new worktree's branch tracks `keys/README.md`
- **THEN** creation fails with `WorktreeError.LinkedFolderOccupied` naming `keys`, and the partially created worktree is removed, best-effort

#### Scenario: Existing worktree
- **WHEN** an existing worktree's `keys` holds a file ShogunAi didn't copy and `keys` is provisioned in link mode
- **THEN** provisioning fails with `WorktreeError.LinkedFolderOccupied`, the worktree is kept and its `keys` files are untouched

### Requirement: Local patterns aren't required to match
A local files entry that is a pattern SHALL NOT produce `WorktreeError.LocalFilesUnavailable` when it matches nothing; exact file and folder entries keep failing with that error when they don't exist in the base repository.

#### Scenario: Pattern and missing file
- **WHEN** the local files include the pattern `*.p12`, which matches nothing, and the file `local.properties`, which doesn't exist in the base repository
- **THEN** creation fails with `WorktreeError.LocalFilesUnavailable` listing only `local.properties`
