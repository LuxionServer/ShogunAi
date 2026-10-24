# Proposal

## Why

A `.shogunai-multirepo.json` is a file that can come from a cloned repository, yet ShogunAi trusts it as if the user had written it: `shogun open --herdr` runs its `agent.agentCommand` in a Herdr pane without showing it, the file also picks the name the command prints, and it's found in the current folder or any parent even when the user never registered that multirepo. Its `repos` and `worktreesRoot` also accept absolute paths, so a declaration changed by a pull can make `shogun new` create branches and worktrees in other repositories of the user and run their already approved post-creation commands. This is a high-severity code execution path (advisory GHSA-5c7x-q846-43h3), and the workflow guide and the `SessionStart` hook tell agents to run `shogun open <id> --herdr`, so an agent working in a malicious repository would trigger it too.

## What Changes

- **BREAKING**: The multirepo agent (command, name, headroom) is stored per multirepo in the user's local ShogunAi folder (`~/.shogunai/multirepos.json`), like a project's agent. The `agent` field of `.shogunai-multirepo.json` is ignored, `shogun multirepo show` and the desktop app say so, and ShogunAi stops writing it. A multirepo without a local agent opens `claude` with headroom; users who set another agent set it again once with `shogun multirepo agent`.
- **BREAKING**: Only multirepos the user registered and approved on this machine apply. A declaration found in the current folder or a parent that isn't registered is ignored: commands behave as outside a multirepo and, outside the hooks, print a note naming `shogun multirepo trust`.
- The approval of a multirepo covers what decides where ShogunAi acts: each alias with its resolved repository path, the resolved worktrees root and the workspace setting. When the declaration changes outside ShogunAi (a pull, a manual edit), the multirepo stops applying until the user reviews and approves it again. Writes made by ShogunAi itself (`shogun multirepo …`, the app's multirepo settings and "Add multirepo") keep it approved. Multirepos registered before this change need one review.
- New `shogun multirepo trust [--yes | --revoke]`: shows the declaration's repositories with their resolved paths, the worktrees root and the workspace repository, marks paths outside the multirepo folder's parent, and registers and approves it (asking on a terminal, or with `--yes`); `--revoke` forgets it without touching the file.
- `shogun multirepo` subcommands that write refuse an unapproved multirepo; `shogun multirepo show` works and reports whether it's approved.
- The desktop app shows a registered multirepo that needs review with its paths and a "Trust" action, disables its group actions, "New change" and "Open multirepo agent in Herdr" until then, and saves the multirepo agent locally from its settings.

## Capabilities

### New Capabilities

- `multirepo-trust`: which multirepo declarations ShogunAi applies on this machine, what their approval covers, when it's lost, and how the user reviews and approves or revokes it from the command line and the desktop app.

### Modified Capabilities

- `multirepo-groups`: the declaration no longer carries the agent; known multirepos are only the registered and approved ones; the multirepo agent is set and read locally; `show` reports the approval and an ignored declared agent; writing subcommands refuse an unapproved multirepo.
- `multirepo-desktop`: the multirepo settings save the agent locally; adding an existing declaration from the app shows its paths before approving it; a multirepo needing review shows it instead of its groups.

## Impact

- Core: `Multirepo`, `MultirepoDeclaration`, `MultirepoSource`/`JsonMultirepoSource` (registry gains per-multirepo local settings and approval digest under the existing file lock), `ResolveMultirepoUseCase` (only approved multirepos), `ConfigureMultirepoUseCase` (agent stored locally, writes re-approve), `OpenMultirepoAgentInHerdrUseCase`.
- CLI: `ShogunCli` (`open`, `new`, `status`, `clean`, `multirepo …`, hooks), `MultirepoJson`, `CliHelp`.
- Desktop: `AppContainer`, `MultirepoSettingsState`/`MultirepoSettingsView`, the multirepo setup dialog, `WorktreeBoard` and the sidebar.
- Docs: `docs/guide/multirepo.md`, `docs/guide/cli.md`, `docs/guide/desktop-app.md`, `docs/architecture/domain.md`, `docs/architecture/infrastructure.md` and their Spanish translations; roadmap.
- Compatibility: older versions read `multirepos.json` as before (they ignore the new keys); if one rewrites the file, the approvals and local agents it drops are asked for again.
