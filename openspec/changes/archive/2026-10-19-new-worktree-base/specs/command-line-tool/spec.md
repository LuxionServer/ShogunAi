## ADDED Requirements

### Requirement: Create a worktree from a remote branch on the command line
`shogun new --branch <name>` SHALL accept a branch that exists only on `origin`, given as `<name>` or `origin/<name>`, creating a local branch `<name>` that tracks it, with the same folder, provisioning and rollback as the desktop app. A local branch with that name SHALL take precedence.

#### Scenario: Remote-only branch
- **WHEN** the user runs `shogun new --branch feature/TASK-9` and only `origin/feature/TASK-9` exists
- **THEN** a local `feature/TASK-9` tracking it is created with its worktree, and the worktree's path is printed

#### Scenario: Branch nowhere
- **WHEN** the user runs `shogun new --branch feature/missing` and neither a local nor an `origin/` branch has that name
- **THEN** the command prints that the branch doesn't exist, creates nothing and exits with code 1

### Requirement: Fetch before creating on the command line
`shogun new --fetch` SHALL fetch from `origin` before resolving `--from` or `--branch`, without waiting for credentials. When the fetch fails, the command SHALL print Git's error, create nothing and exit with code 1.

#### Scenario: Fetch then create
- **WHEN** the user runs `shogun new TASK-123 --from origin/develop --fetch`
- **THEN** the command fetches from `origin` and the new branch starts at the fetched `origin/develop`

#### Scenario: Fetch fails
- **WHEN** the user runs `shogun new TASK-123 --fetch` without network
- **THEN** the command prints the fetch error on standard error, creates nothing and exits with code 1
