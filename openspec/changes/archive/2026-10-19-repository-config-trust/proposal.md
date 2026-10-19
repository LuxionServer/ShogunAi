# Proposal

## Why

`.shogunai.json` is committed to the repository, so whoever controls a branch or a clone controls what ShogunAi does when it creates or provisions a worktree. A review of the project (2026-10-19) found that its post-creation commands run without the user ever seeing them, including from the Herdr plugin and the Claude Code hooks, which work on any Git repository. Declared variables reach agent sessions unfiltered, and a few paths and quoting rules let a hostile branch write outside the worktree or run code when a value is sourced or pasted.

## What Changes

- Post-creation commands run only once the user has approved them on this machine. Approval is per repository and is tied to the exact commands, so a pull that changes them asks again. **BREAKING**: a repository's existing hooks stop running until approved once.
- The desktop app shows the commands and asks before running them. The CLI asks on a terminal, skips them with a note when it can't ask (hooks, scripts), and gains `shogun trust` to review, approve or revoke.
- Saving post-creation commands from the desktop app's settings approves them, since the user wrote them.
- Variable names that change how shells, runtimes or Git behave (`PATH`, `BASH_ENV`, `NODE_OPTIONS`, `LD_*`, `DYLD_*`, `GIT_*`, …) make `.shogunai.json` invalid.
- The environment file, copied files and linked files are written only when their real location, after following symlinks, is inside the worktree.
- Values in the environment file escape backticks, so sourcing it never runs a command.
- The copied launch command quotes the worktree path so that no character in it is interpreted by the shell it is pasted into.
- Branch names and start points that Git could read as options are rejected, and branch existence is checked exactly instead of as a pattern.

## Capabilities

### New Capabilities
- `repository-trust`: approval of the commands a repository asks ShogunAi to run, per machine, and how each entry point asks for it.

### Modified Capabilities
- `repository-provisioning`: post-creation commands run only when approved; provisioned files stay inside the worktree.
- `worktree-environment`: reserved variable names; the environment file stays inside the worktree and escapes backticks.
- `worktree-terminal-launch`: the worktree path is quoted for the pasting shell without interpretation.
- `input-validation`: branch names and start points can't be read as options.

## Impact

- `core`: provisioning and creation flows, `RepositoryProvisioningFiles` parsing, `RepositoryConfigRules`, `NioFileManager` copy, link and write, `ResolveWorktreeEnvironmentUseCase`, `CopyWorktreeLaunchCommandUseCase`, branch use cases; a new trust store under `~/.shogunai/`.
- `cli`: prompts in `new` and `provision`, notes in `hook herdr` and `hook worktree-create`, a new `shogun trust` command, usage text.
- `desktopApp`: an approval step in the create dialog and wherever provisioning runs, an approval line in project settings, strings in English and Spanish.
- Docs: `docs/guide/configuration.md`, `docs/guide/cli.md`, `docs/agents.md` and their Spanish versions; release notes must mention the one-time approval.
