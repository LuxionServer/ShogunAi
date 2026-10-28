# multirepo-trust Specification

## Purpose
Makes sure a `.shogunai-multirepo.json`, which can come from a cloned repository, only decides where ShogunAi creates worktrees and which repositories it acts on after the user on this machine has seen and approved it, and again whenever it changes.

## Requirements

### Requirement: Only approved multirepos apply
A multirepo SHALL apply on this machine only when its declaration is registered in the user's ShogunAi folder and its current content is approved. Every command, hook and the desktop app SHALL treat a directory under an unregistered or unapproved declaration as outside a multirepo.

#### Scenario: Declaration in a cloned repository
- **WHEN** the user clones a repository whose top level has a `.shogunai-multirepo.json` and runs `shogun open --herdr` in it without having registered it
- **THEN** the declaration is ignored, nothing is opened as a multirepo, and the command behaves as in any repository

#### Scenario: Declaration in a parent folder
- **WHEN** `~/downloads/.shogunai-multirepo.json` exists and isn't registered, and the user runs `shogun new TASK-123` in `~/downloads/some-repo`
- **THEN** a single worktree is created in `some-repo` as outside a multirepo

#### Scenario: Registered and approved
- **WHEN** the user registered and approved `~/projects/product` and its declaration hasn't changed
- **THEN** it applies as before

### Requirement: Point to the review of an ignored declaration
When a command other than a hook ignores a declaration in the current folder or a parent because it isn't registered or approved, it SHALL print one note on standard error naming the declaration and `shogun multirepo trust`, and otherwise behave as outside a multirepo. Hooks SHALL print nothing about it.

#### Scenario: Note on the command line
- **WHEN** the user runs `shogun status` in a folder whose unregistered declaration is ignored
- **THEN** standard error names the file and says `shogun multirepo trust` reviews it, and standard output is the same as outside a multirepo

#### Scenario: Hook in that folder
- **WHEN** `shogun hook session-start` runs in that folder
- **THEN** its output doesn't mention the declaration nor `shogun multirepo trust`

### Requirement: What the approval of a multirepo covers
The approval SHALL cover each alias with the repository path it resolves to, the resolved worktrees root, the workspace repository setting, the declared `name`, when there is one, and the shared variables of `env`, when there are any. A change in any of them made outside ShogunAi SHALL make the multirepo unapproved; a change only to `defaultRepos`, or to the ignored `agent`, SHALL NOT. Approvals SHALL be stored per machine under the user's ShogunAi folder, readable and writable only by the user.

#### Scenario: Repository redirected by a pull
- **WHEN** after the approval a pull changes `repos.api` from `product-api` to `/home/user/other-repo`
- **THEN** the multirepo stops applying until the user approves it again

#### Scenario: Worktrees root moved
- **WHEN** the declaration's `worktreesRoot` changes outside ShogunAi
- **THEN** the multirepo stops applying until the user approves it again

#### Scenario: Name changed by a pull
- **WHEN** after the approval a pull changes the declaration's `name`
- **THEN** the multirepo stops applying until the user approves it again, while multirepos approved without a `name` stay approved

#### Scenario: Default repositories changed
- **WHEN** a pull only changes `defaultRepos` to another set of declared aliases
- **THEN** the multirepo stays approved

#### Scenario: Same declaration in another folder
- **WHEN** another folder holds a byte-identical declaration
- **THEN** it isn't approved

#### Scenario: Shared variables changed by a pull
- **WHEN** after the approval a pull changes `env.app.API_URL`, or adds a shared variable
- **THEN** the multirepo stops applying until the user approves it again, while multirepos approved without `env` stay approved

### Requirement: ShogunAi's own writes keep a multirepo approved
Creating a multirepo and changing its declaration through ShogunAi (`shogun multirepo …` subcommands, the desktop app's "Add multirepo" and multirepo settings) SHALL leave the written content approved. A subcommand or setting that writes SHALL refuse an unapproved multirepo, naming `shogun multirepo trust`, and change nothing.

#### Scenario: Add a repository
- **WHEN** the user runs `shogun multirepo add-repo docs product-docs` in an approved multirepo
- **THEN** the declaration includes `docs` and the multirepo is still approved

#### Scenario: Write to an unapproved multirepo
- **WHEN** the declaration changed after a pull and the user runs `shogun multirepo add-repo docs product-docs`
- **THEN** the command says the multirepo needs review with `shogun multirepo trust`, changes nothing and exits with code 1

### Requirement: Review and approve a multirepo from the command line
`shogun multirepo trust [folder]` SHALL print the declaration's path, its declared `name`, each alias with its resolved repository path, the resolved worktrees root, the workspace repository and each shared variable with its alias and template, marking every path outside the multirepo folder's parent, and say whether it's approved. On a terminal it SHALL ask and, on an explicit yes, register and approve it; `--yes` SHALL do so without asking. The folder SHALL default to the nearest declaration in the current folder or a parent.

#### Scenario: Approve a cloned workspace repository
- **WHEN** the user runs `shogun multirepo trust` in a cloned `~/projects/product` whose declaration lists `app` as `product-app` and answers yes
- **THEN** the multirepo is registered and approved and `shogun open --herdr` there opens the multirepo agent

#### Scenario: Path outside the parent
- **WHEN** the declaration lists `api` as `/srv/other/api`
- **THEN** that line is marked as outside `~/projects`

#### Scenario: Interactive no
- **WHEN** the user answers no or just presses Enter
- **THEN** nothing is registered or approved and the command exits with code 0

#### Scenario: Not a terminal
- **WHEN** standard input isn't a terminal and `--yes` isn't given
- **THEN** the command prints the review, approves nothing, says to pass `--yes`, and exits with code 1

#### Scenario: Invalid declaration
- **WHEN** the declaration is invalid
- **THEN** the command names the problem as other commands do, approves nothing and exits with code 1

#### Scenario: No declaration
- **WHEN** neither the folder nor a parent has a declaration
- **THEN** the command says no multirepo was found and exits with code 1

#### Scenario: Shared variables in the review
- **WHEN** the declaration's `env` gives `app` the variable `API_URL` as `http://localhost:${port:api.http}`
- **THEN** the review lists `API_URL` under `app` with that template

### Requirement: Revoke a multirepo from the command line
`shogun multirepo trust --revoke [folder]` SHALL forget the multirepo and its approval and local settings, leaving the declaration file, projects, worktrees and branches as they are.

#### Scenario: Revoke
- **WHEN** the user runs `shogun multirepo trust --revoke` in `~/projects/product`
- **THEN** `.shogunai-multirepo.json` is still there, the multirepo no longer applies, and its repositories behave as outside a multirepo

### Requirement: Review a multirepo in the desktop app
A registered multirepo that isn't approved SHALL stay in the sidebar marked as needing review. Its view SHALL show what `shogun multirepo trust` prints and a "Trust" action that approves it, instead of its groups, and SHALL disable its group actions, "New change" and "Open multirepo agent in Herdr" until then.

#### Scenario: Declaration changed while the app is open
- **WHEN** a pull changes `repos.api` of `product` while the app shows it
- **THEN** after the next refresh `product` is marked as needing review and its view lists the new path of `api` with "Trust"

#### Scenario: Trust from the app
- **WHEN** the user activates "Trust"
- **THEN** the multirepo is approved and its view shows its groups again

### Requirement: Multirepos registered before approvals existed
A multirepo registered by an earlier version, with no approval stored, SHALL count as not approved until the user reviews it once.

#### Scenario: Upgrade
- **WHEN** the user upgrades ShogunAi with `~/projects/product` already registered
- **THEN** commands there print the note naming `shogun multirepo trust`, the app marks it as needing review, and after one approval it applies as before
