# error-presentation Specification

## Purpose

Defines how the app reports failures so the user understands what went wrong and what to do next, while the technical output stays available for those who need it.

## Requirements

### Requirement: Errors explain what failed and what to do
Every error shown to the user SHALL have a summary that says in plain words what failed, and a next step that says what the user can do about it, both in the selected language. Neither SHALL contain a raw command line, an exit code or a tool's raw output.

#### Scenario: Base repository missing
- **WHEN** listing worktrees fails because the project's base repository folder no longer exists
- **THEN** the error says the project's repository wasn't found at its path, and suggests updating the path in the project's settings, with an action that opens them

#### Scenario: Android SDK not found
- **WHEN** starting an emulator fails because no Android SDK is found
- **THEN** the error says the Android SDK wasn't found, and suggests installing it or setting `ANDROID_HOME`

### Requirement: Technical details stay available
An error caused by a failed command SHALL keep its command, exit code and the tool's output, unchanged, behind a collapsed "Technical details" section with an action that copies them.

#### Scenario: Expand the details
- **WHEN** an error caused by a failed `git` command is shown and the user expands "Technical details"
- **THEN** the command, its exit code and Git's output are shown exactly as Git produced them

#### Scenario: Copy the details
- **WHEN** the user activates the copy action in "Technical details"
- **THEN** the command, exit code and output are copied to the clipboard as plain text

### Requirement: Frequent Git failures are recognized
When a Git command fails, the app SHALL recognize from its output these causes and explain them specifically: another Git process holds a lock file, permission is denied on a path, the folder isn't a Git repository, the branch already exists, and the worktree has local changes. Other failures SHALL get a general explanation with the technical details.

#### Scenario: Lock file
- **WHEN** a Git command fails because `index.lock` exists
- **THEN** the error says another Git process seems to be running in the repository, and suggests waiting for it to finish or removing the named lock file if no Git process is running

#### Scenario: Unrecognized failure
- **WHEN** a Git command fails with output none of the known causes match
- **THEN** the error says the Git operation failed and suggests checking the technical details, which hold Git's output

### Requirement: Errors are shown where the action happened
An error SHALL be shown in the place where the failed action was started: inside the dialog for dialog actions, in the worktree's detail for actions on one worktree, in the project's group header for a failure loading that project, in the machine view for its actions, and at the top of the view for failures that affect the whole view.

#### Scenario: Emulator fails to start
- **WHEN** starting a worktree's emulator fails
- **THEN** the error is shown in that worktree's detail, and the rest of the view shows no error

#### Scenario: One project fails to load
- **WHEN** one project's worktree list can't be read in "All worktrees"
- **THEN** the error is shown in that project's group, and the other groups are unaffected

### Requirement: Errors last until dismissed or resolved
An error SHALL stay visible until the user dismisses it or the same action succeeds. Errors from actions that can be repeated safely (reloading, starting or stopping a device, stopping a daemon) SHALL offer "Try again", which repeats the action.

#### Scenario: Try again
- **WHEN** reloading a project failed and the user activates "Try again" after fixing the cause
- **THEN** the reload runs again and, if it succeeds, the error disappears

#### Scenario: Unrelated success
- **WHEN** an error is shown in one worktree's detail and the user copies another worktree's launch command
- **THEN** the error stays

### Requirement: Folder in use by another program is explained
When a worktree's folder can't be deleted because another program is using it, the error SHALL say in plain words, in the selected language, that a program (a terminal, an editor, a sync client) is using the folder, and suggest closing it and removing the worktree again. When Git had already unregistered the worktree, it SHALL add that removing it again will finish the job. The technical details SHALL keep Git's command and output.

#### Scenario: Windows lock
- **WHEN** removing a worktree fails with `failed to delete '…': Permission denied` and closing Herdr didn't free the folder
- **THEN** the error says a program is using the folder, suggests closing it and trying again, and the technical details hold Git's output

#### Scenario: Unregistered worktree
- **WHEN** the same failure leaves the worktree unregistered in Git
- **THEN** the error also says that removing the worktree again finishes the removal

### Requirement: Unreachable external tools are shown as an error state
When a view can't be read because an external tool isn't installed or isn't reachable, the view SHALL replace its content with an error state that names the missing tool in plain words, offers "Try again", and keeps the tool's raw message folded under "Technical details" with its copy action.

#### Scenario: Docker not running
- **WHEN** the Compose projects view is shown and Docker's daemon isn't reachable
- **THEN** the view says Docker isn't available, offers "Try again", and shows the socket error only after expanding "Technical details"

#### Scenario: Try again after starting the tool
- **WHEN** the user starts Docker and activates "Try again"
- **THEN** the view is read again and, if it succeeds, the error state is replaced by the project list

### Requirement: An error only points to technical details it has
An error whose next step tells the user to check its technical details SHALL show those details; an error that carries no detail SHALL give a next step that doesn't depend on them.

#### Scenario: Background app that fails to launch
- **WHEN** launching a worktree's desktop app in the background fails and the error is shown
- **THEN** its technical details show the launch log's path and its last lines

#### Scenario: Failure without a reason
- **WHEN** stopping a worktree's background app fails without a reason, or a repository configuration can't be read without a detail
- **THEN** the next step says what to try instead of pointing to technical details

#### Scenario: Unreadable local file
- **WHEN** a local file can't be read and the system gives a reason
- **THEN** the reason is shown in the error's technical details
