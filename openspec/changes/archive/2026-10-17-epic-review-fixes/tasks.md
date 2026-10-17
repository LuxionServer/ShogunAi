# Tasks

## 1. Project configuration screen

- [x] 1.1 Give the configuration branch of `AppRoot` its own `ViewModelStoreOwner` per visit, cleared when the screen leaves the composition; verify in the running app (the desktop module has no Compose UI test setup) that reopening the screen after the repository declares the emulator provider shows the "Emulator" section, and that a discarded name edit doesn't reappear
- [x] 1.2 Set `isFileHidingEnabled = false` in both `FilePicker` dialogs; verify by opening the local files picker on the demo repository and seeing `.env`

## 2. iOS simulator window

- [x] 2.1 Add `show(udid)` to `AppleSimulators`, implement it in `SimctlAppleSimulators` as `open -b com.apple.iphonesimulator --args -CurrentDeviceUDID <udid>`, falling back to Device Hub (`open -b com.apple.dt.Devices`) when Xcode 27 no longer ships Simulator (argv), and record calls in the fake; verify with `SimctlAppleSimulators` tests for the Simulator app, the Device Hub fallback and both failing
- [x] 2.2 Call `show` from `StartWorktreeSimulatorUseCase` after the device is booted, also when it was already booted, ignoring its failure; verify with use case tests for "Window shown", "Start of a booted simulator" and "Window can't be opened"

## 3. Kotlin Multiplatform detection

- [x] 3.1 Add `usesKotlinMultiplatform` to `KmpTestWiring`, implement it in `FileKmpTestWiring` over the same walk of build scripts and support it in `FakeKmpTestWiring`; verify with `FileKmpTestWiringTest` cases for `kotlin("multiplatform")`, a version catalog alias, a JVM-only build and a repository without Gradle
- [x] 3.2 Print the KMP wiring lines of `shogun simulator status` only for Kotlin Multiplatform repositories; verify with `EnvironmentCommandsTest` for "Status of an unwired repository" and "Status outside Kotlin Multiplatform"
- [x] 3.3 Carry whether the repository is Kotlin Multiplatform in `SimulatorSectionState.Available` and hide the wiring panel otherwise; verify with `SimulatorSettingsTest` for "Repository without Kotlin Multiplatform" and the existing wired and unwired cases

## 4. Shared adb server

- [x] 4.1 Add the shared adb server warning to the emulator hint of `shogun hook session-start`; verify with the `EnvironmentCommandsTest` session-start emulator test

## 5. Cleanup dialog wording

- [x] 5.1 Reword the Gradle daemon, emulator and simulator options in `EnglishStrings` and `SpanishStrings` to say they only affect the worktrees being removed (and orphaned simulators); verify by reading the dialog in the app in both languages

## 6. Docs

- [x] 6.1 Add to `docs/agents.md` and `docs/agents.es.md` that Claude Code must trust the repository's folder before its project hooks run; verify with `mkdocs build --strict`
- [x] 6.2 Add the review's roadmap items to `docs/roadmap.md` and `docs/roadmap.es.md` (providers configuration screen, worktree screen redesign, understandable errors, guided onboarding demo, Android Studio Running Devices integration); verify with `mkdocs build --strict`

## 7. Verification

- [x] 7.1 Run `./gradlew test` and check the fixes in the running app on the demo repository: emulator and simulator sections appear after declaring the providers, the simulator window opens on start, no KMP notice in a non-KMP repository, and `.env` can be picked

## Workflow follow-up

- Archive the change, open its pull request into `epic/pivot`, then the pull request from `epic/pivot` into `develop`.
