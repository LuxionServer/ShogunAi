# Tasks

## 1. Declaration and templates

- [x] 1.1 Add `EnvironmentDeclaration`, `EnvironmentVariable` and template parts to `RepositoryProvisioning`, and parse `env` and `envFile` in `RepositoryProvisioningFiles` (names, placeholders, `$$`, relative `envFile` inside the worktree); verify with tests for valid, missing and every invalid case, and that `addCopyPatterns` keeps both fields
- [x] 1.2 Document `env`, `envFile` and the placeholders in `docs/architecture/domain.md` and `infrastructure.md` (both languages); verify `mkdocs build --strict` passes

## 2. Reservations

- [x] 2.1 Add the `EnvironmentStateStore` and `PortProbe` ports with fakes in `testFixtures`; implement `NioEnvironmentStateStore` (per-worktree `shogunai-env.json`, listing all worktrees' reservations, lock file, atomic writes) and `SocketPortProbe`; verify with infrastructure tests on a temporary repository with a linked worktree, including a busy port
- [x] 2.2 Implement `ResolveWorktreeEnvironmentUseCase` (reserve and read-only modes, lowest free port from 20000, uniqueness across worktrees, stability, release of unused keys, nothing written for an empty declaration) and `EnvironmentFormat` (`sh`, `dotenv`, `json` with escaping); verify with use case tests for every "Resolve variable templates", "Reserve ports per worktree" and "Keep reservations with the worktree" scenario
- [x] 2.3 Describe the reservation store, the lock and the resolution in the architecture docs (both languages); verify `mkdocs build --strict` passes

## 3. Provisioning and the environment file

- [x] 3.1 Add `FileManager.writeText` and `WriteEnvironmentFileUseCase` (inside the worktree, refuses tracked files); verify with tests for written, tracked and outside paths
- [x] 3.2 Resolve the environment and write the file in creation (both flows) and provisioning, with creation's rollback; verify with use case tests for reservation on creation, the file, a failed write removing the new worktree, and provisioning a foreign worktree
- [x] 3.3 Update the provisioning sections of the architecture docs (both languages); verify `mkdocs build --strict` passes

## 4. Command line

- [x] 4.1 Parse `shogun env [<path>] [--format sh|dotenv|json]` and `shogun hook session-start`, and update `shogun help`; verify with parsing tests including usage errors
- [x] 4.2 Implement `env` and `hook session-start` (`CLAUDE_ENV_FILE` through an injected lookup, context line, empty environment, invalid file exits 1 without writing); verify with CLI tests for each scenario
- [x] 4.3 Document the commands in `docs/architecture/cli.md`, the recipes in `docs/agents.md` (Claude Code `SessionStart`, direnv, Cursor, Codex, any tool) and the README, in both languages; verify `mkdocs build --strict` passes

## 5. Desktop app

- [x] 5.1 Load reserved ports per worktree without reserving and show them in the worktree row, with English and Spanish strings; verify with view model tests (ports shown, nothing reserved) and in the running app
- [x] 5.2 Describe the ports in the row in `docs/architecture/ui.md` (both languages); verify `mkdocs build --strict` passes

## 6. Roadmap and end-to-end validation

- [x] 6.1 Update `docs/roadmap.md` and `docs/roadmap.es.md`: remove the delivered "Per-worktree environment for agents" item and keep the emulator and Gradle settings items pointing to it as available; verify both languages match
- [x] 6.2 Validate end to end on a toy repository in the scratchpad: two worktrees get different stable ports, `eval "$(shogun env)"` sets them, `envFile` is written and refused over a tracked file, removing a worktree frees its ports, and a real `claude -p -w` session with the `WorktreeCreate` and `SessionStart` hooks sees `$PORT` in a Bash command
- [x] 6.3 Run `./gradlew test`, `mkdocs build --strict` and `openspec validate worktree-environment --strict`; verify all pass

## Workflow follow-up

- Archive the change with `/opsx:archive` in the same branch once validated, before opening the pull request to `epic/pivot`.
- Add the pull request to the GitHub Project (layer C, step 9) and update the epic's handoff section.
