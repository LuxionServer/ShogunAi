# Spec Delta

## MODIFIED Requirements

### Requirement: Deliver the environment through Claude Code's SessionStart hook
`shogun hook session-start` SHALL read the hook's JSON from standard input and, inside a Git repository, resolve the environment of the worktree containing its `cwd` field, append one `export` line per variable to the file named by `CLAUDE_ENV_FILE`, and print the context: a line naming the variables and their values when there are any, followed by the short workflow rules of `shogun guide` that apply to that location. Outside a Git repository it SHALL write and print nothing and exit with code 0.

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

#### Scenario: Not a repository
- **WHEN** the `cwd` isn't inside a Git repository
- **THEN** nothing is written or printed and the command exits with code 0

#### Scenario: Invalid declaration
- **WHEN** `.shogunai.json` can't be read
- **THEN** the error is printed on standard error and the command exits with code 1, without writing to `$CLAUDE_ENV_FILE`
