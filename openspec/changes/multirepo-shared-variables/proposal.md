# Proposal

## Why

Each worktree of a multirepo group gets its own reserved ports, but the repositories of a group can't see each other's: the app worktree of `feature/TASK-7` has no way to learn the port its own `api` worktree reserved, so agents hard-code ports, point the app at the wrong backend, or copy numbers by hand between sessions. The roadmap's first item, "the environment of backends and services", lists this as one of its two remaining pieces.

## What Changes

- `.shogunai-multirepo.json` gains an optional `env` object mapping a repository alias to the variables the multirepo adds to that repository's worktrees, e.g. `"env": { "app": { "API_URL": "http://localhost:${port:api.http}" } }`. Names and values follow the rules of a repository's own `env` (valid names, no reserved names, no control or invisible characters).
- Shared templates accept `${worktree}`, `$$` and a new placeholder `${port:<alias>.<key>}`: the port that the repository `<alias>` reserves for its shared key `<key>` (declared there as `${port:<key>}` in its own `.shogunai.json`). Plain `${port}` and `${port:<key>}` are not accepted in the multirepo's `env`; ports a repository uses itself stay declared in that repository.
- The referenced port is the one of `<alias>`'s worktree on the same branch as the worktree being resolved (its partner in the group), or of `<alias>`'s main checkout when there is no such worktree (a loose worktree, the main checkout, a group without `<alias>`). Resolving with reservation reserves it in that worktree the same way its own environment would, so the number is the one that worktree's agent receives.
- Shared variables are delivered exactly like the repository's own variables: `shogun env`, the `SessionStart` hook, opening in the terminal or Herdr, and the `envFile`. A repository's own `env` wins over a shared variable of the same name, and a shared variable wins over a provider's.
- A shared variable that can't be resolved (the referenced repository isn't approved or doesn't declare that key, or its worktree can't be read) is left out and reported: `shogun env` names it on standard error and the hook's context names it, without failing either.
- The multirepo's approval covers its `env`: the variables are part of what `shogun multirepo trust` and the desktop app's review show, and a change to them made outside ShogunAi makes the multirepo need review again. Multirepos without `env` keep their approval. `shogun multirepo show` lists the shared variables, also in `--json`.
- ShogunAi's own writes to the declaration (`shogun multirepo …` subcommands, the desktop app's multirepo settings) keep the `env` block as it is.

## Capabilities

### New Capabilities

- `multirepo-environment`: variables a multirepo declares for its repositories' worktrees, the cross-repository port placeholder, which worktree a reference resolves against, precedence against the repository's own and providers' variables, and how unresolved shared variables are reported.

### Modified Capabilities

- `multirepo-groups`: the declaration accepts the optional `env` object, and `shogun multirepo show` lists the shared variables.
- `multirepo-trust`: the approval covers the shared variables, and the review prints them.

## Impact

- `core`: `MultirepoDeclaration` gains `env`; template parsing gains the cross-repository port placeholder and the shared-template rules; multirepo validation checks the `env` block; `approvalSubject` includes it; `ResolveWorktreeEnvironmentUseCase` adds the shared variables through a new port that finds the multirepo, the partner worktree and its ports; `WorktreeEnvironment` reports unresolved shared variables.
- `cli`: wiring of the new port, the unresolved note in `shogun env` and the hook, and the shared variables in `shogun multirepo trust` and `show` (text and JSON, additive fields).
- `desktopApp`: wiring in `AppContainer`, and the shared variables in the multirepo review.
- Docs: the multirepo and configuration guides (English and Spanish), and the roadmap on archive.
- No overlap with the planned `claude-session-names` change beyond both touching `ShogunCli.kt`: that change adds session names to `open --herdr` and `status` output, this one adds environment variables.
