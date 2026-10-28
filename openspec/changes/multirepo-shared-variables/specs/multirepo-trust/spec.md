# Spec Delta

## MODIFIED Requirements

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
