## Why

The "Existing branch" dropdown in the worktree creation flow lists every local branch flat, alphabetically. As a repository accumulates branches across `feature/`, `fix/`, `release/`, and other prefixes, the list becomes hard to scan. Local branches already follow a `<domain>/<name>` naming convention, so that convention can be reused to group the dropdown visually without asking the user to learn a new taxonomy.

## What Changes

- `DropdownSelector` gains an optional `groupBy: (T) -> String? = { null }` parameter. When provided, entries are rendered under clickable header rows for each distinct group value, in order of first appearance; clicking a header collapses/expands the options under it. When `groupBy` is omitted, rendering is unchanged.
- The "Existing branch" dropdown in `WorktreeScreen` passes `groupBy = { it.name.substringBefore('/', "otras") }`, grouping branches like `feature/x` under "feature" and bare branches like `develop` under "otras".
- `ProjectConfigScreen`'s use of `DropdownSelector` (terminal emulator picker) is unaffected, since it doesn't pass `groupBy`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `worktree-workspace`: the "List local branches eligible for a new worktree" requirement gains a scenario describing branches grouped by domain in the dropdown.

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/components/Selector.kt` (`DropdownSelector`)
- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/worktree/WorktreeScreen.kt`
- `openspec/specs/worktree-workspace/spec.md`
