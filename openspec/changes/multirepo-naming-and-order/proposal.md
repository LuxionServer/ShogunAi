# Proposal

## Why

Multirepos are named after their folder, so a workspace repository whose folder isn't named after the product shows up with the wrong name in the sidebar, `shogun multirepo show`, the agents' context and Herdr. A multirepo whose folder moved stays registered at the old path, and `shogun multirepo init` in the new folder refuses because the declaration already exists, so the registry has to be edited by hand. And the sidebar always sorts projects and multirepos alphabetically, which doesn't let people keep the ones they work on every day at the top.

## What Changes

- A multirepo can declare a display name: an optional `name` in `.shogunai-multirepo.json`. When it's set, ShogunAi names the multirepo with it everywhere it named it after its folder (sidebar, multirepo view, `shogun multirepo show`, the multirepo context printed to agents, the Herdr workspace); without it, the folder's name as today.
- The name is set with `shogun multirepo init --name <name>`, `shogun multirepo name <name>` (or `--clear`) and a "Name" field in the multirepo settings of the app; `shogun multirepo show` prints it and its JSON gains `name`.
- `shogun multirepo init` in a folder that already has a declaration registers that declaration instead of refusing, as long as it's valid: it adds it to the known multirepos and registers the declared repositories that have no project, as `add-repo` does. Adding a multirepo from the app with a folder that already has a declaration does the same, showing the declared repositories.
- `shogun multirepo init` and the app drop from `~/.shogunai/multirepos.json` the entries whose folder no longer exists.
- The sidebar's top-level entries (projects and multirepos) and the projects inside each multirepo can be reordered by dragging them within their level, or with "Move up" and "Move down" in their context menu, also in the compact sidebar. The order persists across restarts. Without a manual order everything stays alphabetical; once there is one, new projects and multirepos appear at the end of their level, and "Sort alphabetically" in the context menu goes back to the default order.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `multirepo-groups`: the declaration gains an optional display name; `init` registers an existing declaration and drops registry entries whose folder is gone; a new `name` subcommand; `show` prints the name.
- `multirepo-desktop`: the sidebar folder and the multirepo view use the display name; the settings edit it; adding a multirepo from a folder that already has a declaration registers it.
- `app-shell`: the sidebar lists its entries in the user's order, reorderable by dragging or from the context menu, persisted across restarts.

## Impact

- `core`: `MultirepoDeclaration` (new `name`), `Multirepo.name`, `ConfigureMultirepoUseCase` (init over an existing declaration, set the name), `MultirepoSource`/`JsonMultirepoSource` (drop missing folders), `ShellPreferenceRepository` and `settings.json` (sidebar order).
- `cli`: `multirepo init --name`, `multirepo name`, `show` output and JSON, help.
- `desktopApp`: `Sidebar.kt` (drag and context menu actions), `ShellViewModel` (order), `MultirepoCatalog`, the add-multirepo dialog and the multirepo settings, strings in English and Spanish.
- Older versions ignore the unknown `name` field and the new settings key, so the files stay compatible both ways.
- Docs: the multirepo pages and the roadmap's item 6.
