# homebrew-cask-distribution Specification

## Purpose
Lets macOS users install and update the app with Homebrew, with the Cask kept in sync with every published release.

## Requirements

### Requirement: Instalación vía Homebrew Cask
The system SHALL offer a Homebrew Cask in the `LuxionServer/homebrew-shogunai` tap that installs the latest published version of the app on macOS from the `.dmg` of the corresponding GitHub Release that matches the user's CPU architecture (arm64 or Intel x86_64), without the user choosing it manually.

#### Scenario: Instalación fresca
- **WHEN** a user runs `brew tap LuxionServer/shogunai` followed by `brew install --cask shogunai`
- **THEN** Homebrew downloads the `.dmg` of the latest release for the user's architecture and leaves the app installed in `/Applications`

#### Scenario: Installation on an Apple Silicon Mac
- **WHEN** a user with an Apple Silicon Mac runs `brew install --cask shogunai`
- **THEN** Homebrew downloads the `arm64` `.dmg` and verifies it against the arm64 `sha256`

#### Scenario: Installation on an Intel Mac
- **WHEN** a user with an Intel Mac runs `brew install --cask shogunai`
- **THEN** Homebrew downloads the `x64` `.dmg` and verifies it against the Intel `sha256`

#### Scenario: Advertencia de Gatekeeper documentada
- **WHEN** the user installs the Cask
- **THEN** Homebrew shows a notice (`caveats`) explaining that the first launch may be blocked by Gatekeeper because the app is not notarized, and how to open it anyway

### Requirement: Actualización automática del Cask en cada release
The system SHALL automatically update the Cask in the tap every time a new release of `LuxionServer/ShogunAi` is published, without manual intervention, writing the new version and one `sha256` per macOS architecture.

#### Scenario: Release publicada con éxito
- **WHEN** the release workflow publishes a new GitHub Release with its arm64 and x64 `.dmg` files
- **THEN** a CI job reads the `sha256` of both `.dmg` files from the release's `SHA256SUMS`, regenerates `Casks/shogunai.rb` in the tap from the Cask template versioned in `LuxionServer/ShogunAi` with the new version and both checksums, and commits and pushes that change to the tap repository

#### Scenario: A macOS installer is missing
- **WHEN** the update job runs and either the arm64 or the x64 `.dmg` cannot be downloaded from the release
- **THEN** the job fails visibly in GitHub Actions without modifying the tap

#### Scenario: Re-ejecución sin cambios
- **WHEN** the update job runs again for a version already reflected in the Cask
- **THEN** no new commit is created (there are no differences to commit) and the job finishes without error

#### Scenario: Falla la autenticación contra el tap
- **WHEN** the token used to write to `LuxionServer/homebrew-shogunai` is invalid or has expired
- **THEN** the job fails visibly in GitHub Actions, without affecting the already completed release publication

### Requirement: Install the shogun command with the Cask
The Cask SHALL link the desktop app's executable as `shogun` in Homebrew's `bin` folder, so installing the app also installs the command line tool, and uninstalling removes both.

#### Scenario: Command available after install
- **WHEN** the user runs `brew install --cask shogunai`
- **THEN** `shogun --version` works from a new terminal and prints the installed version

#### Scenario: Uninstall
- **WHEN** the user runs `brew uninstall --cask shogunai`
- **THEN** the `shogun` link is removed together with the app

### Requirement: The cask uses the published installers
The cask rendered by each release SHALL download the `.dmg` names published by that release and declare the checksums from its `SHA256SUMS`, without recomputing the names with its own rule.

#### Scenario: Release 2.0.0
- **WHEN** version `2.0.0` is released
- **THEN** `brew install --cask shogunai` downloads `ShogunAi-2.0.0-<arch>.dmg` and its checksum matches
