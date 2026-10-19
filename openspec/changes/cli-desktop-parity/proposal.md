# Proposal

## Why

The desktop app and the `shogun` command are meant to be two faces of the same worktree layer, but the project review of 2026-10-19 found gaps in both directions that matter in daily use. From the CLI there is no way to remove one worktree on purpose: only `clean` or the hooks. The desktop app, which presents itself as the control tower for worktrees created by any tool, can't provision one of those worktrees or tell that its files went stale. It also hides the worktree's environment variables, which `shogun env` prints. Choosing the base of a new worktree in the desktop app is already planned in the roadmap and stays there.

## What Changes

- `shogun remove <task id | path | branch>` removes one worktree with the same rules as the desktop app: it refuses changes unless `--force`, deletes the branch only with `--delete-branch` and only when merged into the main branch, refuses while an agent runs in it unless `--force`, and stops its providers' resources as cleanup does.
- `shogun repair` runs the same repair the desktop app runs after a repository moves.
- The worktree detail in the desktop app shows whether the worktree's provisioned files are up to date, and offers "Provision" for a worktree that never was or whose files are missing or stale.
- The detail shows the worktree's environment variables, not only its ports, with a copy action.
- Project settings can uninstall the KMP init script, not only install it.

## Capabilities

### New Capabilities

### Modified Capabilities
- `command-line-tool`: `shogun remove` and `shogun repair`.
- `worktree-detail`: provisioning state and action; environment variables.
- `project-configuration`: uninstall the KMP init script.

## Impact

- `cli`: new `CliCommand.Remove` and `CliCommand.Repair`, usage text.
- `core`: reuse `RemoveWorktreeUseCase`, `RepairWorktreesUseCase`, `ProvisionWorktreeUseCase`, `CheckProvisionedFilesUseCase` and the provider cleanup contributions; no new domain logic beyond resolving a task id, path or branch to a worktree.
- `desktopApp`: `AppContainer` wiring for provisioning, detail pane sections, the KMP section in settings, i18n strings.
- Docs: `docs/guide/cli.md`, `docs/guide/worktrees.md` (or the guide page describing the detail) and their Spanish versions.
- Depends on `process-robustness` for the shared branch-deletion rule and the agent guard.
