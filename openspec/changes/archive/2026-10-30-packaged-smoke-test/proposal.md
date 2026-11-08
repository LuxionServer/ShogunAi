# Proposal

## Why

A demo on another Mac showed two failures the developer's machine hides: the app, opened from the Dock with macOS's minimal `PATH`, read a Homebrew `shogun` as missing (so the guided tour and Claude Code's hooks refused to work), and creating a worktree failed while copying its local files. The test suite runs the code from Gradle on a developer-like setup, so nothing today checks the installer a user actually downloads, started the way a user starts it, on a machine without the developer's settings. This is item "1. Reliability on any machine" of the roadmap.

## What Changes

- The packaged desktop app gains a smoke-test mode, started with an environment variable instead of a window: with the same objects the interface uses, it installs the `shogun` command and Claude Code's hooks, walks the guided tour's start, registers a project with local files and creates a worktree, then writes a JSON report and exits with a status that says whether every step passed.
- A reusable packaging workflow builds the installers (`.dmg` for Apple Silicon and Intel, `.msi`, `.deb`) and the CLI zip, shared by the release and by the new release-candidate run.
- A reusable smoke-test workflow installs each package on a fresh runner (`.dmg` on macOS Apple Silicon and Intel, `.msi` on Windows, `.deb` on Linux), starts the app the way the desktop starts it (the Dock's `launchd` environment on macOS, the registry's `PATH` on Windows, a fresh user's session on Linux), runs the smoke-test mode, then runs `shogun new` from a new login shell with the installed command and checks the copied files.
- Linux runs as a fresh user without `~/.local/bin` on its `PATH`, so it covers installing the command into a folder that isn't on the `PATH` yet, and the app's restart that picks it up; Windows covers installing the command from the app (its `shogun.cmd` and the user's `PATH`).
- The `Release` workflow runs the smoke test after building and before tagging, creating the draft or publishing: a failure on any operating system leaves no tag, no release and no updated package manifest.
- A new `Release candidate` workflow runs the same packaging and smoke test on every push to a `release/**` branch (and by hand), so a release branch is checked before its pull request is merged; it never tags or publishes.
- A rehearsal script in `docs/process/` (English and Spanish) for a clean macOS user account, without the developer's instructions or settings, going through the guided tour before each release or demo.
- `docs/process/release.md` and its Spanish translation describe the new steps.

## Capabilities

### New Capabilities

- `packaged-smoke-test`: checking the installed app on a clean machine of each operating system, started the way the desktop starts it, before a release is published; the app's smoke-test mode that does it; and the manual rehearsal on a clean macOS account that complements it before each release or demo.

### Modified Capabilities

- `continuous-integration`: a release builds only after its tests pass, and is now created and published only after its packaged smoke test passes on every operating system.

## Impact

- Code: `desktopApp` (a smoke-test entry point in `main.kt` and the scenario it runs on `AppContainer`'s objects, with unit tests).
- CI: `.github/workflows/release.yml` (reordered jobs), new `package.yml`, `smoke-test.yml` and `release-candidate.yml`, and the per-OS scripts they run under `.github/smoke/`.
- Docs: `docs/process/release.md`/`.es.md`, the new rehearsal pages and `mkdocs.yml`'s navigation; the roadmap when archiving.
- Runtime: a release takes longer (the smoke-test jobs run after the builds, ~5 minutes); runners are GitHub-hosted, no new secrets.
- Related changes in flight: `host-environment` may move where the login shell's `PATH` is read; the smoke-test mode only uses `AppContainer`'s public objects, so it follows that move without changes. `cli-e2e-tests` and `desktop-e2e-tests` cover the CLI and the interface from Gradle; this change covers only the packaged app.
