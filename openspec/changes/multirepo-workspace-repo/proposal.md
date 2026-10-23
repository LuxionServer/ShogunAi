# Proposal

## Why

A multirepo folder can itself be a Git repository (a "workspace" repository that versions the shared files, with the member repositories cloned inside and ignored). Today ShogunAi only half supports it: the folder's agent is shown nowhere (or under a stray project), the app can register the folder as a project of its own, the `agent-state` hook writes nothing when the folder isn't a repository, `shogun new --repos workspace` fails unless the declaration lists the workspace by hand, and `shogun clean` only knows groups. Found on a real multirepo with shogun 0.9.0.

## What Changes

- The declaration gets an optional `workspace` field (`{ "alias": "workspace" }` or `false`). When it is absent and the multirepo folder is the top level of a Git repository, the folder is **detected** as the workspace repository with the alias `workspace`. `false` turns detection off. Declared or detected, the workspace repository takes part in `--repos`, `status` and `clean`.
- `shogun multirepo show` and the multirepo settings screen show a detected, undeclared workspace repository and offer to declare it (`shogun multirepo workspace set [--alias <alias>] [--default]` / `clear`, and a checkbox in settings).
- A project whose base repository is the multirepo folder is absorbed into the multirepo: it is not listed as a loose project in the sidebar, and `shogun app` and `shogun project add` stop registering the folder as a separate project.
- The multirepo view shows the multirepo agent: the multirepo folder is one more agent search, matching only a cwd exactly equal to the folder.
- The `agent-state` hook records the sessions of a multirepo folder that is not a Git repository in a store under `~/.shogunai/` keyed by the folder; a folder that is a repository keeps using its Git directory.
- `shogun new <id> --repos workspace` creates the workspace repository's worktree in `<worktreesRoot>/<alias>/<id>`.
- `shogun clean` in the multirepo folder also lists the loose worktrees (those outside any group) of each member repository and of the workspace repository.
- Docs: `docs/guide/multirepo.md`, `cli.md`, `desktop-app.md` and their `.es.md`.

## Capabilities

### New Capabilities

### Modified Capabilities
- `multirepo-groups`: workspace repository detection and declaration, `--repos workspace`, the multirepo agent's session records and `clean` of loose worktrees.
- `multirepo-desktop`: the multirepo agent in the multirepo view, absorbing the folder's project, and the workspace option in the multirepo settings.

## Impact

- `core`: `MultirepoDeclaration`/`Multirepo` (workspace field and effective workspace repository), `ResolveMultirepoUseCase`, `CreateGroupUseCase`, `ConfigureMultirepoUseCase`, `DetectRunningAgentsUseCase`, `FileAgentSessionStore`, `RegisterProjectUseCase`.
- `cli`: `multirepo show`/`workspace`, `new --repos`, `clean`, `hook agent-state`, `app`, `project add`.
- `desktopApp`: sidebar project list, multirepo view and settings, agent detection wiring.
- `.shogunai-multirepo.json` format: one optional field; files without it keep working.
