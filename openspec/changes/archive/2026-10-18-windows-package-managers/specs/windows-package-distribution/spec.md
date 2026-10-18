# Spec Delta

## ADDED Requirements

### Requirement: Install with winget
The system SHALL publish ShogunAi in winget's community repository as `LuxionServer.ShogunAi`, installing the `.msi` of the corresponding GitHub Release.

#### Scenario: Fresh install
- **WHEN** a Windows user runs `winget install LuxionServer.ShogunAi`
- **THEN** winget downloads the latest release's `.msi`, verifies its `sha256` and installs the app

#### Scenario: Upgrade
- **WHEN** a user with an earlier version installed through winget or the `.msi` runs `winget upgrade LuxionServer.ShogunAi`
- **THEN** the newer version replaces the installed one, leaving a single ShogunAi in the installed apps list

### Requirement: Update the winget package on every release
The release workflow SHALL open a pull request to `microsoft/winget-pkgs` with the new version's manifest every time a release is published, after the release is public, without blocking the other package managers.

#### Scenario: Release published
- **WHEN** the release workflow publishes a release with its `.msi`
- **THEN** a job opens a pull request to `microsoft/winget-pkgs` updating `LuxionServer.ShogunAi` to that version and that `.msi`

#### Scenario: winget update fails
- **WHEN** the winget job fails, e.g. because its token is invalid
- **THEN** the job fails visibly in GitHub Actions, the release stays published and the Homebrew and Scoop updates still run

### Requirement: Install with Scoop
The system SHALL offer a Scoop bucket `LuxionServer/scoop-shogunai` with a `shogunai` manifest that installs the app from the release's `.msi` without running the Windows installer, adds a Start menu shortcut, and provides a `shogun` command that runs the CLI with the runtime bundled in the app.

#### Scenario: Fresh install
- **WHEN** a Windows user runs `scoop bucket add shogunai https://github.com/LuxionServer/scoop-shogunai` followed by `scoop install shogunai`
- **THEN** Scoop downloads the latest release's `.msi`, verifies its `sha256`, extracts the app without administrator rights and adds a ShogunAi shortcut to the Start menu

#### Scenario: Command without Java
- **WHEN** the user runs `shogun --version` in a new terminal after `scoop install shogunai`, on a machine without Java
- **THEN** it prints the installed version

#### Scenario: Uninstall
- **WHEN** the user runs `scoop uninstall shogunai`
- **THEN** the app, its shortcut and the `shogun` command are removed

### Requirement: Update the Scoop manifest on every release
The release workflow SHALL regenerate `bucket/shogunai.json` in `LuxionServer/scoop-shogunai` from the manifest template versioned in `LuxionServer/ShogunAi` every time a release is published, with the new version and the `.msi`'s `sha256`, and commit and push it.

#### Scenario: Release published
- **WHEN** the release workflow publishes a release with its `.msi`
- **THEN** a job computes the `.msi`'s `sha256`, renders the template with the version and the checksum, and pushes the change to the bucket

#### Scenario: Installer missing
- **WHEN** the job runs and the `.msi` can't be downloaded from the release
- **THEN** the job fails visibly without modifying the bucket

#### Scenario: Re-run without changes
- **WHEN** the job runs again for a version already in the manifest
- **THEN** no commit is created and the job finishes without error

#### Scenario: Bucket authentication fails
- **WHEN** the token used to write to the bucket is invalid or expired
- **THEN** the job fails visibly, without affecting the published release or the other package managers

### Requirement: In-place MSI upgrades
Every `.msi` the release builds SHALL declare the same Windows Installer upgrade code, so installing a newer one replaces the installed version.

#### Scenario: Newer msi over an older one
- **WHEN** a user with ShogunAi installed from a release's `.msi` installs a later release's `.msi`
- **THEN** the earlier version is removed and only the later one remains installed
