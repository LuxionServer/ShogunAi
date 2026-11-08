# Proposal

## Why

The machine-entry-and-alarms change fixed the Gradle daemons view after the UX review of the v2 interface, and left the other Machine views as they were. They still show the noise it removed there: a Maven daemon without a known owner has a blank owner cell, the Start and Stop buttons of the Maven, emulator and simulator rows are bordered secondary buttons that weigh as much as the view's own actions, and the disabled list actions ("Stop idle daemons", "Delete orphans", "Take down the projects of removed worktrees", "Remove networks and images") don't say why they are disabled.

## What Changes

- An owner cell whose owner isn't known shows a dimmed "unknown" in every Machine list, not only for Gradle daemons. Today that reaches the Maven daemons view.
- The actions inside the rows of the Maven daemons, Android emulators and iOS simulators views (Stop, Start) are ghost buttons, like the Stop of the Gradle daemon rows. A disabled Maven Stop gives its reason (stopping, busy or state unknown) in its tooltip.
- Every disabled list action of the Maven daemons, iOS simulators, Xcode DerivedData and Compose projects views says why: in its tooltip always, and also written next to it unless the reason is that the action is already running, which its progress indicator shows. "Stop idle daemons" of the Maven view counts busy and unknown daemons as the Gradle one does.
- The Gradle view's own owner cell and stop reason move to shared parts, with the same behavior.
- Out of scope: the state column of the Maven view (it keeps showing every state), the Compose view's Docker error state, and the actions outside the lists (dialogs, notices).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `machine-resources`: the unknown owner and ghost row actions extend from the Gradle daemons view to every Machine list, and the disabled list actions of the other views say why they are disabled.

## Impact

- Desktop app only: `ui/machine` (the Maven, emulator, simulator, DerivedData, Compose and Gradle views, `MachineViewParts.kt`), `ui/i18n` (English and Spanish strings) and their tests.
- No change to `core`, the CLI or persisted settings.
- The parallel active-multirepo-rows and desktop-coverage-project-settings changes also touch the desktop app; shared files (strings, components) may conflict and are resolved when merging `origin/develop` before the pull request.
