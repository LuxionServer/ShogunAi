## MODIFIED Requirements

### Requirement: Print a worktree's environment from the command line
`shogun env [<path>] [--format sh|dotenv|json]` SHALL resolve the environment of the worktree containing the path, or the current directory, and print it: `sh` (the default) as `export NAME='value'` lines safe for `eval`, `dotenv` as `NAME=value` lines, `json` as an object. Declared variables SHALL only be printed while the repository is approved (see `worktree-environment`); when some await approval, a note naming them and `shogun trust` SHALL go to standard error. An empty environment SHALL print nothing on standard output and exit with code 0.

#### Scenario: Shell format
- **WHEN** the user runs `eval "$(shogun env)"` inside a worktree of an approved repository whose `PORT` resolves to 20001
- **THEN** `PORT` is 20001 in that shell

#### Scenario: Values with special characters
- **WHEN** a value contains a space, a quote or `$`
- **THEN** the `sh` output keeps it exactly once evaluated

#### Scenario: Linked worktree path
- **WHEN** the user runs `shogun env ../TASK-123 --format json` from the main worktree
- **THEN** it prints the environment of `TASK-123`, not the main worktree's

#### Scenario: Not a repository
- **WHEN** the path isn't inside a Git repository
- **THEN** an error is printed on standard error and the command exits with code 1

#### Scenario: Variables awaiting approval
- **WHEN** the user runs `shogun env` in a worktree of a repository that declares `PORT` and isn't approved
- **THEN** `PORT` isn't printed, standard error names `PORT` and `shogun trust`, and the command exits with code 0

### Requirement: Deliver the environment through Claude Code's SessionStart hook
`shogun hook session-start` SHALL read the hook's JSON from standard input and, inside a Git repository, print the short workflow rules of `shogun guide` that apply to that location. Only when the repository is registered as a project SHALL it also resolve the environment of the worktree containing its `cwd` field, append one `export` line per variable to the file named by `CLAUDE_ENV_FILE`, and print a context line naming the variables without their values; declared variables awaiting approval SHALL be named in the context as awaiting the user's approval with `shogun trust`, and not delivered. In a group worktree it SHALL also add the group's context, and in a multirepo folder it SHALL print the multirepo's context. Anywhere else outside a Git repository it SHALL write and print nothing and exit with code 0.

#### Scenario: Session in a worktree
- **WHEN** Claude Code starts a session in a worktree of a registered, approved project whose `PORT` resolves to 20001
- **THEN** `export PORT='20001'` is appended to `$CLAUDE_ENV_FILE`, the context says this worktree's environment has `PORT` without showing `20001`, and it includes the rule not to change the worktree's branch

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
- **WHEN** `.shogunai.json` of a registered project can't be read
- **THEN** the error is printed on standard error and the command exits with code 1, without writing to `$CLAUDE_ENV_FILE`

#### Scenario: Repository not registered
- **WHEN** Claude Code starts a session in a cloned repository that isn't registered as a project and whose `.shogunai.json` declares `env`
- **THEN** nothing is written to `$CLAUDE_ENV_FILE`, no environment is resolved nor reserved, the context has only the workflow rules, and the command exits with code 0

#### Scenario: Variables awaiting approval
- **WHEN** a session starts in a worktree of a registered project that declares `PORT` and isn't approved
- **THEN** nothing about `PORT` is written to `$CLAUDE_ENV_FILE`, and the context names `PORT` as awaiting the user's approval with `shogun trust`

### Requirement: No questions in JSON mode
A command run with `--json` SHALL never ask a question. When the repository isn't approved, its post-creation commands and declared variables SHALL be skipped and listed in the result, and `trust --json` without `--yes` SHALL only report.

#### Scenario: Unapproved post-creation commands
- **WHEN** the user runs `shogun new TASK-123 --json` in a repository whose post-creation commands aren't approved
- **THEN** the worktree is created without running them, and the object lists them in `skippedPostCreateCommands`

#### Scenario: Trust without confirmation
- **WHEN** the user runs `shogun trust --json` in a repository with unapproved commands
- **THEN** the object lists the commands with `approved: false`, nothing is approved and nothing is asked

#### Scenario: Unapproved variables
- **WHEN** the user runs `shogun new TASK-123 --json` in a repository that declares `PORT` and isn't approved
- **THEN** the worktree is created without `PORT`, and the object lists `PORT` in `skippedVariables`

### Requirement: JSON result of worktree creation and provisioning
`shogun new --json` SHALL report the worktree's path and branch, the skipped post-creation commands, the names of the skipped declared variables and any post-creation failure. A post-creation failure SHALL still print the object, since the worktree exists, and exit with code 1. `shogun provision --check --json` SHALL list each reported file with its path and state, and exit with code 3 when there is any.

#### Scenario: Post-creation command fails
- **WHEN** `shogun new TASK-123 --json` creates the worktree and a post-creation command exits with code 2
- **THEN** standard output is the object with the path and a `postCreateFailure` naming the command and exit code 2, and the command exits with code 1

#### Scenario: Stale file
- **WHEN** the user runs `shogun provision --check --json` and `local.properties` differs from the base repository's
- **THEN** the object's `drift` lists `local.properties` with state `differs`, and the exit code is 3

#### Scenario: Nothing skipped
- **WHEN** `shogun new TASK-123 --json` creates a worktree in an approved repository
- **THEN** the object has empty `skippedPostCreateCommands` and `skippedVariables`
