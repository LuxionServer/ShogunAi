# Spec Delta

## ADDED Requirements

### Requirement: Keep Orca's board status in step with the worktree's state
For each non-main worktree Orca lists, ShogunAi SHALL set Orca's board status from the worktree's state: `todo` when it's new, `in-progress` when an agent is working or it needs you, `in-review` when it's stopped or inactive, and `completed` when it's merged. A folder missing, the main worktree or a state not read yet SHALL leave the status untouched.

#### Scenario: A new worktree goes to "todo"
- **WHEN** Orca lists a worktree with no commits of its own, no uncommitted changes and no agent running, and its status is `in-progress`
- **THEN** ShogunAi sets its board status in Orca to `todo`

#### Scenario: A merged worktree goes to "completed"
- **WHEN** a worktree Orca lists is merged into its base
- **THEN** ShogunAi sets its board status in Orca to `completed`

#### Scenario: A pushed worktree goes to "in-review"
- **WHEN** a worktree Orca lists has commits of its own, all pushed, nothing uncommitted, isn't merged and has no agent working
- **THEN** ShogunAi sets its board status in Orca to `in-review`

#### Scenario: The main worktree is left alone
- **WHEN** Orca lists the repository's main worktree
- **THEN** ShogunAi doesn't change its board status, comment or links

### Requirement: Respect a status moved by hand in Orca
ShogunAi SHALL write a worktree's board status only when the status that matches its state differs from the one ShogunAi last wrote for that worktree, remembered across restarts of the app and the command line. A status moved by hand in Orca SHALL stay until the worktree's state maps to a different status.

#### Scenario: A card moved by hand stays
- **WHEN** ShogunAi set a worktree to `in-review`, someone moved it to `todo` in Orca and the worktree's state hasn't changed
- **THEN** the next sync leaves the status at `todo`

#### Scenario: A state change moves the card again
- **WHEN** that worktree is then merged into its base
- **THEN** the next sync sets its status to `completed`

### Requirement: Show ShogunAi's summary as the worktree's comment in Orca
For each non-main worktree Orca lists, ShogunAi SHALL set Orca's comment to a one-line summary of the worktree's state starting with `ShogunAi: `, such as `ShogunAi: needs you · 3 uncommitted changes · 2 commits not pushed`. It SHALL write it only when Orca's comment is empty or starts with `ShogunAi: `, and only when it differs.

#### Scenario: An empty comment gets the summary
- **WHEN** Orca lists a worktree with an empty comment, 3 uncommitted changes and 2 commits not pushed to its upstream
- **THEN** ShogunAi sets its comment to a summary that starts with `ShogunAi: needs you` and mentions the 3 uncommitted changes and the 2 commits not pushed

#### Scenario: A comment written in Orca is kept
- **WHEN** a worktree's comment in Orca is "waiting for design review"
- **THEN** ShogunAi doesn't change the comment

#### Scenario: An unchanged summary isn't rewritten
- **WHEN** a worktree's comment in Orca already equals ShogunAi's summary
- **THEN** ShogunAi doesn't send a comment for it

### Requirement: Link the worktree's pull request in Orca
When the GitHub CLI (`gh`) is installed and signed in, and a worktree Orca lists has a branch pushed to an upstream and no pull request linked in Orca, ShogunAi SHALL link the most recent pull request whose head is that branch, open, merged or closed. A pull request already linked in Orca SHALL NOT be replaced or cleared.

#### Scenario: A pushed branch with a pull request is linked
- **WHEN** a worktree's branch `feature/login` is pushed, Orca has no pull request linked to it and `gh` finds pull request 42 for that branch
- **THEN** ShogunAi links pull request 42 to the worktree in Orca

#### Scenario: An existing link is kept
- **WHEN** Orca already links pull request 7 to the worktree
- **THEN** ShogunAi doesn't look the branch up and doesn't change the link

#### Scenario: No GitHub CLI
- **WHEN** `gh` isn't installed, isn't signed in or the repository isn't on GitHub
- **THEN** ShogunAi links nothing and still syncs the status and the comment

### Requirement: Link the worktree's issue in Orca
When the last segment of a worktree's branch starts with an issue number (`123-login`, `123`, `issue-123` or `gh-123`), Orca has no issue linked to it and `gh` finds that issue in the repository, ShogunAi SHALL link the issue in Orca. An issue already linked SHALL NOT be replaced or cleared.

#### Scenario: A branch named after an issue
- **WHEN** a worktree's branch is `feature/123-login`, Orca has no issue linked and issue 123 exists
- **THEN** ShogunAi links issue 123 to the worktree in Orca

#### Scenario: A task id that isn't an issue number
- **WHEN** a worktree's branch is `feature/TASK-123`
- **THEN** ShogunAi doesn't link any issue

### Requirement: Keep GitHub lookups rare
The desktop app SHALL look up a worktree's pull request or issue with `gh` at most once every 10 minutes per worktree, and each lookup SHALL have a time limit of 10 seconds per lookup, and SHALL NOT look up a link Orca already has. A failed lookup SHALL be treated as no link.

#### Scenario: Repeated checks
- **WHEN** the desktop app checks its projects every few seconds and a pushed worktree has no pull request
- **THEN** `gh` is asked about that worktree's branch at most once every 10 minutes

### Requirement: When ShogunAi syncs a worktree to Orca
ShogunAi SHALL sync the worktrees Orca lists on the desktop app's periodic check while Orca is running, the worktree it opens with "Open in Orca" or `shogun open --orca`, and the worktree `shogun hook orca setup` provisions. Syncing SHALL NOT start Orca, change a repository's visibility in Orca or act on a worktree Orca doesn't list.

#### Scenario: Orca not running
- **WHEN** Orca is installed but its desktop app isn't running during the desktop app's periodic check
- **THEN** ShogunAi doesn't start Orca and doesn't sync anything

#### Scenario: Opening from the command line
- **WHEN** `shogun open TASK-123 --orca` opens a worktree in Orca
- **THEN** the worktree's status, comment and links are synced before the command ends

#### Scenario: A worktree created from Orca
- **WHEN** Orca runs `shogun hook orca setup` for a worktree it has just created
- **THEN** after provisioning, ShogunAi syncs that worktree's status and comment in Orca

### Requirement: A failed sync never fails the action
A sync that fails, because Orca's command fails, answers late or doesn't list the worktree, SHALL NOT fail or delay the action that triggered it beyond the sync's own time limit, and SHALL NOT be reported as an error of that action. The command line SHALL print a one-line warning on standard error; the desktop app SHALL ignore it and retry on its next check.

#### Scenario: Orca rejects the update
- **WHEN** `shogun open TASK-123 --orca` opens the worktree but `orca worktree set` fails
- **THEN** the command still exits with success and prints a warning naming the worktree on standard error
