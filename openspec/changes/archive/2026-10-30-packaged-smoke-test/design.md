# Design

## Context

- The packaged launcher is both the app and the `shogun` command: `main` runs the CLI when it gets arguments and opens the window otherwise (`desktopApp/.../main.kt`).
- Installing the command and the hooks, the tour and adding a project only exist in the interface, on objects built by `AppContainer`: `claudeHooks` (`ClaudeHooksState`, wrapping `CommandLineTool` with `jpackage.app-path` as launcher and the login shell's `PATH`), `tour` (`TourState`), `projectRepository` and `worktreeUseCases(config).create`.
- `release.yml` builds the installers in a matrix after creating the tag and the draft, then attests, publishes and updates Homebrew, Scoop and winget. Nothing installs or starts what it built.
- GitHub-hosted runners are fresh VMs: macOS runners have a logged-in desktop session and Homebrew owned by the runner user; Windows runners run as an administrator; Ubuntu runners have no display and allow `sudo useradd`.

## Goals / Non-Goals

**Goals:**

- Exercise the installed package, started with the environment the desktop gives it, through the same objects its buttons call.
- Fail the release on any operating system before anything public (tag, release, manifests) exists.
- Run the same check on a release branch before its merge.

**Non-Goals:**

- Driving the interface (clicks, windows, screenshots): that's `desktop-e2e-tests`, from Gradle.
- End-to-end coverage of the CLI's commands: `cli-e2e-tests`.
- Fixing what the smoke test finds: a failure is fixed in its own change.
- Signing or notarisation: the installers stay unsigned as today.

## Decisions

### A smoke-test mode inside the packaged app, started by an environment variable

The app runs the scenario itself when `SHOGUNAI_SMOKE_TEST_REPORT` is set, building the real `AppContainer` and calling `claudeHooks.refresh/installTool/request/confirm`, `tour.start/continueFromWelcome/continueFromTool/confirmEnd`, project registration and `worktreeUseCases(config).create`, then exits.

- Alternative: automating the interface with AppleScript or UI Automation. Rejected (chosen by Ciro): fragile on hosted runners (accessibility permissions, Compose's accessibility tree), different per OS, and it duplicates `desktop-e2e-tests`.
- An environment variable rather than an argument: arguments already mean "run as the CLI", and a hidden CLI flag would show up in `shogun`. The variable is ignored when there are arguments.
- The mode lives in `desktopApp` (`app.luxion.shogunai.smoke`): a `SmokeTestScenario` that takes its collaborators as small function types, so its unit tests use fakes, and a thin `runSmokeTest(container)` that wires it to `AppContainer` and writes the report.
- Opening no window keeps it runnable on Linux runners without a display, as long as `AppContainer` doesn't touch AWT; if it does, the Linux job runs it under `xvfb-run` (see Risks).

### The app reports, the workflow judges

The report records what the app saw (`commandLineTool.state/folder/restartNeeded`, hooks state, tour step reached, copied files), and the app exits non-zero only when a step errored. What each machine should show (Homebrew folder on macOS, `~/.local/bin` not on `PATH` on Linux's first run, restart needed on Windows) is asserted by each OS's script with `jq`. This keeps machine knowledge out of the product and lets one scenario serve every runner.

Report shape:

```json
{
  "passed": true,
  "steps": [{ "name": "install-command", "status": "passed|failed|skipped", "detail": "…" }],
  "commandLineTool": { "state": "AVAILABLE", "folder": "/opt/homebrew/bin", "restartNeeded": false, "installedByApp": true },
  "hooks": { "state": "INSTALLED" },
  "tour": { "reached": "OPEN_AGENT", "toolStepShown": false },
  "worktree": { "path": "…", "copiedFiles": [".env.local", "config/local.json"] }
}
```

### Starting the app the way the desktop does

- macOS: the `.dmg` is attached with `hdiutil`, the app copied to `/Applications`, and started with `open -W --env SHOGUNAI_SMOKE_TEST_REPORT=… -a /Applications/ShogunAi.app`. `open` goes through LaunchServices, so the app gets `launchd`'s environment (`PATH=/usr/bin:/bin:/usr/sbin:/sbin`) exactly as from the Dock. It runs as the runner user, which has Homebrew in its login shell, as the demo's Mac had.
- Windows: `msiexec /i … /qn`, then `Start-Process` of the installed `ShogunAi.exe` with `PATH` rebuilt from the registry (machine, then user), which is what Explorer gives a program started from the Start menu, rather than the runner's step `PATH`. "A new terminal" is the same rebuild after the app ran, which picks up the folder the app added to the user's `PATH`.
- Linux: `apt-get install ./….deb`, then a fresh user created with the stock skeleton of Ubuntu's `bash` package (the hosted image's `/etc/skel` adds a `.bash_profile` that stops bash reading `.profile`), whose `~/.profile` adds `~/.local/bin` to the `PATH` only when it exists at login, and no other writable folder on its `PATH`: the hosted image makes `/usr/local/bin` writable by everyone, so the script gives it back Ubuntu's stock owner and mode (`root`, `755`) and fails if the new user can still write to it. The app runs as that user with `env -i` and the session's default variables. First run: the command lands in `~/.local/bin`, not on the `PATH`; second run (a restart): the login shell now includes it, so the command is available and the hooks install. This is the "machine without `~/.local/bin` on its `PATH`" case of the roadmap, checked against the app's documented behaviour.
- After the app, each script runs `shogun new smoke-cli` from a new login shell of the same user (`zsh -l`/`bash -l`, or `cmd /c` with the rebuilt `PATH`) and compares the copied files with the base repository's.

### The sample repository is prepared by the script

Each script creates a Git repository with a committed `.shogunai.json` (`"copy": ["config/local.json"]`), an ignored `config/local.json` and an ignored `.env.local`, and passes it as `SHOGUNAI_SMOKE_TEST_REPOSITORY` with `SHOGUNAI_SMOKE_TEST_LOCAL_FILES=.env.local`. The app registers it as a project and adds `.env.local` to the project's local files, so both sources of copied files (project and repository) are covered.

### Workflows

- `package.yml` (`workflow_call`, inputs `version`, `ref`): the current `build` matrix and `build-cli`, moved unchanged.
- `smoke-test.yml` (`workflow_call`): one job per installer (`macos-latest`, `macos-26-intel`, `windows-latest`, `ubuntu-latest`), `contents: read`, no secrets, 15-minute timeout. It downloads its installer artifact, runs `.github/smoke/smoke-<os>.sh` (`.ps1` on Windows) and always uploads the report, the app's log and the script's output.
- `release.yml`: `resolve-version → test → package → smoke-test → create-release (tag + draft) → attest-assets → publish-release → manifests`. Creating the draft after building is fine: it was before building only to write the notes once, which still holds.
- `release-candidate.yml`: on `push` to `release/**` and `workflow_dispatch` (with a `version` input, default `0.0.0`, for branches that aren't `release/**`); resolves and validates the version like `release.yml`, then `package` and `smoke-test`. It has only `contents: read`. The `Tests` workflow already runs on `release/**` pushes, so it doesn't run the test suite again.
- Validation of this change: push the branch as `release/0.0.0` (a valid version that nothing publishes from a push), check the run, delete the branch. Ciro approved a temporary `release/` branch; the name follows the version rule.

## Risks / Trade-offs

- [`AppContainer` touches AWT on Linux without a display] → run the Linux job's app under `xvfb-run`, decided when the first run shows it.
- [`open --env` needs macOS 13 or later] → hosted runners are macOS 15+; the script fails with a clear message otherwise.
- [The scenario writes into the runner user's home (macOS, Windows)] → hosted runners are discarded after each job; the script checks there's no `~/.shogunai` before starting, so a reused machine fails loudly instead of passing on old state.
- [The release takes ~10 minutes longer] → the smoke jobs run in parallel and only after the builds; it's the price of not publishing a broken installer.
- [Updating checks from the network during the run] → the scenario never calls the update check; it builds `AppContainer` but doesn't start the window's background tasks.
- [Winget and Homebrew can't be smoke tested before publishing] → they install the same `.msi`/`.dmg` the smoke test installed; the rehearsal covers the Cask.

## Migration Plan

No data migration. Rollback is reverting the workflow files; the smoke-test mode is inert without its variable.

## Dependencies and follow-ups

- `host-environment` may replace `ShellLoginShellPath` in `AppContainer`; the scenario only uses `claudeHooks`, `tour` and the project and worktree objects, so it keeps working.
- `worktree-preflight` changes how a missing local file fails; the scenario reports whatever error comes back, so no change is needed here.
- Found by the first run: the `.deb`'s post-install script, generated by jpackage, fails with `xdg-desktop-menu: No writable system menu directory found` on a machine without the system menus folder `/etc/xdg/menus` (servers, minimal containers), leaving the package half-configured. Desktop installs have it, so the Linux script creates it (and `/usr/share/applications` and `/usr/share/desktop-directories`, which the same script needs) before installing, as a desktop has them. Follow-up: make the `.deb` install without a menu folder.
- Merged with `develop` before the pull request: `clean-account-rehearsal` had already added the rehearsal script (`docs/process/clean-account-rehearsal.md`), so this change dropped its own and only points that script to the `Release candidate` run's `.dmg`; and the macOS installers are now signed and notarized by `macos-installer.yml`, which `package.yml` calls with the repository's secrets, so `Release candidate` also smoke tests the signed `.dmg` users download and catches a signing problem before the merge.
- Follow-up: smoke test the Homebrew Cask and Scoop manifests rendered from the release, once they can be installed from local files before publishing.
