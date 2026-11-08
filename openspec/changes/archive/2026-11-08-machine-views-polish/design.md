# Design

## Context

The Gradle daemons view (`GradleDaemonsView.kt`) got its treatment in the archived machine-entry-and-alarms change: a private `DaemonOwnerCell` draws a dimmed "unknown" (`MachineStateText`, test tag `daemon-owner-unknown`) when the owner is `null`; row Stop is a `GhostButton` wrapped in `DisabledReason`, fed by `DaemonStopBlock.describe`; and "Stop idle daemons" writes `stopIdleReason` next to it when the block is `NONE_IDLE`, and gives it as tooltip in every case.

The other views don't: `OwnerCell` in `MachineViewParts.kt` draws an empty text for a `null` owner (only Maven daemons pass one: `MachineMavenDaemon.owner` is `null` when the daemon has no working folder); the Maven, emulator and simulator rows use `SecondaryButton`; and the list actions of the Maven, simulators, DerivedData and Compose views compute `enabled` inline, without any reason. The DerivedData and Compose rows have no action. Emulator and simulator rows are never disabled: while busy they show a progress indicator instead of the button.

The desktop app's lines and branches outside composables have coverage floors, so the reasons are computed in plain functions that unit tests reach.

## Goals / Non-Goals

**Goals:**
- One owner cell and one way to show a disabled list action, shared by every Machine view, so Gradle and the others can't drift apart.

**Non-Goals:**
- The Maven view's state column, which stays as it is: Maven daemons report a state far more often than Gradle ones.
- Dialogs, notices and the Compose view's Docker error state.

## Decisions

### `OwnerCell` draws the unknown owner
`OwnerCell(owner = null)` draws `strings.ownerUnknown` with `MachineStateText` and the test tag `owner-unknown`, instead of an empty text. Gradle's `DaemonOwnerCell` goes away and its rows call `OwnerCell` directly; its UI test looks for the new tag. The DerivedData owner cell's `Orphaned` branch keeps drawing nothing: orphans are listed apart and never reach that cell.

Alternative: a `MachineOwnerCell` wrapper per view, as Gradle has today. It duplicates the same three lines in every view and leaves `OwnerCell` with a blank state nobody wants.

### A shared list action with its reason
A plain `ListActionBlock(reason: String, running: Boolean)` describes why a list action is disabled, and a composable `ListActionReason(block, tag) { enabled -> button }` in `MachineViewParts.kt` writes `block.reason` with `MachineStateText` before the button when `!block.running`, and wraps the button in `DisabledReason(block?.reason)`. Gradle's "Stop idle daemons" uses it too: `NONE_IDLE` maps to `running = false`, `STOPPING` to `running = true`, which is exactly what it does today, keeping its `stop-idle-reason` tag.

Each view gets a plain function that returns the block, or `null` when the action is enabled:
- `mavenStopIdleBlock(daemons, stopping, strings)`: running → "Daemons are being stopped" (`stopIdleBlockedStopping`); no idle daemon → the same counts as Gradle, through a `noneIdleReason(states, strings)` extracted from `stopIdleReason` (busy count vs. unknown or untracked count, falling back to "No daemon is idle" with no daemon).
- `derivedDataDeleteBlock(orphanCount, deleting, strings)`: running → "Orphaned folders are being deleted"; none → "No folder is orphaned".
- `simulatorOrphansDeleteBlock(deleting, strings)`: running → "Orphaned simulators are being deleted". The action only shows when there are orphans, so it has no other reason.
- `composeTakeDownBlock(removedCount, takingDown, strings)`: running → "Projects are being taken down"; none → "No project belongs to a removed worktree".
- `composeLeftoversBlock(leftovers, removing, strings)`: running → "Leftovers are being removed"; only volumes → "Only volumes are left". "Remove volumes too" gets the running reason only.

Alternative: extending `DaemonStopBlock` with the new cases. It's an enum about daemons; folders, simulators and Compose projects would turn it into a catch-all with a `describe` that knows every view.

### Maven row Stop
`mavenStopBlock(state, stopping)` returns a `DaemonStopBlock?` (`STOPPING`, `BUSY`, `STATE_UNKNOWN` or `null` when idle), reusing `DaemonStopBlock.describe`, and the row draws a `GhostButton` inside `DisabledReason`, as Gradle does. The busy text Gradle uses ("Only idle daemons can be stopped, and this one is busy") and the unknown one apply to Maven as written.

### Emulator and simulator rows
Their Start and Stop become `GhostButton`. They have no disabled state (the progress indicator replaces them while busy), so they don't need `DisabledReason`.

### Strings
New strings in `Strings`, `EnglishStrings` and `SpanishStrings`: `derivedDataDeleteBlockedDeleting`, `derivedDataDeleteBlockedNoOrphans`, `simulatorOrphansDeleteBlockedDeleting`, `composeTakeDownBlockedRunning`, `composeTakeDownBlockedNone`, `composeLeftoversBlockedRemoving`, `composeLeftoversBlockedOnlyVolumes`.

## Risks / Trade-offs

- [Reasons written next to several actions take horizontal room in a narrow window] → `MachineStateText` is one line with ellipsis, and the full reason stays in the tooltip.
- [The parallel active-multirepo-rows and desktop-coverage-project-settings changes touch the strings files and may touch `MachineViewParts.kt`] → New members are appended in their own block; conflicts are resolved when merging `origin/develop` before the pull request.
- [Coverage floors] → Every new plain function gets unit tests for each branch; the composables get UI tests for the written reasons and the unknown owner.
