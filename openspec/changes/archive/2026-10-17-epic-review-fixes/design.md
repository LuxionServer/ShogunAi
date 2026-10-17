## Context

See proposal.md for why. The defects come from the final walkthrough of `epic/pivot`:

- `AppRoot` gets `ProjectConfigViewModel` from the window's `ViewModelStore` with the key `"${project.id}-${project.hashCode()}"` (or `"new-project"`). The store lives as long as the window, so reopening an unchanged project returns the instance of the first visit, whose provisioning, emulator and simulator sections were computed at construction and never refreshed. The new-project form has the same issue with its typed values.
- `SimctlAppleSimulators.boot` runs `xcrun simctl boot`, which boots the device headless; nothing opens a window for it.
- `FilePicker` uses `JFileChooser`, whose `isFileHidingEnabled` defaults to `true` and hides dotfiles.
- `FileKmpTestWiring.isWiredInRepository` walks the repository's `*.gradle(.kts)` files looking for `IOS_SIMULATOR_UDID`; nothing checks whether the repository uses Kotlin Multiplatform at all.

## Goals / Non-Goals

**Goals:** fix the defects with the smallest change that keeps the current architecture; record the review's ideas in the roadmap.

**Non-Goals:** the worktree screen redesign, a providers configuration UI, friendlier error messages and the onboarding demo. They're roadmap items for changes after the epic is merged.

## Decisions

### A view model store per visit of the configuration screen

The configuration branch of `AppRoot` creates a `ViewModelStoreOwner` with `remember`, clears its store in a `DisposableEffect` when the screen leaves the composition, and passes it to `viewModel(viewModelStoreOwner = …)`. Every visit gets a new view model built from the saved project and the repository as it is now, and the previous one is cleared, cancelling its loads.

Alternatives: a unique key per visit in the window's store would never clear old instances; keeping the cached instance and adding a refresh method would need to reset every form field by hand and keep that list in sync with new fields.

### Show the device after booting, best effort

`AppleSimulators` gains `show(udid)`, implemented as `open -b com.apple.iphonesimulator --args -CurrentDeviceUDID <udid>` and, when that fails, `open -b com.apple.dt.Devices`. Xcode 27 no longer ships `Simulator.app`: its replacement, Device Hub, lists every simulator and showed the booted worktree simulator when opened during the review. Opening by bundle id avoids depending on the app's name or path, and `open -a Simulator` fails on Xcode 27 with "Unable to find application named 'Simulator'". `StartWorktreeSimulatorUseCase` calls it once the device is booted, also when it was already booted, inside `runCatching` so a failure to open the window never fails the start. Putting it in the use case covers the app and `shogun simulator start` alike, matching the Android emulator, which opens its window by default.

Alternative: a `--no-window`-style option like the emulator's. Not needed yet: a booted simulator costs the same with or without its window, and an agent that doesn't want it can close it.

### Kotlin Multiplatform detection next to the wiring check

`KmpTestWiring` gains `usesKotlinMultiplatform(repositoryPath)`. `FileKmpTestWiring` reuses the same bounded walk of build scripts and treats a repository as Kotlin Multiplatform when a script mentions `multiplatform`, ignoring case, which covers `kotlin("multiplatform")`, `id("org.jetbrains.kotlin.multiplatform")` and version catalog aliases such as `libs.plugins.kotlinMultiplatform`. The CLI's `simulator status` prints nothing about KMP otherwise, and the config screen's `SimulatorSectionState.Available` carries whether the repository is Kotlin Multiplatform so the section hides the wiring panel.

Alternative: parsing the plugins block. Too much machinery for a notice; a false positive only shows a snippet, as today.

### Warn agents about the shared adb server

One adb server per machine serves every emulator, and `ANDROID_SERIAL` keeps each worktree's `adb` and Gradle commands on its own device. What breaks the isolation is an agent restarting the server (`adb kill-server`), which cuts every other agent's install or instrumented test, or forcing a device with `-s`, `-e` or `-d`. The emulator hint of the `SessionStart` context line says so, since that's the one place every Claude session of such a repository reads.

Alternative: one adb server per worktree with `ANDROID_ADB_SERVER_PORT`. It would need every emulator, `adb` call and Gradle build of a worktree to agree on that port, which hasn't been explored; the warning covers the real risk at no cost.

### Hidden files in the file picker

Both `FilePicker` dialogs set `isFileHidingEnabled = false`. The directory picker benefits too, for example to choose `.claude/worktrees`.

## Risks / Trade-offs

- [Device Hub has no documented argument to select a device, so with several booted simulators it may show another one first] → It lists them all; the worktree's simulator is one click away and named after the worktree. In the review it reopened on the last device selected, not on the one just started.
- [Quitting Device Hub shut down the simulator it was showing during the review] → Xcode 27 behaviour outside ShogunAi; the worktree row and `shogun simulator status` show the real state, and starting again boots it.

- [Opening the simulator viewer brings it to the front over the user's work] → It happens only when the user or an agent explicitly starts a simulator, as with the Android emulator window.
- [A build script that mentions "multiplatform" in a comment is taken as Kotlin Multiplatform] → It only shows the existing wiring notice.
- [The view model is rebuilt on each visit, rereading `.shogunai.json` and listing AVDs and runtimes] → These reads already happen on the first visit and are asynchronous.
