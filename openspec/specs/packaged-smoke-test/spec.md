# packaged-smoke-test Specification

## Purpose
Checks the installers a user downloads, installed on a clean machine of each operating system and started the way the desktop starts them, before a release is published, and keeps a manual rehearsal on a clean macOS account for what automation can't see.

## Requirements

### Requirement: Smoke-test mode of the packaged app
When the packaged app is started with `SHOGUNAI_SMOKE_TEST_REPORT` set to a file path, it SHALL open no window, run the smoke-test scenario with the same objects its interface uses, write a JSON report of every step to that file and exit with status 0 only when every step passed. Without that variable the app SHALL start as before.

#### Scenario: Every step passes
- **WHEN** the app is started with `SHOGUNAI_SMOKE_TEST_REPORT=<file>` and every step of the scenario succeeds
- **THEN** no window opens, `<file>` holds one entry per step marked as passed, and the process exits with status 0

#### Scenario: A step fails
- **WHEN** a step of the scenario fails, for example copying a local file into the new worktree
- **THEN** the report marks that step as failed with the error's message, the steps that depend on it as skipped, and the process exits with a non-zero status

#### Scenario: Normal start
- **WHEN** the app is started without `SHOGUNAI_SMOKE_TEST_REPORT`
- **THEN** it opens its window as before

#### Scenario: Command-line use
- **WHEN** the app's launcher is run with arguments, as the `shogun` command
- **THEN** it runs the command as before, whether `SHOGUNAI_SMOKE_TEST_REPORT` is set or not

### Requirement: Smoke-test command and hooks steps
The scenario SHALL first read the command-line tool's state, install the command from the app when it isn't usable by the hooks and the app can install it, and install Claude Code's hooks in the user settings when the command is usable by them, as the interface's buttons do.

#### Scenario: Command found on the login shell's PATH
- **WHEN** the app is started with the minimal `PATH` and the folder it installs `shogun` into is on the login shell's `PATH`
- **THEN** the report shows the command available in that folder and the hooks installed

#### Scenario: Command installed into a folder not on the PATH
- **WHEN** no writable folder on the login shell's `PATH` exists and the app installs `shogun` into `~/.local/bin`
- **THEN** the report shows the command installed but not on the `PATH` with that folder, and the hooks step skipped as not offered

#### Scenario: Windows
- **WHEN** the scenario runs on Windows and the command is missing
- **THEN** the app installs its `shogun.cmd`, the report shows that terminals need restarting, and the hooks are installed

### Requirement: Smoke-test tour step
The scenario SHALL then start the guided tour and continue it as a user would, record the step it reached and whether the command-line tool step was shown, and end the tour, removing its sample.

#### Scenario: Tour starts
- **WHEN** the command was installed in the previous steps
- **THEN** the report shows the tour reaching the step that opens its agent, and no tour project or folder is left

### Requirement: Smoke-test project and worktree steps
The scenario SHALL finally register the repository named by `SHOGUNAI_SMOKE_TEST_REPOSITORY` as a project with the local files named by `SHOGUNAI_SMOKE_TEST_LOCAL_FILES`, create a worktree in it, and check that each local file and each file the repository declares was copied with the base repository's contents.

#### Scenario: Local files copied
- **WHEN** the project lists `.env.local` as a local file and the repository's `.shogunai.json` declares another file to copy
- **THEN** the new worktree holds both with the base repository's contents, and the report lists them

#### Scenario: Missing repository
- **WHEN** `SHOGUNAI_SMOKE_TEST_REPOSITORY` is unset or names a folder that isn't a Git repository
- **THEN** the project and worktree steps fail naming the folder, and the process exits with a non-zero status

### Requirement: Packaged smoke test on every operating system
A smoke-test workflow SHALL install each installer on a fresh runner: the `.dmg` on macOS Apple Silicon and Intel, the `.msi` on Windows and the `.deb` on Linux. It SHALL start the installed app the way the desktop starts it, run the smoke-test mode, check its report against what that machine should show, then run `shogun new` with the installed command from a new login shell and check the copied files.

#### Scenario: macOS opened from the Dock
- **WHEN** the `.dmg` is installed into `/Applications` and the app is opened with `open`, which gives it `launchd`'s minimal `PATH`
- **THEN** the report shows `shogun` available in a Homebrew folder, the hooks installed, the tour started and the worktree's local files copied, and `shogun new` from a login shell creates a worktree with the same files

#### Scenario: Windows started from the Start menu
- **WHEN** the `.msi` is installed silently and the app is started with the `PATH` the registry gives a new session
- **THEN** the report shows the command installed from the app and the hooks installed, and a new session's `PATH`, read again from the registry, runs `shogun new` through the installed `shogun.cmd`

#### Scenario: Linux without ~/.local/bin on the PATH
- **WHEN** the `.deb` is installed and the app is started as a new user whose `~/.local/bin` doesn't exist, with the session's default `PATH`
- **THEN** the first run shows the command installed into `~/.local/bin` but not on the `PATH`; after the app is started again, which reads the login shell's `PATH` anew, the command is available and the hooks are installed, and `shogun new` from that user's login shell copies the local files

#### Scenario: A check fails
- **WHEN** the report has a failed step, doesn't match what the machine should show, or a copied file is missing or differs
- **THEN** that operating system's job fails, naming the step or file, and uploads the report and the app's log

### Requirement: Release candidates are smoke tested
Every push to a `release/**` branch SHALL run the packaging and the smoke test on that commit, and they SHALL also be startable by hand, without creating a tag, a release or any package manifest.

#### Scenario: Push to a release branch
- **WHEN** a commit is pushed to `release/1.4.0`
- **THEN** the installers for version `1.4.0` are built and smoke tested on every operating system, and no tag or release is created

#### Scenario: Invalid release branch name
- **WHEN** a commit is pushed to `release/1.4`
- **THEN** the run fails before building, naming the expected format

### Requirement: Rehearsal on a clean macOS account
The repository SHALL keep, in `docs/process/` in English and Spanish, a script to rehearse before each release or demo on a macOS user account with no ShogunAi settings, no Claude Code settings and none of the developer's instructions: installing the `.dmg` or the Homebrew Cask, opening the app from the Dock and going through the guided tour to its end, with what to check at each step and what to do with a failure.

#### Scenario: Before a release
- **WHEN** a release branch is ready to merge
- **THEN** the release process points to the rehearsal script, which says how to create the clean account, what to check at each step of the tour and how to remove the account afterwards
