# Proposal

## Why

ShogunAi's Herdr plugin (`shogunai.worktrees`) is what provisions the worktrees Herdr creates. When it is installed linked to a folder that no longer exists (for example a worktree that was removed after `herdr plugin link`), Herdr keeps listing it as enabled but can't read its manifest, and stops running its hooks without any error: worktrees created from Herdr silently lose their local files, ports and environment. Neither ShogunAi command line nor its app notices it today.

## What Changes

- Core reads the Herdr plugins with `herdr plugin list --json` and tells whether ShogunAi's plugin is installed, disabled, broken (Herdr reports warnings for it, such as `manifest unavailable`) or missing.
- Core can reinstall the plugin from the repository: it removes the current install (`herdr plugin unlink` for a linked one, `herdr plugin uninstall` for a GitHub one) and installs it again with `herdr plugin install LuxionServer/ShogunAi/integrations/herdr`.
- `shogun hooks status` adds a line for the Herdr plugin when Herdr is installed, with Herdr's warnings when it is broken and the command that fixes it; `--json` adds a `herdr` object.
- `shogun hooks install --herdr [--yes] [--json]` shows the Herdr commands it will run, asks first unless `--yes`, and reinstalls (or installs) the plugin.
- The hooks section of the app's Settings shows the plugin's state when Herdr is installed, with a "Reinstall" (broken) or "Install" (missing) action that shows the Herdr commands and runs them after confirmation.

## Capabilities

### New Capabilities

- `herdr-plugin-health`: checking that ShogunAi's Herdr plugin is installed and working, and reinstalling it from the repository, from the command line and the app's Settings.

### Modified Capabilities

None. `claude-code-hooks` keeps its requirements; the Herdr line in `shogun hooks status` and the Herdr part of the hooks section are owned by the new capability.

## Impact

- `core`: `HerdrClient` port and `CliHerdrClient` gain plugin listing, removal and installation; a new use case decides the plugin's state and the reinstall steps; `FakeHerdrClient` in the test fixtures.
- `cli`: `hooks status` text and JSON, `hooks install --herdr`, help text.
- `desktopApp`: the hooks section of Settings (`ui/shell`), its state holder, the app container and the English and Spanish strings. The `ui/worktree` package isn't touched.
- Docs: the CLI guide (`docs/guide/cli.md` and `.es.md`) and the roadmap bullet, removed when archiving.
