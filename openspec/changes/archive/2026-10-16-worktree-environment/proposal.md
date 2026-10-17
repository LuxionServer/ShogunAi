# Proposal

## Why

A worktree isolates files, not the runtime: two worktrees that start the same dev server fight over the same port, and anything that must differ per worktree (a port, an app suffix, later an emulator serial) has no way to reach the agent's shell, because ShogunAi doesn't start the agents. Claude Code now lets a `SessionStart` hook persist variables for every Bash command of the session (`CLAUDE_ENV_FILE`), and other tools can `eval` a command's output, so a per-worktree environment computed by `shogun` can reach any agent. It's also the base the emulator provider and per-worktree Gradle settings need.

## What Changes

- `.shogunai.json` gains an `env` object of variable templates. A value can use `${worktree}` (the worktree's folder name), `${port}` (a port reserved for that variable) and `${port:<key>}` (a port shared by every variable using the same key); `$$` is a literal `$`. An unknown placeholder or a non-string value makes the file invalid.
- Ports are reserved per worktree: unique among the repository's worktrees, free on the machine when reserved, and stable afterwards. Reservations live in Git's per-worktree administrative directory (`.git/worktrees/<id>/` for a linked worktree, `.git/` for the main one), so removing or pruning a worktree drops them with it and worktrees created by other tools work the same way.
- Values are resolved when a worktree is created or provisioned, and lazily the first time the environment is asked for.
- `shogun env [<path>] [--format sh|dotenv|json]` prints the environment of the worktree containing the path or the current directory (`sh`, the default, prints `export` lines for `eval`).
- `shogun hook session-start`, a Claude Code `SessionStart` hook: appends the worktree's variables to `$CLAUDE_ENV_FILE` and prints a short line of context for Claude naming the variables and ports.
- An optional `envFile` in `.shogunai.json` (e.g. `".env.shogunai"`) is written into each worktree in dotenv format when it's created or provisioned, for tools that read dotenv files. It's never written over a file tracked by Git.
- Desktop app: each worktree's row shows its reserved ports.
- Docs: recipes for Claude Code (`SessionStart` hook), direnv (`.envrc` with `eval "$(shogun env)"`), Cursor, Codex and other tools.

## Capabilities

### New Capabilities
- `worktree-environment`: declaring per-worktree variables in the repository, resolving their templates, reserving and storing ports per worktree, and writing the optional environment file.

### Modified Capabilities
- `repository-provisioning`: creating or provisioning a worktree resolves its environment and writes the environment file when one is declared.
- `command-line-tool`: new `shogun env` command and `shogun hook session-start`.
- `worktree-overview`: each worktree's row shows its reserved ports.

## Impact

- `core`: environment declaration in `RepositoryProvisioning` and `RepositoryProvisioningFiles`; a port for reading and writing per-worktree reservations and one for checking that a port is free; use cases to resolve a worktree's environment and to format it; creation and provisioning write the environment file.
- `cli`: `env` command, `hook session-start`, help text.
- `desktopApp`: reserved ports in the worktree row, strings in English and Spanish.
- Docs: `docs/agents.md`, README, architecture pages (both languages), roadmap.
- Repositories without `env` behave as before; older ShogunAi versions ignore the new fields.
