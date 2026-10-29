# Spec Delta

## REMOVED Requirements

### Requirement: Instalación vía Homebrew Cask
**Reason**: Its "Advertencia de Gatekeeper documentada" scenario no longer holds now that the app is notarized; replaced by "Install via Homebrew Cask", identical except for that scenario.
**Migration**: None for users; the Cask simply stops showing the Gatekeeper `caveats`.

## ADDED Requirements

### Requirement: Install via Homebrew Cask
The system SHALL offer a Homebrew Cask in the `LuxionServer/homebrew-shogunai` tap that installs the latest published version of the app on macOS from the `.dmg` of the corresponding GitHub Release that matches the user's CPU architecture (arm64 or Intel x86_64), without the user choosing it manually.

#### Scenario: Fresh install
- **WHEN** a user runs `brew tap LuxionServer/shogunai` followed by `brew install --cask shogunai`
- **THEN** Homebrew downloads the `.dmg` of the latest release for the user's architecture and leaves the app installed in `/Applications`

#### Scenario: Installation on an Apple Silicon Mac
- **WHEN** a user with an Apple Silicon Mac runs `brew install --cask shogunai`
- **THEN** Homebrew downloads the `arm64` `.dmg` and verifies it against the arm64 `sha256`

#### Scenario: Installation on an Intel Mac
- **WHEN** a user with an Intel Mac runs `brew install --cask shogunai`
- **THEN** Homebrew downloads the `x64` `.dmg` and verifies it against the Intel `sha256`

#### Scenario: Opens without a Gatekeeper warning
- **WHEN** the user installs the Cask and opens the app for the first time
- **THEN** macOS opens it without the "the developer cannot be verified" warning, and the Cask shows no `caveats` about Gatekeeper or the quarantine attribute
