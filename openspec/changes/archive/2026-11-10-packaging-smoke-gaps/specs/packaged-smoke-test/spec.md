# Spec Delta

## MODIFIED Requirements

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

#### Scenario: Linux without a system menus folder
- **WHEN** the Linux job starts on a runner without `/etc/xdg/menus`
- **THEN** it installs the `.deb` without creating that folder and checks the package is fully installed; after the app's checks it removes the package and checks it's gone, then creates the menus folders as a desktop has them, installs the `.deb` again and checks the applications menu entry was added

#### Scenario: Linux runner with a menus folder
- **WHEN** the Linux job starts on a runner that already has `/etc/xdg/menus`
- **THEN** it fails before installing, saying the runner isn't the machine to test

#### Scenario: A check fails
- **WHEN** the report has a failed step, doesn't match what the machine should show, or a copied file is missing or differs
- **THEN** that operating system's job fails, naming the step or file, and uploads the report and the app's log

## ADDED Requirements

### Requirement: Package manager manifests are smoke tested before publishing
The smoke-test workflow SHALL render the Homebrew Cask and the Scoop manifest for the release's version from the installers just built, with the same rendering the release uses to publish them, and install each one on a fresh runner before anything is published: the Cask on macOS Apple Silicon and Intel, the manifest on Windows.

#### Scenario: Cask installed and removed
- **WHEN** the Cask rendered for version `1.4.0` is installed with `brew install --cask` from a local tap, with the `.dmg` built for the runner's architecture in place of the release's download
- **THEN** the Cask's URL is the release's download URL for that `.dmg`, its checksum matches it, the app is in `/Applications`, `shogun --version` from a new login shell prints `shogun 1.4.0`, and after `brew uninstall --cask` neither the app nor the `shogun` link remains

#### Scenario: Scoop manifest installed and removed
- **WHEN** the manifest rendered for version `1.4.0` is installed with `scoop install` from a local file, with the `.msi` just built in place of the release's download
- **THEN** the manifest's URL is the release's download URL for that `.msi`, its checksum matches it, the Start menu shortcut exists, `shogun --version` from a new session prints `shogun 1.4.0`, and after `scoop uninstall` neither the app, the shortcut nor the `shogun` command remains

#### Scenario: A manifest doesn't match the installers
- **WHEN** the rendered Cask or manifest names a download the release won't publish, has a placeholder left, or its checksum doesn't match the installer
- **THEN** the job fails naming the mismatch, and the release isn't published

### Requirement: One rendering for the smoke test and the release
The release SHALL publish the Homebrew Cask and the Scoop manifest rendered by the same scripts the smoke test runs, and rendering SHALL fail when a checksum is missing or a placeholder is left in the result.

#### Scenario: Missing checksum
- **WHEN** a checksum for one of the installers is empty
- **THEN** rendering fails naming the missing checksum and nothing is written to the tap or the bucket
