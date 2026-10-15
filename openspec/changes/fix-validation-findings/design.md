## Context

`WorktreeViewModel` starts loading the worktree list in `init`, and `AppRoot` then calls either `refresh()` or, right after saving a moved project, `repairAfterMove(previousWorktreesRoot)`. Both paths end in `ListWorktreesUseCase`, whose success branch sets `error = null`. Use cases wrap everything in `runCatching`, so cancelling an in-flight load would surface a `CancellationException` as an error.

The domain throws `CommandExecutionException` (from the `ShellCommandExecutor` port) when a process can't start; it isn't a `WorktreeError`, so `Strings.worktreeError` falls back to `unexpectedError(error.message)`.

The manual validation drove the app through macOS accessibility (`System Events`), which performs the element's press action. Repeating the same click as a real mouse event (CoreGraphics) left the disabled option unselected, so the defect only affects assistive-technology input.

## Decisions

- **Load generations instead of cancellation.** Each list load takes a generation number; its result is applied only if no newer load started meanwhile. Cancellation is avoided because `runCatching` in the use cases would turn it into a visible error. `repairAfterMove` runs the repair, then the list, and applies the repair outcome last, so a repair failure is what the user sees.
- **Localize process start failures in the UI, keep the exception in the domain.** `Strings.worktreeError` gets a dedicated branch for `CommandExecutionException` with a new `commandCouldNotStart(command)` text. Making it a `WorktreeError` variant was rejected: it's raised by the executor port, not by the worktree flow, and other callers may run commands too.
- **Guard disabled options in two places.** `DropdownSelector` ignores `onClick` for an option whose `enabled(option)` is false (defence against any input path that bypasses Compose's own check), and `WorktreeViewModel` exposes whether the selected branch is available so "Create worktree" stays disabled even if state is set by other means.
- **`Modifier.width(IntrinsicSize.Max)` on segmented labels.** `SingleChoiceSegmentedButtonRow` is `width(IntrinsicSize.Min)` with equally weighted segments, and `SegmentedButton`'s content layout has no intrinsic overrides, so the label's minimum intrinsic width (its longest word) decides the segment width. The modifier reports the label's full width as its minimum intrinsic width while still respecting incoming constraints, so a genuinely narrow window keeps the ellipsis. `softWrap = false` was tried first and doesn't help: a text's minimum intrinsic width ignores `softWrap`.
- **`inputs.property("appVersion", …)` on `generateBuildInfo`.** Gradle reruns the task whenever the version changes; the configuration cache already treats the property as an input.
- **Docs describe the code as it is.** Removed flows are dropped from the architecture pages instead of being kept with a "removed" note; history stays in `openspec/changes/archive/` and `docs/decisions/` (read-only).

## Risks / Trade-offs

- A window-focus reload that completes after a failed repair still clears the repair error, consistent with how every successful reload clears stale errors.
- No Compose UI test infrastructure exists, so the selector guard and the label sizing are covered by a manual check rather than automated tests.
