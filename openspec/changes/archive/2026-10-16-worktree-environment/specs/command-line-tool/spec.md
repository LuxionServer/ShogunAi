# Spec Delta

## ADDED Requirements

### Requirement: Print a worktree's environment from the command line
`shogun env [<path>] [--format sh|dotenv|json]` SHALL resolve the environment of the worktree containing the path, or the current directory, and print it: `sh` (the default) as `export NAME='value'` lines safe for `eval`, `dotenv` as `NAME=value` lines, `json` as an object. An empty environment SHALL print nothing and exit with code 0.

#### Scenario: Shell format
- **WHEN** the user runs `eval "$(shogun env)"` inside a worktree whose `PORT` resolves to 20001
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

### Requirement: Deliver the environment through Claude Code's SessionStart hook
`shogun hook session-start` SHALL read the hook's JSON from standard input, resolve the environment of the worktree containing its `cwd` field, append one `export` line per variable to the file named by `CLAUDE_ENV_FILE`, and print a single line of context naming the variables and their values. With an empty environment it SHALL write and print nothing and exit with code 0.

#### Scenario: Session in a worktree
- **WHEN** Claude Code starts a session in a worktree whose `PORT` resolves to 20001
- **THEN** `export PORT='20001'` is appended to `$CLAUDE_ENV_FILE` and the context line says this worktree uses `PORT=20001`

#### Scenario: No environment file variable
- **WHEN** `CLAUDE_ENV_FILE` isn't set
- **THEN** only the context line is printed and the command exits with code 0

#### Scenario: Repository without environment
- **WHEN** the repository declares no `env`
- **THEN** nothing is written or printed and the command exits with code 0

#### Scenario: Invalid declaration
- **WHEN** `.shogunai.json` can't be read
- **THEN** the error is printed on standard error and the command exits with code 1, without writing to `$CLAUDE_ENV_FILE`
