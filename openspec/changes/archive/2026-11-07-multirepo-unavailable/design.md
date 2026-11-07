# Design

## Context

`MultirepoSource.forgetMissing()` (added by the archived `multirepo-naming-and-order` change) removes, under the registry's lock, every entry of `~/.shogunai/multirepos.json` whose parent folder doesn't exist, with its `local` block (approval digest and local agent). `ShellViewModel.loadMultirepos()` calls it through `AppContainer.forgetMissingMultirepos()` on start and on every reload, and `ShogunCli.initMultirepo` at the start of `shogun multirepo init`. That design accepted forgetting multirepos on unmounted volumes; in practice it loses approvals and agents every time an external drive is out. `ResolveMultirepoUseCase.known()` already skips, without error, the entries whose declaration is missing or invalid, and `CheckMultireposUseCase` (the installation check) already reports them as problems.

## Goals / Non-Goals

**Goals:**
- No code path removes a registry entry unless the user asks for it (`multirepo delete`, `trust --revoke`, the app's "Remove").
- The app shows every known entry whose declaration can't be read, with the reason, and lets the user remove it or try again.

**Non-Goals:**
- A CLI command to list or forget an entry whose declaration can't be read (`trust --revoke` and `delete` need the declaration). Left in the roadmap.
- Telling an unmounted volume from a deleted folder. Both are "the folder can't be reached"; the user decides.
- Carrying the approval or local agent over to a moved folder (already in the roadmap).

## Decisions

### Remove `forgetMissing` instead of making it smarter

The peer's minimum was "don't prune when the volume root or an ancestor is missing". Any heuristic still forgets in some case the user didn't ask for (a renamed folder on a mounted volume that comes back with a `git mv`, a network share whose mount point exists but is empty). Since stale entries are harmless (every reader already ignores an unreadable declaration) and now visible and removable in the app, the port method, its JSON implementation, the fake and their tests go away. The two callers stop calling it. This also removes the only automatic write of the registry from a reload.

### `ResolveMultirepoUseCase.listing()` splits the known multirepos

- `KnownMultirepos(available: List<Multirepo>, unavailable: List<UnavailableMultirepo>)` in `domain.model`, with `UnavailableMultirepo(folder, reason)` and `reason` a sealed `MultirepoUnavailability`: `FolderUnreachable`, `DeclarationMissing`, `InvalidDeclaration(detail)`. `UnavailableMultirepo.name` is the folder's last segment, since the declared name can't be read.
- One pass over `source.knownFolders()`: `read` returning a declaration → `build` into `available`; `read` throwing `WorktreeError.InvalidMultirepo` (or any other exception from reading) → `InvalidDeclaration(message)`; `read` returning null → `DeclarationMissing` when `fileManager.exists(folder)`, otherwise `FolderUnreachable`.
- `known()` becomes `listing().available`, so its callers (CLI, conflict checks, workspace registration) keep their behavior.

### Desktop: the catalog and the view model carry the unavailable entries

- `MultirepoCatalog` takes `() -> KnownMultirepos` and exposes `multirepos` and `unavailable` (sorted by name); a failure reading the registry keeps today's empty fallback for both.
- `ShellViewModel` drops `forgetMissingMultirepos` and takes `forgetMultirepo: (String) -> Unit` (`AppContainer` → `multirepoSource.forget`). Removal is view-model state so it's unit-testable: `requestMultirepoRemoval(folder)` sets `multirepoPendingRemoval`, `cancelMultirepoRemoval()` clears it and `confirmMultirepoRemoval()` forgets the folder, clears its collapsed state, refreshes and falls back to the active worktrees when the destination referenced it.
- `isAvailable(MultirepoView(folder))` is true when the folder is available or unavailable, so an app opened with the volume out lands on the unavailable view instead of losing the saved destination. `MultirepoSettings` of an unavailable folder isn't available.
- The sidebar keeps a single folder entry type: `SidebarMultirepo` gains `unavailable: MultirepoUnavailability?`, localized by the sidebar. Unavailable entries use the same `multirepo:<folder>` key, so the saved order survives while the volume is out. The row shows a "folder off" icon instead of the disclosure toggle, the name in the muted color and a tooltip with the reason; its context menu offers "Remove" and the reordering items. In the rail it's a folder-off item with the name as tooltip.
- `ShellContent` renders `MultirepoView` of an unavailable folder with an `UnavailableMultirepoView`: the folder path in the data font, the reason, and "Try again" (`viewModel.refresh()`) and "Remove" (opens the confirmation). The confirmation reuses `ShogunDialog` with `DangerButton`, like "Remove project".
- Strings (English and Spanish): the three reasons, the view's title and actions, and the confirmation's title and message saying the folder's files aren't touched and that the approval and local agent are forgotten.

### Coverage

No new `@Serializable` class, so `core`'s missed-branch maximum is unchanged. Removing `forgetMissing` removes lines and tests in `core`; the new `listing()` branches are covered by `ResolveMultirepoUseCaseTest`. The desktop app's line coverage went from 88.92% on `develop` to 89.04%, so its line floor rises from 88 to 89; branches went from 75.21% to 75.41% and their floor of 74 stays.

## Risks / Trade-offs

- [Entries of folders deleted on purpose stay until removed] → they're listed as unavailable with "Remove"; nothing else reads them.
- [`shogun multirepo init` after moving a folder leaves the old entry] → it's ignored by every command and shown as unavailable in the app; a CLI `forget` is a roadmap follow-up.
- [The projects of an unavailable multirepo show at the top level while it's out] → membership needs the declaration; they go back under the folder when it's readable again.
- [`fileManager.exists` on a hung network mount can block] → it was already called on the same path by `forgetMissing` and by `build`; no new I/O pattern.
