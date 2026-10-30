# Proposal

## Why

`shogun doctor` and the desktop app's "Check Installation…" check ShogunAi's own installation and each project's local files, but not the tools the providers a repository declares rely on (the Android SDK, Xcode, Docker…) nor the multirepos and their groups. On a machine that isn't the developer's, those are exactly what fails later, far from a useful message: an emulator that won't start, a `shogun compose up` that can't find Docker, a multirepo whose repository moved. Roadmap point 1 (reliability on any machine) asks for these checks, and for the clean account rehearsal to run `shogun doctor` so a failure there arrives with what's wrong and how to fix it.

## What Changes

- For each registered project, the installation check reads the providers its `.shogunai.json` declares and checks the tools each one needs:
  - `gradle`: the repository's Gradle wrapper, or `gradle` on the `PATH`.
  - `maven`: the repository's Maven wrapper, or `mvn` on the `PATH`.
  - `emulator`: the Android SDK, found where the emulator provider looks for it, and at least one Android Virtual Device.
  - `ios-simulator`: Apple's simulators (macOS with Xcode) and at least one iOS runtime.
  - `xcode`: Xcode itself (`xcodebuild -version` runs, which the Command Line Tools alone don't), and `pod` on the `PATH` when it declares `cocoaPods`.
  - `docker-compose`: `docker compose version` runs.
  - `flutter`: `flutter` on the `PATH`, or `fvm` when the repository uses FVM.
- A provider tool that is missing or unusable is a warning naming the project, the provider and the tool, with its fix; ShogunAi itself still works.
- For each multirepo ShogunAi knows, the check reports its declaration (missing, invalid, untrusted), each declared repository that isn't a Git repository, and each group whose worktree folder in one of its repositories no longer exists, each with its fix.
- The installation check now may run commands that list what is installed (AVDs, simulator runtimes, Git worktrees), still without changing anything.
- `shogun doctor`, its `--json` output and the desktop app's window show the new checks in English and Spanish.
- The clean account rehearsal gains a step that runs `shogun doctor` and attaches its output to a failure report.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `installation-check`: new requirements for provider tools and multirepos, and the "without changing anything" requirement allows listing commands.

## Impact

- `core`: `CheckInstallationUseCase` gains provider and multirepo checks; `InstallationFinding` gains `ProviderTool` and `MultirepoCheck` findings; `InstallationReportText` renders them.
- `cli`: `shogun doctor` wires the Android SDK, Apple's simulators and the multirepo resolution into the check; `doctorJson` needs no change beyond the new ids.
- `desktopApp`: `AppContainer` wires the same dependencies; English and Spanish installation texts for the new findings.
- Docs: `docs/guide` pages describing `shogun doctor` / Check Installation (both languages), `docs/process/clean-account-rehearsal.md` and `.es.md`.
