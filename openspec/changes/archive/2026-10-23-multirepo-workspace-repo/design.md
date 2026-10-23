# Design

## Context

- A multirepo is declared by `.shogunai-multirepo.json` (`MultirepoDeclaration`); `Multirepo.repositories` is built only from `repos`. The workspace repository can already be listed by hand with the path `.`, but nothing detects it.
- Agent detection (`DetectRunningAgentsUseCase`) takes `AgentSearch`es of worktrees and attributes a process cwd to the owning worktree, skipping subfolders that have their own `.git` entry. The multirepo folder is never one of them, and the multirepo view has no agent indicator.
- `FileAgentSessionStore` resolves `<git dir>/shogunai/agent-sessions` and does nothing when the path has no `.git`; `hook agent-state` additionally returns early when `ResolveRepositoryUseCase` fails.
- `lastDestination` (settings) is **not** involved in the stray project: it only stores which view was open last. A project for the folder is created by `RegisterProjectUseCase`, which `shogun app` calls with the repository of the cwd (`ShogunCli.app`) and `shogun project add` calls with the cwd, and by the desktop "Add project" dialog and drop. Running `shogun app` (or Herdr's `open` flows that call it) from the multirepo folder, when it is a Git repository, registers it as a normal project, which the sidebar then lists loose.

## Goals / Non-Goals

**Goals:**
- Treat a multirepo folder that is a Git repository as one more repository of the multirepo, by detection and by declaration.
- Show the multirepo agent in the multirepo view, with state, also when the folder isn't a repository.
- Stop the folder from becoming, or appearing as, a separate project.
- `--repos workspace` and `clean` of loose worktrees.

**Non-Goals:**
- Changing groups, `defaultRepos` semantics or the declaration of ordinary repositories.
- Detecting several nested workspaces or a workspace above the multirepo folder (a multirepo is never declared inside a repository).
- Migrating projects already registered for the folder: they are absorbed at read time, not rewritten.

## Decisions

- **Effective repositories.** `Multirepo` gets `workspace: WorkspaceDeclaration?` (`alias`, `default`) in the declaration and a computed `effectiveRepositories` that appends the workspace repository (path `.`) when it is declared, or detected (folder is a repository top level, field absent, no `repos` entry with path `.`). Detection needs a filesystem/Git check, so it lives in `ResolveMultirepoUseCase` (which already runs `ResolveRepositoryUseCase` on the folder) and the result is carried in `MultirepoContext`/`Multirepo` as `detectedWorkspaceAlias`. Everything that today iterates `repositories` (create, status, clean, groups, open, desktop catalog) moves to the effective list. Alternative: write the entry into `repos` on detection; rejected because it mutates a versioned file implicitly and the user asked for detect **and** declare.
- **`workspace` field shape.** `false` or object `{alias, default}`; a custom serializer accepts both. Absent keeps files written by 0.9.0 valid. Older versions ignore unknown keys? If not, an older reader rejects the file; the field is optional so only users who declare it are affected.
- **Worktrees folder.** `proposedWorktreesFolder(alias)` already gives `<worktreesRoot>/<alias>`; the workspace repository's project uses it when created through `--repos workspace`, with the same "propose, change only when accepted" rule as other repositories (non-interactive: accept for the workspace repository only when it had no worktrees folder of its own).
- **Absorbing the folder's project.** Instead of a new registration path, `RegisterProjectUseCase` receives a guard: if the repository is the folder of a known multirepo with workspace enabled, it returns the project of the workspace repository (registering it once with the defaults) and the `app`/`project add` commands print the multirepo notice. The desktop adds the same guard to "Add project" and filters, in the sidebar's loose list, projects whose base repository is a multirepo folder with workspace enabled; their worktrees are listed under the multirepo (`ShellViewModel` catalog). Alternative: delete the stray project; rejected because it holds settings and agents the user may want.
- **Multirepo agent detection.** `AgentSearch` gets the multirepo folder as a pseudo-worktree with the multirepo agent's command. `attributedWorktree` already attributes by longest owning path and stops at nested `.git` entries; for a folder that is **not** a repository, member repositories have `.git` so they are excluded, and loose subfolders of the folder are not counted: the match is made exact (cwd == folder) for this search kind. The result keyed by the folder path feeds the multirepo view and the sidebar activity.
- **Session store for a folder that is not a repository.** `FileAgentSessionStore` falls back to `~/.shogunai/agent-sessions/<hash of the canonical folder>/` when there is no `.git`, but only for paths that are multirepo folders (the store is given a predicate), so ordinary non-repository directories still write nothing. `hook agent-state` replaces its early return: when `ResolveRepositoryUseCase` fails it checks whether the cwd is exactly a known multirepo folder and then writes there; otherwise unchanged.
- **`clean` of loose worktrees.** `cleanGroups` already computes groups from `ReadGroupsUseCase`; it adds, per effective repository, the worktrees of the project that belong to no group, using the same eligibility as the single-repository `cleanCandidates`, and removes them with the same flow. Group worktrees are excluded by path.
- **Settings and CLI to declare.** `ConfigureMultirepoUseCase` gets `setWorkspace(alias?, default)`/`clearWorkspace()`; CLI `multirepo workspace set|clear`; the settings screen binds a checkbox and alias field to them, and `show`/settings mark a detected, undeclared workspace.

## Risks / Trade-offs

- Detection changes what existing multirepos with a repository folder do: `clean` and `status` now include the workspace repository, and its project is absorbed. `"workspace": false` is the escape hatch, and the behavior is visible in `show`.
- Exact-cwd matching misses an agent started in a subfolder of the multirepo folder that is not a repository; accepted, it belongs to no repository either.
- A store under `~/.shogunai/` keyed by folder adds a second location for session records; it is limited to multirepo folders and cleaned like the Git-dir one.
- The root cause of the stray project is inferred from the code paths, not reproduced; the guard covers every registration path (`app`, `project add`, desktop add/drop), so the exact trigger does not matter. Task 1.3 confirms it with a test per path.
