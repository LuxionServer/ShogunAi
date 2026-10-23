## ADDED Requirements

### Requirement: Workspace repository of a multirepo
A multirepo folder that is the top level of a Git repository SHALL be treated as its workspace repository, with the alias `workspace`, unless the declaration says otherwise. The declaration MAY hold `workspace`: an object with an optional `alias` (and `default`, whether it joins `defaultRepos`), or `false` to turn detection off. A workspace repository's worktrees folder SHALL be `<worktreesRoot>/<alias>`.

#### Scenario: Detected without declaration
- **WHEN** `~/projects/product` is the top level of a Git repository and its declaration has no `workspace` field and no repository with the path `.`
- **THEN** the workspace repository is `workspace`, its worktrees go in `~/projects/product-worktrees/workspace` and it isn't in `defaultRepos`

#### Scenario: Declared alias
- **WHEN** the declaration has `"workspace": { "alias": "docs-root", "default": true }`
- **THEN** the workspace repository is `docs-root` and `shogun new TASK-12` in the multirepo folder also creates its worktree

#### Scenario: Detection turned off
- **WHEN** the declaration has `"workspace": false` and the folder is a Git repository
- **THEN** the folder isn't a repository of the multirepo, `--repos workspace` is rejected as unknown and its agent isn't attributed to a repository

#### Scenario: Folder isn't a repository
- **WHEN** the multirepo folder isn't a Git repository
- **THEN** there is no workspace repository, whatever the `workspace` field says

#### Scenario: Already declared as a repository
- **WHEN** the declaration lists the folder in `repos` with the path `.`
- **THEN** that alias is the workspace repository and `workspace` isn't added as a second one

#### Scenario: Invalid workspace field
- **WHEN** `workspace.alias` is invalid or collides with a key of `repos`
- **THEN** every command that reads the declaration names the problem and the file, and exits with code 1

### Requirement: Show and declare the workspace repository
`shogun multirepo show` SHALL list the workspace repository, marked `detected` when the declaration doesn't state it, with `workspace` in its `--json` object. `shogun multirepo workspace set [--alias <alias>] [--default]` SHALL declare it, and `workspace clear` SHALL set `"workspace": false`.

#### Scenario: Show a detected workspace
- **WHEN** the user runs `shogun multirepo show` in a multirepo whose folder is a Git repository and whose declaration says nothing about it
- **THEN** the output lists `workspace` as detected, with the command to declare it

#### Scenario: Declare it
- **WHEN** the user runs `shogun multirepo workspace set --default`
- **THEN** the declaration holds `"workspace": { "alias": "workspace", "default": true }` and nothing else changes

#### Scenario: Turn it off
- **WHEN** the user runs `shogun multirepo workspace clear`
- **THEN** the declaration holds `"workspace": false` and the folder is no longer a repository of the multirepo

#### Scenario: Not a repository
- **WHEN** the user runs `workspace set` in a multirepo folder that isn't a Git repository
- **THEN** the command says there is no workspace repository, changes nothing and exits with code 1

### Requirement: Create a group with the workspace repository
`shogun new <id> --repos <aliases>` in the multirepo folder SHALL accept the workspace repository's alias, declared or only detected, and create its worktree on the group's branch in `<worktreesRoot>/<alias>/<id>`, with its base branch and provisioning resolved as for any repository.

#### Scenario: Detected only
- **WHEN** the user runs `shogun new TASK-12 --repos workspace,app` in a multirepo whose folder is a Git repository
- **THEN** `workspace` and `app` each get a worktree on `feature/TASK-12`, the workspace repository's in `~/projects/product-worktrees/workspace/TASK-12`

#### Scenario: Unknown alias lists the workspace
- **WHEN** the user passes `--repos web`
- **THEN** the declared aliases listed in the error include the workspace repository's

#### Scenario: Worktree inside a repository where members are ignored
- **WHEN** the workspace repository ignores the nested member repositories
- **THEN** its worktree contains only what it versions, and the members stay in the multirepo folder

### Requirement: Multirepo agent state and session records
A session of the multirepo agent SHALL be attributed to the multirepo when its working directory is exactly the multirepo folder, never a subfolder. Its Claude Code session records SHALL be written by `shogun hook agent-state` in the folder's Git directory when it is a Git repository and otherwise in a store under `~/.shogunai/` keyed by the folder.

#### Scenario: Folder is not a repository
- **WHEN** a Claude Code session runs in a multirepo folder that isn't a Git repository and its hooks fire
- **THEN** `agent-state` records the session in the store under `~/.shogunai/` and the multirepo's agent shows its state

#### Scenario: Folder is a repository
- **WHEN** the multirepo folder is a Git repository
- **THEN** the records go to its Git directory as for any worktree

#### Scenario: Subfolder is a member repository
- **WHEN** an agent runs in `~/projects/product/product-app`
- **THEN** it's attributed to `app`'s worktree and not to the multirepo agent

#### Scenario: Session ended
- **WHEN** the session ends or its process is gone
- **THEN** its record is removed from the store as for a worktree's

### Requirement: Clean loose worktrees from the multirepo folder
`shogun clean` in the multirepo folder SHALL also list, after the groups, the worktrees of each member repository and of the workspace repository that belong to no group and are eligible for cleaning as in a single repository, and SHALL remove them under the same confirmation and options as the groups.

#### Scenario: Loose worktree of a member
- **WHEN** `api` has a merged worktree on `feature/TASK-3` that no other repository shares
- **THEN** `shogun clean` in the multirepo folder lists it under `api` after the groups

#### Scenario: Group worktrees aren't listed twice
- **WHEN** a worktree belongs to a group
- **THEN** it's listed only as part of that group

#### Scenario: Workspace repository
- **WHEN** the workspace repository has a loose merged worktree
- **THEN** it's listed under `workspace` and removed like any other

#### Scenario: Nothing to clean
- **WHEN** there are no eligible groups and no loose worktrees
- **THEN** the command says there is nothing to clean and exits with code 0

### Requirement: The multirepo folder is not a project of its own
`shogun app` and `shogun project add` run in, or given, a multirepo folder that is a Git repository SHALL NOT register it as a project: they SHALL act on the workspace repository's project only when the workspace repository is declared or detected, and otherwise report that the folder is a multirepo. A project already registered for the folder SHALL be treated as the workspace repository's.

#### Scenario: App opened from the multirepo folder
- **WHEN** the user runs `shogun app` in the multirepo folder
- **THEN** the app opens the multirepo and no project is registered for the folder

#### Scenario: Project add in the folder
- **WHEN** the user runs `shogun project add` in the multirepo folder with the workspace detected
- **THEN** the project of the workspace repository is registered once, as a repository of the multirepo

#### Scenario: Detection off
- **WHEN** the declaration has `"workspace": false`
- **THEN** both commands register the folder as an ordinary project
