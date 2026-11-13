# Proposal

## Why

`shogun gradle` already names the build that holds a busy Gradle daemon: the tasks its client asked for, when it started and the agent session that launched it. The desktop app's Gradle daemons view only says "busy", so from the app the user can't tell which build, or which agent, is keeping a daemon (and a slot of the Gradle pool) taken. The roadmap lists it under "Robustness and maintenance".

## What Changes

- The Gradle daemons view shows, under the row of each busy daemon whose build core identifies, a detail line with the build's tasks, how long ago it started and the agent session that launched it, leaving out the parts core couldn't find.
- The app reads the daemons through core's `ListGradleDaemonsUseCase` with the agent session store, the same way `shogun gradle` does, so the holding build arrives with its session; the app adds no lookup of its own.
- The detail line's texts live in the interface's strings (English and Spanish), next to the other Gradle daemon texts.
- The roadmap item is removed from `docs/roadmap.md` and `docs/roadmap.es.md`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `machine-resources`: the Gradle daemons view shows the build that holds each busy daemon.

## Impact

- `desktopApp`: `GradleDaemonsView.kt` (detail line), `AppContainer.kt` (daemon listing wired through `ListGradleDaemonsUseCase` with the session store), `MachineUseCases.listDaemons` (receives the saved worktrees' paths), one call in `MachineResources.kt`, the i18n `Strings`, and their tests.
- No change in `core` or `cli`.
- File length caps of `Strings.kt`, `EnglishStrings.kt`, `SpanishStrings.kt` raised by the lines of the new text (see design).
