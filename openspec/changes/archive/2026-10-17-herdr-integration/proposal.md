## Why

[Herdr](https://herdr.dev) is a terminal multiplexer built for AI agents: it keeps agent sessions alive in a background server, shows whether each agent is working, blocked or done, and groups each repository's worktrees in a space. It is the piece ShogunAi deliberately leaves out of scope (running terminals and agents), so the two fit together: ShogunAi creates and prepares the worktrees, Herdr runs the agents in them. Today, starting an agent on a ShogunAi worktree inside Herdr is a manual copy-and-paste without the worktree's environment, and a worktree created with Herdr's own "New worktree" lands in Herdr's folder (`<directory>/<repo>/<branch-slug>`), outside the project's worktrees folder and unprovisioned.

## What Changes

- **Main path: "Open in Herdr"** on each worktree row of the desktop app. It opens the worktree in Herdr as a worktree of its repository, grouped under the repository's space like Herdr's "Open worktree…", with the worktree's environment set in the shell, every variable `shogun env` resolves, providers included (ports, `ANDROID_SERIAL`, and later ones such as an iOS simulator's), and runs the project's agent command there. If Herdr already has the worktree open, it focuses it instead.
- **Rescue path: a Herdr plugin** shipped in this repository (`integrations/herdr/`), installable with `herdr plugin install LuxionServer/ShogunAi/integrations/herdr`. On Herdr's `worktree.created` event it runs a new CLI command, `shogun hook herdr`, which provisions the worktree created by mistake from Herdr like `shogun provision <path>` does, and, when the repository has a ShogunAi project, shows a Herdr notification saying the worktree is outside the project's worktrees folder and that new worktrees should be created from ShogunAi.
- Worktrees inside Herdr's worktrees folder (`~/.herdr/worktrees` by default, or the `[worktrees] directory` of Herdr's config) are classified with a new "Herdr" origin in the overview and in `shogun status`, so misplaced ones stand out.
- Documentation: a Herdr section in `docs/agents.md` (and its Spanish translation) describing the recommended flow (create with ShogunAi, open in Herdr, don't use Herdr's "New worktree") and the rescue plugin.

## Capabilities

### New Capabilities
- `herdr-integration`: opening a ShogunAi worktree in Herdr from the desktop app with its environment and agent, and the Herdr plugin with its `shogun hook herdr` command that provisions and flags worktrees created from Herdr.

### Modified Capabilities
- `worktree-overview`: the origin classification gains a "Herdr" origin for worktrees inside Herdr's worktrees folder.
- `command-line-tool`: `shogun status` reports the new Herdr origin.

## Impact

- `core`: `HerdrClient` port and its implementation on the `herdr` CLI (argv through `ShellCommandExecutor`), `OpenWorktreeInHerdrUseCase`, `HerdrConfigSource` and its reader, `WorktreeOrigin.HERDR`, `WorktreeError` cases for Herdr not installed or failing.
- `cli`: `hook herdr` subcommand, usage text, `herdr` origin label.
- `desktopApp`: row action, view model wiring, English and Spanish strings, origin label.
- New repository folder `integrations/herdr/` with the plugin manifest.
- Requires Herdr 0.9.5 or newer for the action and the plugin; the action doesn't need `shogun` on the `PATH`, the plugin does. Nothing changes for users without Herdr.
