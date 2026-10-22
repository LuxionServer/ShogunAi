## MODIFIED Requirements

### Requirement: Find the repository and its project
Every command SHALL work on the Git repository that contains the current directory, or the path it's given, resolved to its main worktree even when run from a linked worktree. When the desktop app has a project for that repository, the command SHALL use its worktrees folder, local files and main branch. `shogun new`, `shogun status`, `shogun open`, `shogun clean` and `shogun multirepo` SHALL also work in a multirepo folder, which isn't a Git repository, acting on the multirepo and its groups.

#### Scenario: Run from a linked worktree
- **WHEN** the user runs `shogun status` inside a linked worktree of `~/projects/main-repo`
- **THEN** the command works on `~/projects/main-repo` and lists all its worktrees

#### Scenario: Repository with a project
- **WHEN** the repository has a project in the desktop app with worktrees folder `~/projects/worktrees` and main branch `develop`
- **THEN** `shogun new` creates worktrees under `~/projects/worktrees` and `shogun status` compares against `develop`

#### Scenario: Multirepo folder
- **WHEN** the user runs `shogun status` in a multirepo folder, which isn't a Git repository
- **THEN** the command shows the multirepo's groups instead of failing

#### Scenario: Outside a Git repository
- **WHEN** the user runs a command outside any Git repository and multirepo folder
- **THEN** it prints an error saying no repository was found and exits with code 1

### Requirement: Deliver the environment through Claude Code's SessionStart hook
`shogun hook session-start` SHALL read the hook's JSON from standard input and, inside a Git repository, resolve the environment of the worktree containing its `cwd` field, append one `export` line per variable to the file named by `CLAUDE_ENV_FILE`, and print the context: a line naming the variables and their values when there are any, followed by the short workflow rules of `shogun guide` that apply to that location. In a group worktree it SHALL also add the group's context, and in a multirepo folder it SHALL print the multirepo's context. Anywhere else outside a Git repository it SHALL write and print nothing and exit with code 0.

#### Scenario: Session in a worktree
- **WHEN** Claude Code starts a session in a worktree whose `PORT` resolves to 20001
- **THEN** `export PORT='20001'` is appended to `$CLAUDE_ENV_FILE`, the context says this worktree uses `PORT=20001` and includes the rule not to change the worktree's branch

#### Scenario: No environment file variable
- **WHEN** `CLAUDE_ENV_FILE` isn't set
- **THEN** only the context is printed and the command exits with code 0

#### Scenario: Repository without environment
- **WHEN** the repository declares no `env`
- **THEN** nothing is written to `$CLAUDE_ENV_FILE`, the context has the workflow rules but no variables line, and the command exits with code 0

#### Scenario: Main checkout
- **WHEN** Claude Code starts a session in the repository's main checkout
- **THEN** the context says to create a worktree with `shogun new` for each change and not to change the checkout's branch

#### Scenario: Herdr not available
- **WHEN** Herdr isn't installed or its server isn't running
- **THEN** the context has no rule about `shogun open --herdr` nor about messaging other sessions

#### Scenario: Short context
- **WHEN** a session starts in a worktree of a repository with every provider declared
- **THEN** the workflow rules in the context take at most ten lines and end with how to read the rest with `shogun guide`

#### Scenario: Group worktree
- **WHEN** the `cwd` is inside a worktree of a group
- **THEN** the worktree's environment is delivered as for any worktree and the context also describes the group

#### Scenario: Multirepo folder
- **WHEN** the `cwd` is a multirepo folder
- **THEN** the multirepo's context is printed, nothing is written to `$CLAUDE_ENV_FILE`, and the command exits with code 0

#### Scenario: Not a repository
- **WHEN** the `cwd` isn't inside a Git repository and isn't a multirepo folder
- **THEN** nothing is written or printed and the command exits with code 0

#### Scenario: Invalid declaration
- **WHEN** `.shogunai.json` can't be read
- **THEN** the error is printed on standard error and the command exits with code 1, without writing to `$CLAUDE_ENV_FILE`
