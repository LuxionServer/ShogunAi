## Context

The "Existing branch" dropdown (`DropdownSelector` in `WorktreeScreen.kt`) renders `viewModel.localBranches` (`List<BranchOption>`) as a flat, alphabetically-ordered list. Local branches already follow a `<domain>/<name>` naming convention (`feature/`, `fix/`, `release/`, plus bare branches like `develop`/`main`), which `BranchType` only partially models (it covers `feature`/`fix` for *new* branch creation, not the open-ended set of prefixes an *existing* branch can have).

`DropdownSelector` is a generic, shared component: it's also used by `ProjectConfigScreen` to pick a `TerminalEmulator`. Any change to its signature must default to the current flat rendering so that call site is unaffected.

## Goals / Non-Goals

**Goals:**
- Visually group branches by domain (the segment before the first `/`, or "otras" if there isn't one) in the "Existing branch" dropdown, so branches are easier to scan as the branch list grows.
- Let the user collapse/expand a group by clicking its header, so a domain with many branches can be hidden while browsing others.
- Keep `DropdownSelector` generic and backward-compatible for its other call site.

**Non-Goals:**
- No change to `BranchOption`, `ListLocalBranchesUseCase`, or how branches are fetched — grouping is purely a rendering concern.
- No change to `BranchType` or the "New branch" flow.
- No persistence of collapsed/expanded state across dropdown opens or app restarts — it resets to fully expanded each time the composable is recreated.

## Decisions

- **Add `groupBy: (T) -> String? = { null }` to the generic `DropdownSelector`** rather than forking a worktree-specific dropdown, mirroring the precedent set by the `enabled` parameter added for `ListLocalBranchesUseCase`: `ProjectConfigScreen`'s call site doesn't pass it, so its rendering is untouched.
  - **Alternative considered**: a two-step "pick domain, then pick branch" selector. Rejected per explicit user preference — grouping happens inside the existing single dropdown, not as a new interaction pattern.
- **Group value comes from `label`'s underlying data, not the rendered label** — the call site supplies `groupBy` as a separate function (`{ it.name.substringBefore('/', "otras") }`) operating on the branch option itself, not on the already-formatted label string, so grouping logic doesn't depend on label text formatting (e.g. the "(ya tiene un worktree)" suffix).
- **Preserve list order for grouping, don't re-sort into a domain-keyed map** — iterate `options` once, emitting a header whenever the group value changes from the previous item (i.e., groups appear in order of first appearance, options keep their existing relative order within a group). This avoids introducing a new sort order that could put checked-out/disabled branches in a surprising position relative to `viewModel.localBranches`' existing order.
- **Headers are clickable `DropdownMenuItem`s** (not a separate composable type) that toggle a per-group expanded flag held in `remember { mutableStateMapOf<String, Boolean>() }` inside `DropdownSelector`. Clicking a header hides/shows the options belonging to that group without closing the menu or affecting `selected`. A leading `▼`/`▶` glyph in the header text signals state, avoiding a new icon dependency (the project only pulls in `material-icons-core`, which lacks `ExpandMore`/`ChevronRight`).
- Groups start collapsed (no entry in `expandedGroups` means not expanded), per explicit user preference, so a long branch list opens compact and the user opts into expanding the domain they need.
- **Reuse `"otras"` as the fallback group name** (matching `substringBefore('/', "otras")`) instead of `null`/no-group, so every branch — including bare ones like `develop` or `main` — still gets a header, keeping the list's visual structure consistent instead of mixing headed and headerless entries.

## Risks / Trade-offs

- [Grouping by a hardcoded fallback string ("otras") is presentation logic living in `WorktreeScreen.kt` rather than a shared/reusable domain concept] → Acceptable: no other screen currently needs "branch domain" as a first-class concept, and duplicating this one-liner if a second call site needs it later is cheaper than introducing a shared abstraction now.
- [If `localBranches` is not already sorted such that same-domain branches are contiguous, headers could repeat for the same domain] → Mitigation: `git for-each-ref` (used by `ListLocalBranchesUseCase`) returns branches in a stable order where same-prefix branches are naturally adjacent; if this ever changes, the fix is sorting by `groupBy(it)` before rendering, not changing the header-emission logic itself.
