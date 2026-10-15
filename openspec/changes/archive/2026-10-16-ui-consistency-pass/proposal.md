## Why

The desktop app's UI feels cluttered and inconsistent: every "pick one of N" interaction (terminal mode, terminal emulator, create-worktree mode, branch type, eligible branch) is a hand-rolled `RadioButton` group, each laid out differently (vertical, horizontal, nested, as a list). `ProjectConfigScreen` is a single long unsectioned scrolling form, and `WorktreeScreen` mixes a creation form with the worktree list inside one `LazyColumn`. None of this is dictated by the product requirements — it's accumulated implementation inconsistency across screens built at different times.

## What Changes

- Extract reusable `Selector` composables to replace the ad hoc `RadioButton` groups: a segmented-control style for small fixed option sets (`TerminalSelectionMode`, `CreateMode`, `BranchType`), and a dropdown style for longer/dynamic lists (`TerminalEmulator`, eligible branches), following the existing `ThemeModeToggle` dropdown as the working precedent.
- Reorganize `ProjectConfigScreen`'s form into visually grouped sections (identity, paths, secret files, terminal, agent) using `Card`/`OutlinedCard` boundaries instead of one continuous `Column`.
- Split `WorktreeScreen` into a distinct "create worktree" section and worktree list section, instead of interleaving both as items in one `LazyColumn`.
- Move `WorktreeScreen`'s transient form state (`taskId`, `branchType`, `createMode`, `selectedBranch`) from local `remember { mutableStateOf(...) }` in the composable into `WorktreeViewModel`, matching how `ProjectConfigViewModel` and `ProjectListViewModel` already own their screens' state.
- Replace raw emoji `IconButton` glyphs (✎, 🗑, ✕) with Material Icons and `contentDescription`, for visual consistency and accessibility.
- Unify spacing (currently hardcoded 8/16/24dp paddings scattered ad hoc) and typography hierarchy (`headlineSmall`/`titleMedium` usage) across the three screens.

This is an implementation-level consistency pass: no user-facing behavior described by existing specs changes.

## Capabilities

### New Capabilities
- `ui-consistency`: cross-cutting, testable requirements this pass introduces — icon-only actions must expose an accessible label, choice controls must use one of two consistent shapes (segmented selector for small fixed option sets, dropdown selector for longer/dynamic lists), and the worktree creation form must be visually distinct from the worktree list.

### Modified Capabilities
(none — no existing requirement's behavior changes; all existing scenarios in `project-catalog`, `project-configuration`, `worktree-workspace`, and `visual-theme` must keep passing unchanged, in particular: normalized task id shown while typing, reload loading-indicator disabling the "Reload" action, create-mode/branch-type/eligible-branch selection, and the terminal-preference/agent-config fields)

## Impact

- `ui/projectlist/ProjectListScreen.kt`: icon buttons only (Material Icons instead of emoji).
- `ui/projectconfig/ProjectConfigScreen.kt`: sectioned layout, `Selector` for `TerminalSelectionMode`/`TerminalEmulator`, unified spacing.
- `ui/worktree/WorktreeScreen.kt`: split layout, `Selector` for `CreateMode`/`BranchType`/eligible branches, unified spacing.
- `ui/worktree/WorktreeViewModel.kt`: gains ownership of `taskId`, `branchType`, `createMode`, `selectedBranch` state.
- New shared composable(s) under `ui/components/` (e.g. `Selector.kt`) used by `ProjectConfigScreen` and `WorktreeScreen`.
- `docs/architecture/ui.md` / `ui.en.md`: updated to reflect the new shared component and `WorktreeViewModel`'s expanded state ownership.
- No changes to domain models, use cases, or persistence — this is UI-layer only.
- Coordinate with the in-progress `system-theme-mode` change, which also touches `AppRoot.kt`/`ThemeModeToggle`, to avoid conflicting edits.
