# Proposal

## Why

`develop` gained two changes while the UI redesign was underway on `epic/ui-redesign-v2`: `orca-multirepos` (#218), which opens multirepo groups and the multirepo agent in Orca, and `merged-against-base` (#217), which compares a worktree with its recorded base and counts it as merged into it. Both touch screens the epic has rebuilt, so merging `develop` into the epic conflicts, and resolving it file by file would bring back the pre-v2 buttons and labels. This change brings `develop` in and routes what it adds through the v2 pieces, so nothing from `develop` is lost and the epic stays mergeable back.

## What Changes

- Merge `origin/develop` into the epic's work branch (a merge, never a rebase), resolving every conflict in favour of the v2 structure.
- "Open all in Orca" and "Open multirepo agent in Orca" enter through the action catalog, next to their Herdr counterparts: the change group's header projects its open actions from the catalog, and the multirepo view's "More" (⋯) menu offers the multirepo agent in Herdr and in Orca. Orca's actions are hidden when Orca isn't installed. `develop`'s separate "Open multirepo agent" dropdown is dropped.
- The v2 "New change" dialog keeps its form components and gains `develop`'s runner selector ("Then open it in": don't open, Herdr, Orca) and the per-repository opening result.
- The worktree's base and the merged-into-base state reach the v2 surfaces: the inspector's Status section compares with the base and names it, and says which branch (base or main branch) the worktree is merged into; the row's Git cell does the same. The test `develop` added to `StatusSegmentsTest` (deleted by the epic) moves to the inspector's tests.
- The epic's agent indicator replaces `develop`'s agent labels wherever both meet.
- Specs, the desktop app guide and the roadmap combine both sides; the roadmap drops the "Orca actions through the catalog" follow-up once done.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `action-catalog`: the catalog declares the Orca multirepo actions, and the view header's "More" menu includes the multirepo agent in Orca; a change group's header takes its open actions from the catalog.
- `multirepo-desktop`: "Open all in Orca" and "Open multirepo agent in Orca" are hidden without Orca, and the multirepo agent actions live in the view's "More" menu.
- `worktree-detail`: the Status section compares with the worktree's base and names the branch it is merged into.

## Impact

- Code: `desktopApp` `ui/actions` (`ActionSpec`, `ViewActionCatalog`), `ui/worktree` (`WorktreeScreen`, `WorktreeListPane`, `WorktreeRowModel`, `WorktreeDetailPane`, `inspector/InspectorModels`, `inspector/StatusSection`), `ui/multirepo/NewChangeDialog`; everything else from `develop` (core, CLI, `WorktreeBoard`, strings) comes in through the merge unchanged.
- Tests: `ActionSurfacesTest`, `InspectorModelsTest`, `WorktreeRowModelTest`, `WorktreeBoardTest`; `StatusSegmentsTest` stays deleted.
- Docs: `docs/guide/desktop-app(.es).md`, `docs/roadmap(.es).md`.
- No core, CLI or settings format change beyond what `develop` already ships.
