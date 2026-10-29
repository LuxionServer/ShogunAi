## MODIFIED Requirements

### Requirement: Show groups from the command line
`shogun status` in the multirepo folder SHALL list every group by branch with, per repository, its alias, path, uncommitted changes, commits ahead and behind its base (its branch's recorded base in that repository, or the repository's base branch), whether it's merged into its base or the repository's base branch and the agents running in it, then the ungrouped worktrees of each repository, and with `--json` print the same data as one JSON object. Inside a repository it SHALL behave as before.

#### Scenario: Multirepo folder
- **WHEN** the user runs `shogun status` in `~/projects/product` with groups `feature/TASK-1` (app, api) and `feature/TASK-2` (app, docs) and a loose worktree of `api`
- **THEN** both groups are listed with one line per repository showing its path, changes, ahead/behind, merged state and agents, followed by the loose `api` worktree under the ungrouped ones

#### Scenario: JSON
- **WHEN** the user runs `shogun status --json` in the multirepo folder
- **THEN** the output is one JSON object with a `groups` list, each with `branch` and a `repos` list with each repository's `alias`, `path`, `base`, `uncommittedChanges`, `ahead`, `behind`, `merged` and `agents`, and an `ungrouped` list

#### Scenario: Status of one repository unavailable
- **WHEN** the status of one repository's worktree can't be read
- **THEN** that repository says why and the rest of the group is still shown

#### Scenario: Group branched from an epic
- **WHEN** `feature/TASK-3` was created in `app` from `epic/redesign` and in `api` from `develop`
- **THEN** `app`'s line shows its ahead/behind `of epic/redesign` and its JSON `base` is `epic/redesign`, while `api`'s is `develop`

### Requirement: Select groups to clean up
`shogun clean` in the multirepo folder SHALL list as candidates the groups whose every worktree is clean and merged into its base or its repository's base branch, or has a missing folder, and has no agent running in it; remove nothing without `--yes`; and report each other group with the reason it's kept.

#### Scenario: Merged group
- **WHEN** the group `feature/TASK-1` has `app` and `api`, both clean and merged into their bases, and no agent runs in them
- **THEN** `shogun clean` lists the group with its two worktrees and says to add `--yes`, removing nothing

#### Scenario: Partly merged group
- **WHEN** `app`'s worktree on `feature/TASK-2` is merged but `api`'s isn't
- **THEN** the group isn't a candidate and the output says it's kept because `api` isn't merged

#### Scenario: Agent in a worktree of the group
- **WHEN** every worktree of `feature/TASK-1` is merged but an agent runs in `api`'s
- **THEN** the group is kept and the output names the agent and the repository

#### Scenario: JSON
- **WHEN** the user runs `shogun clean --json` in the multirepo folder
- **THEN** the output is one JSON object listing the candidate groups with their worktrees, and the kept groups with their reasons

#### Scenario: Group merged into an epic
- **WHEN** every worktree of `feature/TASK-3` is clean and merged into its recorded base `epic/redesign`, which isn't in `develop` yet
- **THEN** the group is a candidate
