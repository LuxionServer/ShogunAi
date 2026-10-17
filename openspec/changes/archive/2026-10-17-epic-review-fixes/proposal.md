# Proposal

## Why

A guided walkthrough of every feature of `epic/pivot`, done before merging it into `develop`, found a few defects that make features look broken: the iOS simulator boots without ever showing a window, the project configuration screen keeps showing what the repository declared the first time it was opened (so the emulator and simulator sections never appear after a provider is added), dotfiles such as `.env` can't be picked as local files, the KMP test-wiring notice shows up in repositories that don't use Kotlin Multiplatform, and the cleanup dialog doesn't say that only the emulators, simulators and daemons of the removed worktrees are affected. The same review produced UI/UX and product ideas that belong in the roadmap, not in this epic.

## What Changes

- The project configuration screen reads the repository's state (provisioning patterns, emulator and iOS simulator sections) and starts from the saved project every time it's opened, instead of reusing the state of a previous visit.
- Starting a worktree's iOS simulator, from the app or `shogun simulator start`, also opens Xcode's simulator viewer showing that device (Simulator, or Device Hub from Xcode 27, which no longer ships Simulator), best effort: failing to open the window doesn't fail the start.
- The file picker used for local files shows hidden files, so `.env`, `.env.local` and similar can be chosen.
- The KMP test-wiring notice (config screen section and `shogun simulator status`) only appears when one of the repository's Gradle build scripts applies the Kotlin Multiplatform plugin.
- The `SessionStart` context line of repositories with the emulator provider warns the agent that the adb server is shared with other worktrees: never restart it, and never pick a device other than `ANDROID_SERIAL`.
- The cleanup dialog's emulator, simulator and Gradle daemon options say they only affect the worktrees being removed.
- Docs: the agents page says Claude Code must trust the repository's folder before its project hooks run (English and Spanish).
- Roadmap (English and Spanish): providers configuration screen, worktree screen redesign, understandable errors, guided onboarding demo, Android Studio Running Devices integration.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `project-configuration`: the screen shows the repository's current state each time it's opened; local files can be picked among hidden files; the KMP notice only applies to Kotlin Multiplatform repositories.
- `ios-simulator-provider`: starting a simulator opens its window; wiring is only suggested for Kotlin Multiplatform repositories.
- `command-line-tool`: `shogun simulator status` only reports KMP wiring in Kotlin Multiplatform repositories; the emulator context line warns about the shared adb server.
- `worktree-cleanup`: the dialog's provider options state that they only affect the removed worktrees.

## Impact

- `core`: `AppleSimulators` port gains an operation to show a device's window, implemented with `open` on the viewer's bundle id; `KmpTestWiring` gains Kotlin Multiplatform detection; `StartWorktreeSimulatorUseCase` shows the window after booting.
- `cli`: `simulator status` output and the `session-start` emulator hint.
- `desktopApp`: `AppRoot` creates a fresh `ProjectConfigViewModel` per visit; `FilePicker` shows hidden files; the simulator section hides the KMP panel for other repositories; cleanup strings in English and Spanish.
- `docs/`: `agents.md`, `roadmap.md` and their Spanish translations.
