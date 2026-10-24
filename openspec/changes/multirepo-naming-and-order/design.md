# Design

## Context

- `Multirepo.name` is derived from the folder and is read by the CLI (`show`, error messages), the agents' context (`GroupContext`), the Herdr workspace label (`OpenGroupInHerdrUseCase`), the sidebar (`MultirepoMembership.name`, `SidebarMultirepo.name`), the multirepo view's title and the add-multirepo dialog (to propose aliases and the default `worktreesRoot`).
- `~/.shogunai/multirepos.json` holds only the declarations' paths (`JsonMultirepoSource`). `ResolveMultirepoUseCase.known()` skips unreadable entries, so a moved multirepo is silently ignored, and `ConfigureMultirepoUseCase.init` refuses a folder that already has a declaration.
- The app's add-multirepo dialog already does `setup.read(folder) ?: setup.init(folder)` on confirmation, but it never registers an existing declaration and it lists the folder's detected repositories, not the declared ones.
- The sidebar (`Sidebar.kt`) is a scrolling `Column`, not a lazy list. `sidebarEntries` sorts the top level and each folder's projects alphabetically; `ShellViewModel.loadProjects` and `MultirepoCatalog.refresh` also sort by name. Rows are fixed-height (`SidebarRowHeight` = 32 dp, rail items 36 dp) with `Spacing.xxs` between them, and each project row already has a `ContextMenuArea`; the multirepo row has one with "Multirepo settings".
- Shell preferences live in `~/.shogunai/settings.json` (`JsonSettingsFile.Settings`), written under a file lock with unknown keys ignored.
- The first-run change, in parallel, touches the main window and Preferences; this change keeps its UI edits inside `Sidebar.kt`, `ShellViewModel` and the multirepo screens.

## Goals / Non-Goals

**Goals:**
- One place decides a multirepo's visible name; every surface uses it.
- `shogun multirepo init` and the app recover a moved multirepo without editing `~/.shogunai/multirepos.json`.
- Reordering by drag and from the context menu, with an order that survives restarts and tolerates projects and multirepos appearing and disappearing.

**Non-Goals:**
- Fixing projects whose `baseRepositoryPath` points to a moved repository (that's the projects' own moved-folder problem); they stay as they are.
- Moving a project into or out of a multirepo by dragging: membership is the declaration's.
- Reordering the views or machine sections, or ordering anything outside the sidebar (the palette, the "All" view's project filter keep their alphabetical order).
- Keyboard shortcuts for moving entries; the context menu is the accessible path.

## Decisions

### Display name

- `MultirepoDeclaration` gains `name: String? = null` (serialized only when set, as `encodeDefaults = false` already does). `Multirepo.name` returns the trimmed `name` when not blank, otherwise a new `Multirepo.folderName`. Callers that need the folder's name for paths or aliases (`init`'s default `../<folder>-worktrees`, `proposedAlias` in the add dialog and the settings) switch to `folderName`, so a display name like `Lux Home` never ends up in a path. Every other caller keeps `name` and gets the display name for free (CLI, `GroupContext`, Herdr, sidebar, view title).
- `ConfigureMultirepoUseCase.setName(multirepo, name?)` trims and stores the name, or removes it for `null`/blank; `init` takes an optional `name`. The CLI adds `multirepo name <name> | --clear` and `init --name`; a blank name is a `UsageException` (exit 2), as other argument errors.
- `multirepoJson` adds `name` first, always present (the resolved name), so a script doesn't need to fall back to the folder itself.
- The settings screen adds a "Name" text field at the top, saved with the other fields through `setup.setName`; its placeholder is the folder's name.
- Alternative considered: a `displayName` key. `name` matches the `agent.name` wording already in the declaration and reads naturally in a hand-written file.

### Registering an existing declaration

- `init` splits on `source.read(folder)`: no declaration keeps today's path; an existing one goes through `register`, which runs `resolver.validate`, checks that no other known multirepo (whose folder exists) declares any of its repositories, calls `source.remember`, and then registers a project for each declared repository with no project at its canonical path, reusing the `addRepository` project logic (extracted into a private helper shared by both) with the same `WorktreesFolderDecision`. The result says whether the multirepo was newly remembered and which projects were registered, so the CLI can print "registered existing multirepo …" or "already registered".
- `--worktrees-root`/`--name` with an existing declaration are rejected before anything is written, pointing to `worktrees-root` and `name`.
- The CLI keeps asking about worktrees folders as `add-repo` does (`--use-worktrees-root`/`--keep-worktrees-folder` are accepted by `init` too, and `--json` without either declines).
- The app: `MultirepoSetup.inspect` reports the folder's existing declaration (or its validation error). `AddMultirepoState` then shows the declared repositories read-only (alias, path, already a project) and the proposed folder toggles for the ones without a project; confirming calls a new `setup.register(folder, useProposedFolder)` and selects the multirepo. When the multirepo is already known, confirming just selects it.

### Forgetting missing folders

- `MultirepoSource.forgetMissing(): List<String>` removes, under the registry's lock, every entry whose parent folder doesn't exist, and returns the removed folders. It's called at the start of `init` (CLI) and from `ShellViewModel.loadMultirepos` before `multirepoCatalog.refresh()`. It isn't wired into `knownFolders()`, so read-only commands like `shogun status` never write the registry.
- Entries whose folder exists but whose declaration is missing or invalid are kept, as the spec says; they stay ignored as today. That keeps a temporarily broken declaration from being forgotten. The trade-off: a multirepo on an unmounted volume is forgotten after `init` or an app reload; re-running `init` there restores it, which is now possible.

### Sidebar order: model and persistence

- Keys: a top-level entry is `project:<id>` or `multirepo:<folder>`; inside a folder, a project's key is its id. Using the project's id survives renames; using the folder path is how the collapsed state already identifies multirepos.
- `Settings` gains `sidebarOrder: SidebarOrder = SidebarOrder()` with `topLevel: List<String>` and `multirepos: Map<String, List<String>>` (folder → project ids). `ShellPreferenceRepository` gains `loadSidebarOrder()`/`saveSidebarOrder(order)`.
- A pure function `orderedBy(saved: List<String>, keyOf, entries)` places the entries with a saved key in saved order, followed by the rest alphabetically. `sidebarEntries(projects, multirepos, order)` uses it for the top level and for each folder. Unknown keys are ignored when reading and dropped on the next save, so removed projects don't accumulate and a re-added project (new id) lands at the end.
- Operations live in `ShellViewModel`: `moveSidebarEntry(level, key, toIndex)`, `moveSidebarEntryBy(level, key, delta)` and `sortSidebarAlphabetically(level)`, where `level` is `TopLevel` or `Folder(folder)`. Each computes the visible order of the level, applies the move, saves the full list of the level (so every current entry gets a saved place, which is what makes new ones go to the end) or, for sorting, removes the level's saved list.
- `ShellViewModel.projects` and `MultirepoCatalog.multirepos` keep their alphabetical order: they feed other views; only `sidebarEntries` applies the manual order.

### Sidebar order: interaction

- Drag: each reorderable row gets a `Modifier.reorderable(...)` built on `pointerInput` + `detectDragGestures` (available on Compose Desktop; no new dependency). While dragging, the row is drawn translated by the pointer's offset with a raised background, the other rows of the level stay put, and a 2 dp insertion line shows where it will drop; on release the target index is computed from the drag offset and the level's fixed row pitch (row height + `Spacing.xxs`, or the rail item size), clamped to the level's bounds. That's what makes "dropped outside its level" land at the nearest place of its level.
- The level is what matters for the pitch: a top-level drag skips over expanded folders, so the pitch calculation uses the measured heights of the level's entries (collected with `onGloballyPositioned`) rather than a constant, which also covers expanded folders and the rail.
- Drag starts after the platform touch slop, so a click still selects; `interactive(onClick)` stays on the row.
- Context menu: project and multirepo menus get "Move up", "Move down" (omitted at the edges) and "Sort alphabetically", after the existing items. The multirepo menu keeps "Multirepo settings" first. Projects inside folders get them for their folder's level.
- Why both: dragging is what people expect in a sidebar; the context menu is the keyboard- and screen-reader-reachable path and the reliable one in the compact rail, where targets are small.
- Alternative considered: a lazy reorderable list library. The sidebar isn't lazy and has few entries; a hand-written modifier avoids a dependency and keeps the edit inside `Sidebar.kt`.

## Risks / Trade-offs

- [Drag gestures conflicting with the row's click or context menu] → drag only after touch slop, right-click left to `ContextMenuArea`; verified in the real app.
- [The first-run change also edits `Settings`/`ShellViewModel`] → additions only (one field, a few functions); a conflict, if any, is a trivial merge.
- [A display name that collides with another multirepo's or project's name] → allowed; entries are identified by folder and id, not by name.
- [Forgetting a multirepo on an unmounted volume] → limited to `init` and app reloads, recoverable with `init` (documented in the multirepo page).
- [Registering projects for a moved multirepo leaves the old projects with dead paths] → out of scope; they're visible in the sidebar and removable as today.
