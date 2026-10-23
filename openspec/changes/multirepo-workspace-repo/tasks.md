# Tasks

## 1. Workspace repository in the model

- [ ] 1.0 Rename the `## ADDED Requirements` header to `## Requirements` in `openspec/specs/multirepo-groups/spec.md` and `openspec/specs/multirepo-desktop/spec.md` (an earlier change left them as delta headers); verify with `openspec validate multirepo-workspace-repo --strict` no longer reporting that archive would refuse the deltas.

- [ ] 1.1 Add the optional `workspace` field (`false` or `{alias, default}`) to the declaration with its serializer; verify with a test that files without it, with `false` and with an object round-trip and keep unrelated fields.
- [ ] 1.2 Add the effective repositories (declared, detected, or the `.` entry of `repos`) to `Multirepo`/`ResolveMultirepoUseCase`, rejecting an invalid or colliding alias; verify with tests for each scenario of "Workspace repository of a multirepo" (detected, declared, off, not a repository, already declared, invalid).
- [ ] 1.3 Guard `RegisterProjectUseCase` so a multirepo folder with the workspace enabled returns the workspace repository's project instead of registering the folder, and apply it in `shogun app` and `shogun project add`; verify with tests per path, including `"workspace": false` registering normally.
- [ ] 1.4 Add `setWorkspace`/`clearWorkspace` to `ConfigureMultirepoUseCase`; verify with tests that only the declaration changes and that a folder that is not a repository is refused.

## 2. Command line

- [ ] 2.1 `shogun multirepo workspace set|clear` and the workspace in `show` (text and `--json`, marked detected); verify with CLI tests for the scenarios of "Show and declare the workspace repository".
- [ ] 2.2 `shogun new <id> --repos workspace[,…]` through the effective repositories, including the unknown-alias message and `defaultRepos` with `default: true`; verify with `CreateGroupUseCase` tests (detected, declared, group with another repository, worktrees folder `<worktreesRoot>/<alias>`).
- [ ] 2.3 `shogun clean` in the multirepo folder lists and removes loose worktrees of each effective repository after the groups; verify with tests: loose member worktree, group worktree not duplicated, workspace worktree, nothing to clean.
- [ ] 2.4 `hook agent-state` writes to the multirepo store when the cwd is exactly a known multirepo folder that is not a repository; verify with a test that an ordinary non-repository directory still writes nothing.
- [ ] 2.5 Update `docs/guide/multirepo.md`, `docs/guide/cli.md` and their `.es.md` (workspace field, detection, `workspace` subcommand, `--repos workspace`, `clean`, agent state).

## 3. Agent detection and session store

- [ ] 3.1 `FileAgentSessionStore` falls back to a store under `~/.shogunai/` keyed by the canonical folder for multirepo folders without `.git`; verify with tests using a temporary home (write, list, remove, no write for other directories).
- [ ] 3.2 Add the multirepo folder as an exact-match agent search with the multirepo agent's command in `DetectRunningAgentsUseCase`; verify with tests: agent in the folder, agent in a member repository, Herdr agent in the folder.
- [ ] 3.3 Update `docs/architecture/domain.md`, `infrastructure.md` and their `.es.md` for the declaration field, the store and the search.

## 4. Desktop app

- [ ] 4.1 Hide from the sidebar's loose projects those whose base repository is a multirepo folder with the workspace enabled, list their worktrees under the multirepo, and make "Add project"/drop explain instead of adding; verify with `ShellViewModelTest` cases.
- [ ] 4.2 Show the multirepo agent and its state in the multirepo view and the sidebar activity; verify with view-model tests and by running the app on a multirepo whose folder is a repository.
- [ ] 4.3 Workspace section in the multirepo settings (detected mark, declare checkbox, alias, default, off) and the workspace row in "New change"; verify with state tests and by hand in the app.
- [ ] 4.4 Update `docs/guide/desktop-app.md`, `docs/architecture/ui.md` and their `.es.md`.

## 5. Integration

- [ ] 5.1 Run `./gradlew test`, then check on a real multirepo whose folder is a repository: `shogun multirepo show`, `shogun new <id> --repos workspace,<member>`, an agent started in the folder shown in the multirepo view, no stray project after `shogun app`, `shogun clean`.
- [ ] 5.2 Update `docs/roadmap.md` and `docs/roadmap.es.md` if the change completes or adds items.

## Workflow follow-up

- Archive the change (`/opsx:archive`) in this branch before opening the pull request, with the base derived as `AGENTS.md` describes.
