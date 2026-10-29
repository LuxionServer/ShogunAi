# Proposal

## Why

Each worktree action is written by hand in five places (the detail, the row's context menu, the command palette, the tray submenu and the Worktree menu), and they have drifted apart. Opening is modelled as two parallel enums (`LaunchKind`, `OpenKind`) plus one agent picker per launch action, so every runner or agent added multiplies the controls: with Orca and two agents the detail shows 9 buttons and 4 agent selectors, and the context menu grows one entry per runner × agent. Design system v2 (section 03, "Action model: one catalog, five surfaces") fixes this structurally, and Ciro chose to drop "Copy launch command" as the detail's primary action in favour of "Open in <default runner>".

## What Changes

- A new **action catalog** in the desktop app: every action is declared once with an id, a scope (app, project, multirepo, worktree, machine), a group (open, copy, provision, resources, manage, destructive), label, icon, shortcut, availability (available, hidden when its tool isn't installed, or disabled with a reason), whether it is agent-aware, and the confirmation it needs. The model is plain Kotlin, testable without Compose. It covers every worktree action that exists today, the view header's project actions and the multirepo actions that exist today ("Open all in Herdr", "Open multirepo agent in Herdr").
- A **"Default runner"** preference (Herdr or Orca, only the installed ones), stored in the app settings. A settings file without it keeps today's behaviour (Herdr). Herdr and Orca installation is detected with the other tools.
- New shared components: **`SplitButton`** ("Open in <default runner>" plus ▾), **`ShogunMenu`** (an own popup with sections, submenus, shortcuts, disabled items with their reason, keyboard navigation and elevation level 2) and **`ActionBar`** with fixed slots: one primary, up to three icons, then ⋯.
- **Worktree detail**: the open/copy/delete buttons and the four agent pickers become one `ActionBar`. The primary is the SplitButton; the first icon is "Copy launch command" (⇧⌘C). The agent is picked once, in the "With agent" section of the "Open with…" menu, and each agent-aware runner offers a "with agent ▸" submenu. Destructive actions go last in ⋯, in danger text. **BREAKING** (UI): the detail's "Agent" dropdown and per-action agent arrows are removed.
- **Every surface is built from the catalog**: the row's context menu (moved to its own file, now a `ShogunMenu` with submenus), the command palette (which also finds "Open in Orca with Codex"), the tray submenu of a recent worktree (default runner, then "Open with ▸") and the Worktree menu of the menu bar.
- **Shortcuts**: ⌘O opens the selection in the default runner and ⇧⌘O opens "Open with…". ⌘E stays as an alias of ⌘O for one release. **BREAKING**: ⌘E no longer means Herdr when the default runner is Orca.
- **View header**: "Clean up" and "Open multirepo agent in Herdr" move to the view's ⋯ menu; the "Merged" group keeps offering "Clean up" as a group action.
- Inherited from `ui-v2-tokens` (task 7.2): the delete and Clean up dialogs' buttons are checked with the new button sizes.
- Docs: `docs/architecture/ui.md` (the catalog and the action norms of design v2), `docs/guide/desktop-app.md` and their Spanish translations.

## Capabilities

### New Capabilities
- `action-catalog`: the single declaration of the app's actions, their availability rules and how every surface (action bar, menus, palette, tray, menu bar) is derived from it, plus the shared menu and action bar behaviour.
- `default-runner`: choosing which installed runner (Herdr or Orca) gets the primary open action and ⌘O, its persistence and migration.

### Modified Capabilities
- `worktree-detail`: "Detail actions", "Row quick actions" and "Keyboard shortcuts on the selected worktree" change to the action bar, the catalog-built context menu and the new shortcuts.
- `keyboard-shortcuts`: "Open the selected worktree in Herdr with a shortcut" becomes opening in the default runner (⌘O, ⌘E alias) and "Open with…" (⇧⌘O).
- `worktree-terminal-launch`: "Choose the agent for a launch" and "Set the agent of each worktree" move the agent choice to the "Open with…" menu.
- `command-palette`: "What the palette searches" adds the catalog's open actions for the selected worktree, with agent variants.
- `tray-menu`: "Actions on a recent worktree" offers the default runner and "Open with ▸".
- `worktree-cleanup`: "Confirm before cleaning up" moves the view's "Clean up" to the view's ⋯ menu.
- `multirepo-desktop`: "Open the multirepo agent from the app" moves the action to the view's ⋯ menu.
- `herdr-integration`, `orca-integration`: the "Open in Herdr/Orca from the desktop app" requirements are offered through the catalog (default runner or "Open with…") and hidden when the tool isn't installed.

## Impact

- `desktopApp`: new package `ui/actions` (catalog, availability, surfaces), new components `SplitButton`, `ShogunMenu`, `ActionBar`; changes in `WorktreeDetailPane.kt` (actions block only), `WorktreeRow.kt` (context menu extracted to `WorktreeContextMenu.kt`), `WorktreeScreen.kt` (header), `ProjectWorktrees.kt`, `WorktreeBoard.kt`, `palette/**`, `tray/**`, `shell/AppMenuBar.kt`, `shell/PreferencesWindow.kt`, `shell/ToolsPreferences.kt`, `main.kt`, i18n. `OpenActions.kt` and `AgentPicker.kt` are replaced by the catalog.
- `core`: `DetectedTools` gains Herdr and Orca, `LocalToolDetector` detects them, `ToolPreferenceRepository` and `JsonSettingsFile` gain the default runner (with tests).
- Coexists with `ui-v2-inspector-and-rows`, which owns the detail body and the row grid; this change only replaces the detail's action block and the row's context menu, and merges first.
- `orca-multirepos` (planned, not started) will add Orca multirepo actions; they enter through the catalog in a follow-up task.
