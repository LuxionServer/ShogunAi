# Proposal

## Why

A repository's `.shogunai.json` can declare `env` variables that ShogunAi exports into the agent's session (Claude Code's `SessionStart` hook), into the terminal or Herdr pane it opens, into `shogun env` and into the `envFile` it writes. Unlike `hooks.postCreate`, these variables need no approval, and the only defense is a short list of reserved names. A cloned repository can therefore set variables such as `XDG_CONFIG_HOME`, `PAGER`, `HTTPS_PROXY` or `ANTHROPIC_BASE_URL` and run code or redirect traffic as soon as the user opens the agent or a terminal in it, even when the repository was never registered as a project. This is a high-severity vulnerability that must be fixed before it is disclosed.

## What Changes

- **BREAKING**: the variables a repository declares in `env`, and its `envFile`, become part of what the user approves for that repository, together with its post-creation commands, in one approval per repository. Until they are approved they aren't delivered anywhere: not in the `SessionStart` hook, `shogun env`, terminal or Herdr launches, the environment file, nor the desktop app's environment view. A change to any of them asks again. Repositories that declare no `env` or `envFile` keep their current approvals.
- Creating or provisioning a worktree asks for the repository's approval before its environment is prepared, showing the commands and the declared variables with their templates; skipping keeps the worktree without the declared variables, the environment file and the commands. Where ShogunAi can't ask, it skips them and says how to review them with `shogun trust`.
- `shogun trust` and the desktop app's project settings show and approve the declared variables and the environment file along with the commands. Saving them from project settings approves them.
- **BREAKING**: the reserved variable names grow from a handful to every name that changes how common tools behave (XDG base directories, pagers and editors, proxies and TLS settings, package registries, compilers and build tool options, SSH, Docker, Kubernetes, and Claude Code's and Anthropic's own variables), matched without regard to case. Reserved names are rejected even if approved; this also applies to the Compose provider's `portVariables`.
- **BREAKING**: variable templates and the `envFile` path can't contain line breaks, control characters or invisible Unicode format characters.
- The `SessionStart` hook names the delivered variables in the agent's context without their values, and delivers no environment in a repository that isn't registered as a project (it still prints the workflow rules).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-trust`: the approval covers the declared environment variables and the environment file as well as the post-creation commands, and every entry point that asks, skips or reviews does so for all of them.
- `worktree-environment`: declared variables and the environment file only apply once approved; the reserved names become a broad, case-insensitive set; templates reject control and format characters.
- `repository-provisioning`: preparing the environment during creation and provisioning only uses the declared variables when they are approved, and the approval is asked before the environment is prepared.
- `command-line-tool`: `shogun env` and `shogun hook session-start` only deliver approved declared variables; the hook names variables without values and delivers no environment outside registered projects.
- `worktree-detail`: the environment view shows declared variables awaiting approval without their values.
- `project-configuration`: the environment editor blocks reserved names and control characters, and saving approves the saved variables.

## Impact

- Core: `WorktreeEnvironment.kt` (reserved names, value rules), `RepositoryProvisioningFiles.kt` (parsing), `RepositoryTrust` port and `JsonRepositoryTrust` (approved subject and digest, stored format), `PostCreateApproval` (what is asked), `ResolveWorktreeEnvironmentUseCase` (gating declared variables), `WorktreeCreation.kt` and `ProvisionWorktreeUseCase` (asking before preparing the environment).
- CLI: `ShogunCli` (`hookSessionStart`, `trust`, `env`, approval prompt and skipped-notes), `CommandJson.trustJson`.
- Desktop app: approval dialog, project settings trust status, worktree detail environment view, `AppContainer` wiring.
- Docs: `docs/` pages that describe `env`, `envFile`, approvals and the `SessionStart` hook (English and Spanish), and the roadmap.
- Users: after upgrading, repositories that declare `env` or `envFile` need one approval (`shogun trust`, or the desktop app) before their variables apply again; a `.shogunai.json` that uses a newly reserved name becomes invalid until the variable is renamed.
