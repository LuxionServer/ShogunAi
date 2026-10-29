# Tasks

## 1. Runners in core

- [x] 1.1 Add `RunnerId {HERDR, ORCA}` and `DetectedTools.herdr/orca`; `LocalToolDetector` detects them through injected lookups (Herdr's and Orca's executable resolution). Verify: `LocalToolDetectorTest` covers installed and missing runners.
- [x] 1.2 Add `loadRunner()/saveRunner()` to `ToolPreferenceRepository`, `runner` to `JsonSettingsFile.Settings`, the JSON repository and the fake. Verify: `JsonSettingsFileTest` covers a file without `runner` (null), saving, reloading and clearing.

## 2. Action catalog model

- [x] 2.1 Create `ui/actions` with `ActionId`, `OpenTarget`, `ActionScope`, `ActionGroup`, `Availability`, `ActionIcon`, `ConfirmKind` and `ActionSpec`. Verify: compiles without warnings.
- [x] 2.2 `ToolsPreferences` exposes installed runners (both until detected), `chosenRunner`, `defaultRunner` (saved if installed, else first installed, else null) and `chooseRunner`. Verify: unit tests for migration (no saved value → Herdr), uninstalled choice fallback and no runner.
- [x] 2.3 Worktree catalog builder: open actions (default runner first, other runner, terminal, IDE, Claude Desktop, folder), copy launch command, copy path, delete; hidden/disabled rules (tool missing, main worktree, folder missing, opening in progress); agent-aware flags; the primary open action with its fallback. Verify: `WorktreeActionCatalogTest`.
- [x] 2.4 View catalog builder (project and multirepo scopes): New worktree / New change, Project settings, Reload, Open multirepo agent in Herdr, Open all in Herdr, Clean up with its `CleanupBlock` reason. Verify: unit tests.
- [x] 2.5 Surface projections from the catalog, all pure: "Open with…" menu entries (with agent submenus and "With agent" section), ⋯ entries, row context menu entries, palette items (incl. "Open in Orca with Codex"), tray submenu entries, Worktree menu bar items. Verify: `ActionSurfacesTest` asserts every surface's entries come from the same catalog (same labels/availability) for a worktree with Herdr, Orca and two agents.
- [x] 2.6 `ProjectWorktrees.perform(worktree, id, agent)` replaces `open(kind)`/`launch(kind, agent)`/`openActions`/`launchChoices`; remove `OpenActions.kt`, `LaunchKind`, `LaunchChoice` and `AgentMenuButton`. Verify: `ProjectWorktreesTest` updated and green.

## 3. Components

- [x] 3.1 `ShogunMenu` (popup, `MenuEntry` model, sections, separators, submenus, shortcuts, checked, disabled with reason, danger, elevation level 2, keyboard navigation). Verify: `ShogunMenuUiTest` (keyboard into/out of submenu, Escape, disabled item doesn't run, reason in semantics).
- [x] 3.2 `SplitButton` and `ActionBar` (primary, ≤3 icons, ⋯). Verify: `ActionBarUiTest` (every icon-only control has a content description and tooltip label, order, the ▾ part opens its menu).

## 4. Surfaces

- [x] 4.1 Detail: replace the agent dropdown → delete block in `WorktreeDetailPane.kt` with one `WorktreeActionBar` call (new file), including the ⇧⌘O request. Verify: `WorktreeDetailActionsUiTest`.
- [x] 4.2 Row: extract the context menu into `WorktreeContextMenu.kt` (ShogunMenu at pointer on secondary click), `WorktreeRow` wraps it in one call; update `WorktreeListPane.kt` callers. Verify: `WorktreeRowUiTest` updated.
- [x] 4.3 Palette from the catalog (`PaletteIndex.kt`, `PaletteItem.kt`, `AppShell.kt` runner). Verify: `PaletteIndexTest` updated (agent variants, missing tool, no destructive).
- [x] 4.4 Tray from the catalog (`TrayMenuModel.kt`, `WorktreeBoard.trayWorktrees`, `main.kt`). Verify: `TrayMenuModelTest` updated.
- [x] 4.5 Menu bar and shortcuts: Worktree menu from the catalog with ⌘O, ⇧⌘O, ⇧⌘C, ⌘⌫; ⌘E alias in the window's key handler; board `openSelectedInDefaultRunner`/`requestOpenWith`. Verify: `ShellViewModelTest`/`WorktreeBoardTest` updated; a test that the shortcuts in `AppMenuBar` don't collide.
- [x] 4.6 View header: ⋯ menu with "Open multirepo agent in Herdr" and "Clean up" (danger, disabled reason); remove the header's Clean up and multirepo agent buttons. Verify: `MultirepoViewUiTest` and a header UI test.
- [x] 4.7 Preferences: "Default runner" selector (installed runners, "No runner found"). Verify: `ToolsSectionUiTest`.
- [x] 4.8 Tour: update any step that points at removed detail buttons (`TourViews.kt`). Verify: tour tests green.
- [x] 4.9 Check the delete and Clean up dialogs' buttons with the new sizes (inherited task 7.2 of `ui-v2-tokens`). Verify: visual check in the app, both themes.
- [x] 4.10 Strings in `EnglishStrings` and `SpanishStrings`. Verify: i18n parity test green.

## 5. Docs and validation

- [x] 5.1 `docs/architecture/ui.md` (catalog, surfaces, action norms of design v2) and `docs/guide/desktop-app.md`, with `.es.md`. Verify: `mkdocs build --strict` if available, otherwise review.
- [x] 5.2 `./gradlew test` green without warnings.
- [x] 5.3 Validate in the real app with hot reload, light and dark: detail action bar, Open with menu and submenus by keyboard, context menu, palette, tray, menu bar shortcuts, header ⋯, Preferences.

## Workflow follow-up

- After `orca-multirepos`: route "Open all in Orca" and "Open multirepo agent in Orca" through the catalog (recorded in the roadmap at archive time).
- `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` (⌘E alias removal, the orca-multirepos follow-up).
- Implementation commit, then ask Ciro before opening the PR with `--base epic/ui-redesign-v2 --assignee CiroFeliu`.
