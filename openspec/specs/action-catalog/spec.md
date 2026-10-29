# action-catalog Specification

## Purpose
A single declaration of the desktop app's actions and the rules that derive every surface (action bar, menus, command palette, tray and menu bar) from it, so an action, runner or agent added once appears consistently everywhere.

## Requirements

### Requirement: Every action is declared once
The desktop app SHALL declare each action once with an identifier, a scope (app, project, multirepo, worktree or machine), a group (open, copy, provision, resources, manage or destructive), a label, an icon, an optional shortcut, an availability, whether it is agent-aware, and the confirmation it requires, if any.

#### Scenario: Actions covered
- **WHEN** the catalog is built for a worktree, a project view and a multirepo view
- **THEN** it declares opening the worktree in Herdr, Orca, the terminal, the IDE, Claude Desktop and its folder, copying its path and its launch command, deleting it, "Clean up", "New worktree", "New change", "Project settings", "Reload", "Open all in Herdr" and "Open multirepo agent in Herdr"

#### Scenario: Same label everywhere
- **WHEN** the detail, the row's context menu, the command palette, the tray and the menu bar show "Open in Orca" for the same worktree
- **THEN** all of them take its label, availability and agent choice from the same declaration

#### Scenario: Destructive actions ask first
- **WHEN** the user activates "Delete worktree" from any surface
- **THEN** the delete confirmation opens before anything is removed

### Requirement: Availability of an action
An action SHALL be available, hidden or disabled with a reason. It SHALL be hidden when the tool it needs isn't installed or when it can never apply in that context, and disabled with a reason when it applies but can't run now. A disabled action shown in a menu or bar SHALL explain its reason in a tooltip; the command palette and the tray SHALL omit disabled and hidden actions.

#### Scenario: Tool not installed
- **WHEN** Orca isn't installed
- **THEN** no surface offers "Open in Orca"

#### Scenario: Folder missing
- **WHEN** a worktree's folder is missing and the user opens its "Open with…" menu
- **THEN** its open actions are disabled and their tooltip says the folder is missing

#### Scenario: Main worktree
- **WHEN** the main worktree is selected
- **THEN** no surface offers "Delete worktree"

### Requirement: Agent-aware actions offer a "with agent" submenu
An agent-aware action (opening in Herdr, Orca or the terminal, and copying the launch command) SHALL, when the worktree's project has several agents, offer a "with agent" submenu listing the project's agents in every menu that shows it, and a palette item per agent other than the worktree's. Launching with an agent SHALL also make it the worktree's agent. With a single agent, the action SHALL have no submenu.

#### Scenario: Open in Orca with Codex
- **WHEN** a project has Claude (default) and Codex, and the user picks Codex in "Open in Orca › with agent"
- **THEN** the worktree opens in Orca running Codex and Codex becomes the worktree's agent

#### Scenario: Single agent
- **WHEN** the project has only Claude
- **THEN** no menu shows a "with agent" submenu

### Requirement: Menus with sections, submenus and keyboard navigation
The app's action menus SHALL support sections, separators, submenus, shortcuts shown next to their item, checked items and disabled items with their reason, drawn on the floating surface level. They SHALL be usable with the keyboard alone: arrow keys move between items, Right opens a submenu with its first item focused, Left or Escape closes a submenu, Enter activates the focused item (or opens its submenu when the entry isn't an action itself) and Escape closes the menu.

#### Scenario: Navigate into a submenu
- **WHEN** the "Open with…" menu is open, the user moves to "Open in Orca" and presses Right
- **THEN** its "with agent" submenu opens with its first agent focused

#### Scenario: Destructive items last
- **WHEN** a menu offers "Delete worktree…"
- **THEN** it is the last item, after a separator, in danger text

#### Scenario: Close with Escape
- **WHEN** a submenu is open and the user presses Escape twice
- **THEN** the submenu closes, then the menu closes, and nothing runs

### Requirement: Action bar with fixed slots
The worktree's detail SHALL present its actions in an action bar with, in order, one primary action, at most three icon actions, and a "More" (⋯) menu at the end. Every icon-only control in the bar SHALL have an accessible label and a tooltip, and the bar SHALL be reachable with Tab in that order.

#### Scenario: Icon actions explain themselves
- **WHEN** the user hovers or focuses an icon in the action bar
- **THEN** a tooltip names the action and assistive technology reads the same label

### Requirement: The row's context menu comes from the catalog
A worktree row's context menu SHALL offer, in this order, "Open in <default runner>", an "Open with" submenu with the same entries as the detail's "Open with…" menu, "Copy launch command", "Copy path" and, except for the main worktree, "Delete worktree…" after a separator in danger text.

#### Scenario: Context menu of a non-main worktree
- **WHEN** the default runner is Herdr, Orca is installed, and the user opens the context menu of a non-main worktree
- **THEN** it offers "Open in Herdr", "Open with ▸" containing "Open in Orca" among the other tools, "Copy launch command", "Copy path" and "Delete worktree…" last

### Requirement: View header actions
A worktree view's header SHALL keep a single primary action ("New worktree", or "New change" in a multirepo view) and SHALL move its other actions into a "More" (⋯) menu: "Open multirepo agent in Herdr" in a multirepo view and, last after a separator in danger text, "Clean up", disabled with its reason when nothing can be cleaned up.

#### Scenario: Clean up from the header
- **WHEN** "All worktrees" has cleanup candidates and the user opens the header's ⋯ menu
- **THEN** "Clean up" is the last item, in danger text, and opens the cleanup confirmation
