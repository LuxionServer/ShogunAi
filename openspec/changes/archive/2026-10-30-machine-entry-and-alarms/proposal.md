# Proposal

## Why

The UX review of the v2 interface found that the machine's resources are hard to reach and alarm more than they should. When no project declares a provider the window has no way into Machine at all, Preferences is only reachable with ⌘, or the menu bar, a missing optional tool (no Android SDK, Docker not running) paints the status bar in the danger color, and the Gradle daemons view shows a column of "state unknown", an unexplained disabled "Stop idle daemons", blank owners, faint Stop buttons and the same build cache sentence once per project.

## What Changes

- The status bar is always shown and always starts with a "Machine" item. It opens a Machine overview listing the desktop turn and every resource that applies, each opening its view; with no provider declared, the overview says how to get one.
- A gear at the bottom of the sidebar, in both the expanded sidebar and the rail, opens the Preferences window.
- A resource whose read failed because its tool is missing or optional (Android SDK not found, Docker not available, iOS simulators unsupported on this machine) shows in the neutral colors with the reason in its tooltip. The danger color is kept for real failures.
- The Gradle daemons view:
  - Drops the state column when no daemon has a known state.
  - Says next to "Stop idle daemons" why it's disabled, with the counts (e.g. "All 9 are busy, none is idle").
  - Shows the owner's worktree when it's known and a dimmed "unknown" otherwise.
  - Draws each row's Stop as a ghost button.
  - Groups the build cache notices of several projects into one notice with their names under "Details".
- Out of scope: the single source of the Gradle daemon count (the ux-quick-fixes change) and moving the tour notice to the sidebar footer (same change), which will sit next to the gear.

## Capabilities

### New Capabilities

- `machine-overview`: the Machine overview opened from the status bar's "Machine" item, listing the machine's resources and what to do when none applies.

### Modified Capabilities

- `machine-status-bar`: the bar is always shown and starts with "Machine"; missing or optional tools use the neutral colors.
- `machine-resources`: the Gradle daemons view's state column, the reason "Stop idle daemons" is disabled, the unknown owner, the ghost Stop buttons and the grouped build cache notice.
- `app-shell`: a gear at the bottom of the sidebar opens Preferences.

## Impact

- Desktop app only: `ui/shell` (status bar, sidebar, shell destination and content), `ui/machine` (status items, a new overview view, the Gradle daemons view), `ui/i18n` (English and Spanish strings) and their tests.
- No change to `core`, the CLI or the persisted settings beyond the new destination's storage key.
- Expected conflicts with the parallel ux-quick-fixes change in the sidebar footer and the status bar.
