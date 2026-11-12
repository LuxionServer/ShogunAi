## MODIFIED Requirements

### Requirement: Review and approve a multirepo from the command line
`shogun multirepo trust [folder]` SHALL print the declaration's path, its declared `name`, each alias with its resolved repository path, the resolved worktrees root, the workspace repository and each shared variable with its alias and template, marking every path outside the multirepo folder's parent, and say whether it's approved. The folder SHALL default to the nearest declaration in the current folder or a parent.

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

## ADDED Requirements

### Requirement: Confirm a multirepo's approval
On a terminal, `shogun multirepo trust` SHALL ask and, on an explicit yes, register and approve the multirepo; `--yes` SHALL do so without asking.

#### Scenario: Yes on a terminal
- **WHEN** the user runs `shogun multirepo trust` on a terminal and answers yes
- **THEN** the multirepo is registered and approved

#### Scenario: Without asking
- **WHEN** the user runs `shogun multirepo trust --yes`
- **THEN** nothing is asked and the multirepo is registered and approved

#### Scenario: No or Enter
- **WHEN** the user answers no or just presses Enter
- **THEN** nothing is registered or approved and the command exits with code 0
