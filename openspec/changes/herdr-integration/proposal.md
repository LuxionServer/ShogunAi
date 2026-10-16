## Why

[Herdr](https://herdr.dev) is a terminal multiplexer built for AI agents: it keeps agent sessions alive in a background server, shows whether each agent is working, blocked or done, and can create Git worktrees itself. It is the piece ShogunAi deliberately leaves out of scope (running terminals and agents), so the two fit together: Herdr runs the agents, ShogunAi prepares the worktrees they work in. Today a worktree created from Herdr comes out unprovisioned (no secrets, no `.shogunai.json` files, no environment file), and starting an agent on a ShogunAi worktree inside Herdr is a manual copy-and-paste.

## What Changes

- A Herdr plugin shipped in this repository (`integrations/herdr/herdr-plugin.toml`), installable with `herdr plugin install LuxionServer/ShogunAi/integrations/herdr`. It hooks Herdr's `worktree.created` event and runs `shogun hook herdr`.
- A new CLI command, `shogun hook herdr`, that reads the event Herdr passes to plugin hooks (`HERDR_PLUGIN_EVENT` and `HERDR_PLUGIN_EVENT_JSON`) and provisions the worktree Herdr just created, exactly like `shogun provision <path>`.
- An "Open in Herdr" action on each worktree row of the desktop app: it opens a Herdr workspace in the worktree with the worktree's environment (reserved ports, `ANDROID_SERIAL`, …) set in its shell, and runs the project's agent command there. If the worktree already has a Herdr workspace, it focuses that one instead of opening a second.
- Worktrees inside Herdr's worktrees folder (`~/.herdr/worktrees` by default, or the `[worktrees] directory` of Herdr's config) are classified with a new "Herdr" origin in the overview and in `shogun status`.
- Documentation: a Herdr section in `docs/agents.md` (and its Spanish translation) covering the plugin, the desktop action and how the per-worktree environment reaches agents started in Herdr.

## Capabilities

### New Capabilities
- `herdr-integration`: the Herdr plugin and its `shogun hook herdr` command that provision worktrees Herdr creates, and opening a worktree in Herdr from the desktop app with its environment and agent.

### Modified Capabilities
- `worktree-overview`: the origin classification gains a "Herdr" origin for worktrees inside Herdr's worktrees folder.
- `command-line-tool`: `shogun status` reports the new Herdr origin.

## Impact

- `core`: new `HerdrClient` port and its infrastructure implementation (runs the `herdr` CLI as argv through `ShellCommandExecutor`), an `OpenWorktreeInHerdrUseCase`, a reader for Herdr's worktrees folder, new `WorktreeOrigin.HERDR`, new `WorktreeError` cases for Herdr not being installed or failing.
- `cli`: `hook herdr` subcommand, usage text, `herdr` origin label.
- `desktopApp`: row action, view model wiring, English and Spanish strings, origin label.
- New repository folder `integrations/herdr/` with the plugin manifest.
- Requires Herdr 0.9 or newer on the user's machine for the plugin and the action; nothing changes for users without Herdr.
