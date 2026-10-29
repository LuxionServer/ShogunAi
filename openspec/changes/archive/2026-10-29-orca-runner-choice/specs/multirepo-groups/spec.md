## MODIFIED Requirements

### Requirement: Tell the multirepo agent about the multirepo
`shogun hook session-start` in the multirepo folder SHALL print a context naming the multirepo, its repositories with their paths, the multirepo folder's `AGENTS.md` when it exists, and saying that `shogun status` lists the groups, `shogun new <id>` creates one and `shogun open <id> --<runner>`, naming the runner in use, opens its worktrees, without writing to `CLAUDE_ENV_FILE`.

#### Scenario: Session in the multirepo folder
- **WHEN** Claude Code starts a session in `~/projects/product`
- **THEN** the context lists `docs`, `app` and `api` with their paths and the three commands, nothing is written to `$CLAUDE_ENV_FILE`, and the command exits with code 0

#### Scenario: Orca in use
- **WHEN** Orca is the runner in use and Claude Code starts a session in `~/projects/product`
- **THEN** the context says `shogun open <id> --orca` opens a group's worktrees

#### Scenario: Shared instructions
- **WHEN** `~/projects/product/AGENTS.md` exists
- **THEN** the context says to read it for what all the repositories share
