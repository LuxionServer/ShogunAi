# Design

## Context

Opening and launching are spread over `OpenKind {TERMINAL, EDITOR, CLAUDE_DESKTOP, FOLDER}` (`OpenActions.kt`), `LaunchKind {COPY, TERMINAL, HERDR, ORCA}` with `AgentMenuButton` (`AgentPicker.kt`), and per-surface code: the detail (`WorktreeDetailPane.kt`), the row's `ContextMenuArea` (`WorktreeRow.kt`), `PaletteIndex.kt` (`OpenSelected`, `LaunchSelected`), `TrayMenuModel.kt` (`TrayOpenAction`) and `AppMenuBar.kt` (⌘E = Herdr). Herdr and Orca are always offered and fail with `HerdrNotInstalled`/`OrcaNotInstalled` when missing; only terminals, IDEs and Claude Desktop are detected (`DetectedTools`). Compose Desktop's `ContextMenuArea` has no submenus, and Material's `DropdownMenu` neither.

The tokens and components of `ui-v2-tokens` and `ui-v2-forms-states` are reused: `ShogunTheme.elevation.level2`, `ButtonSize`, `PrimaryButton`/`SecondaryButton`/`GhostButton`, `IconAction`/`WithTooltip`, `DisabledReason`, `InlineConfirm`, `focusRing`, `Spacing`.

Design v2 norms applied here: "Every new action goes into the catalog, never into a screen", "One primary action per view, in ink", "Placement ladder of an action", "Destructive never goes in a bar", "How a new runner is presented", "How a new agent is presented", "Texts are written for the person using the app" and "Accessibility is checked by tests" (icon-only controls have a label and tooltip, menu and ActionBar are keyboard navigable).

## Goals / Non-Goals

**Goals:**
- One declaration per action; every surface derives its entries, labels, shortcuts and availability from it.
- Adding a runner or an agent adds one entry to "Open with…" (and one option in "Default runner" for a runner), never a button or a picker.
- The model (catalog, availability, menu structure per surface) is unit-tested without Compose.

**Non-Goals:**
- The detail's body (header with branch/origin/path, sections, `PropertyList`, `AgentIndicator`) and the row grid: `ui-v2-inspector-and-rows`.
- Project group headers in the list keep their buttons; the "Merged" group's "Clean up" already exists and is kept.
- Orca multirepo actions: `orca-multirepos` adds them; a follow-up task routes them through the catalog.
- Machine-scope actions are declared as a scope value but no machine action is migrated in this change.

## Decisions

### D1. The catalog is plain data in `ui/actions`
`ActionSpec(id, scope, group, label, icon, shortcut, availability, agentAware, confirm)` with:
- `ActionId`: a sealed interface replacing `OpenKind` and `LaunchKind`: `Open(target: OpenTarget)` with `OpenTarget {HERDR, ORCA, TERMINAL, EDITOR, CLAUDE_DESKTOP, FOLDER}`, `CopyLaunchCommand`, `CopyPath`, `RemoveWorktree`, `SetDefaultRunner`, `NewWorktree`, `NewChange`, `ProjectSettings`, `Reload`, `CleanUp`, `OpenMultirepoAgent`, `OpenAllInHerdr`. Each has a stable `key` (`worktree.open.runner(orca)`) used by the palette's recent items.
- `ActionScope {APP, PROJECT, MULTIREPO, WORKTREE, MACHINE}`, `ActionGroup {OPEN, COPY, PROVISION, RESOURCES, MANAGE, DESTRUCTIVE}` (declaration order is menu order).
- `Availability`: `Available`, `Hidden`, `Disabled(reason: String)`.
- `ActionIcon` enum, mapped to `ImageVector` only in the Compose layer, so the model has no Compose dependency.
- `ConfirmKind {REMOVE_WORKTREE, CLEAN_UP}` names the dialog an action opens before acting.

Builders per scope take a small context instead of the view model: `worktreeActions(WorktreeActionContext, Strings)`, where the context carries the worktree flags (main, folder missing), the detected tools, the effective default runner, the agents and the in-flight openings. `ProjectWorktrees.perform(worktree, id, agent)` executes an id, replacing `open(kind)` and `launch(kind, agent)`.

*Alternative*: one composable per surface reading the view model directly (today's approach) — rejected, it is exactly the drift the change removes.

### D2. Availability rules
- **Hidden**: the tool isn't installed (Herdr, Orca, no terminal, no IDE, no Claude Desktop), or the action can never apply in that context (delete on the main worktree).
- **Disabled with reason**: the worktree's folder is missing (open actions: "Folder missing"), the worktree is already being opened in that runner ("Opening…"), "Clean up" blocked by `CleanupBlock`.
- Surfaces: the action bar and menus show disabled items with their reason as tooltip (`DisabledReason`); the palette and the tray omit disabled and hidden actions; the menu bar shows disabled items greyed.
- Until the first tool detection finishes, Herdr and Orca count as installed, so nothing flickers away on startup and behaviour matches today's.

### D3. Default runner
`RunnerId {HERDR, ORCA}` in `core` (`domain/model/Tools.kt`), `DetectedTools(herdr, orca)` detected by `LocalToolDetector` (Herdr's and Orca's executable lookup, reused from their clients as injected lambdas), and `ToolPreferenceRepository.loadRunner()/saveRunner()` stored as `runner` in `settings.json`. The effective runner is the saved one if installed, else the first installed in `RunnerId` order; a file without `runner` resolves to Herdr, today's behaviour. If no runner is installed the primary action falls back to the first available of terminal, IDE, folder. Preferences' tools section gets a "Default runner" selector with the installed runners only.

### D4. `ShogunMenu`: own popup instead of `DropdownMenu`/`ContextMenuArea`
Built on `Popup` (focusable) with a `MenuEntry` model: `Item(label, shortcut, enabled/reason, danger, checked, onClick)`, `Submenu(label, entries)`, `Section(title)`, `Separator`. Keyboard: ↑/↓ move skipping separators, sections and disabled items' activation; → or Enter on a submenu opens it with its first item focused; ← or Escape closes a submenu; Escape closes the menu; Enter activates. Elevation level 2, `ControlShape`, `Spacing` paddings, focus with the accent. Submenus open on hover too. The row's context menu opens it at the pointer on a secondary click, which replaces `ContextMenuArea` in the row (the sidebar's menus have no submenus and stay as they are).

*Alternative*: nested Material `DropdownMenu`s — no keyboard model for submenus and the Material look v2 drops.

### D5. `ActionBar` and `SplitButton`
`ActionBar(primary, icons, overflow)` lays out fixed slots: primary first, then at most three `IconAction`s (a fourth is a programming error: `require`), a spacer, and a ⋯ `IconAction` opening a `ShogunMenu`. `SplitButton` is a `PrimaryButton` main part plus a ▾ part with its own label ("Open with…") that opens a `ShogunMenu`; both parts are separate focus targets. Hidden actions don't take a slot.

### D6. What goes where in the detail (placement ladder)
- Primary: SplitButton "Open in <default runner>"; ▾ opens "Open with…".
- Icons: "Copy launch command" (⇧⌘C, confirmed in place with `InlineConfirm`), "Open in <terminal>", "Open folder" — frequent, recognisable icons, matching the mockup's three icons.
- "Open with…": every available open action (default runner first, marked "default · ⌘O"), each agent-aware one with a "with agent ▸" submenu when the project has several agents, "Copy launch command" (⇧⌘C), "Set default runner…" (opens Preferences), and a "With agent" section with the project's agents, the worktree's one checked, which sets it without launching.
- ⋯: "Copy path", then a separator and "Delete worktree…" in danger text (never on the main worktree).
- The detail's "Agent" dropdown and the four per-action agent arrows are removed. `ui-v2-inspector-and-rows` shows the agent in its Agent section.

### D7. Surfaces
- **Row context menu** (`worktree/WorktreeContextMenu.kt`): the default runner, "Open with ▸" (same entries as the detail's menu), "Copy launch command", "Copy path", separator, "Delete worktree…". `WorktreeRow` wraps its row in one call to it, so the other change can rewrite the grid around it.
- **Palette**: for the selected worktree, every available non-destructive worktree action, and for agent-aware ones in a project with several agents one item per other agent ("Open in Orca with Codex", "Copy launch command with Codex"). The palette keeps its "No destructive actions" requirement, a deliberate deviation from "the palette receives everything".
- **Tray**: "Show in ShogunAi", "Open in <default runner>", "Open with ▸" (the other available open actions, without agent submenus: a native tray menu nested three levels is hard to use, and the worktree's agent applies), "Copy launch command".
- **Menu bar** (Worktree menu): "Open in <default runner>" ⌘O, "Open with…" ⇧⌘O, "Copy launch command" ⇧⌘C, separator, "Delete worktree" ⌘⌫, "Clear selection". Labels and enabling come from the catalog.
- **View header**: title, progress, Reload, settings icon, ⋯ menu, primary ("New worktree" or "New change"). ⋯ holds "Open multirepo agent in Herdr" in a multirepo view and, last after a separator, "Clean up" in danger text with its disabled reason.

### D8. Shortcuts
⌘O and ⇧⌘O are unused today (checked against `AppMenuBar.kt`, `KeyboardShortcuts.kt` and the window's `onPreviewKeyEvent`). ⌘O is a menu-bar item shortcut. ⇧⌘O asks the board to open the selected worktree's "Open with…" menu (a request counter observed by the detail's SplitButton). ⌘E is handled in the window's `onPreviewKeyEvent` as an alias of ⌘O, not shown in the menu, and is marked for removal in the roadmap. All of them respect the existing "text field focused / dialog open" guard.

### D9. Coexistence with `ui-v2-inspector-and-rows`
In `WorktreeDetailPane.kt` only the block from the agent dropdown to the delete button is replaced by one `WorktreeActionBar(...)` call in the same place. In `WorktreeRow.kt` only the `ContextMenuArea` wrapper changes, into one `WorktreeContextMenu(...)` call; the row's parameters for actions collapse into an `actions` model. This change merges first.

## Risks / Trade-offs

- [Popup focus and keyboard on Compose Desktop can be finicky] → UI tests drive the menu with key events; validated in the real app on both themes.
- [⇧⌘O needs the detail on screen] → the detail is shown whenever a worktree is selected (or overlaid on narrow windows); with nothing selected the item is disabled.
- [Detecting Herdr/Orca costs file lookups] → runs with the existing tool detection on IO, once per refresh.
- [Users used to ⌘E opening Herdr with Orca as default] → ⌘E follows the default runner, which is Herdr unless changed; documented in the guide.
- [Removing the detail's agent dropdown before the inspector shows the agent] → the agent stays visible and changeable in "Open with… › With agent" and named in the SplitButton's tooltip.

## Migration Plan

`settings.json` without `runner` behaves as Herdr; the key is written only when the user picks a runner. Rolling back ignores the unknown key. No data migration.
