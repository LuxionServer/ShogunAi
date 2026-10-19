## Purpose

Lets the desktop app tell the user when a newer ShogunAi release is published and how to update it for the way it was installed, without downloading or installing anything itself.

## ADDED Requirements

### Requirement: Check for a newer release
When automatic checks are on, the desktop app SHALL ask GitHub for the latest published release of ShogunAi in the background every time the app starts and, while it stays open, once every 24 hours, and SHALL compare its version with the running one. Development builds SHALL NOT check. A failed automatic check SHALL NOT show anything and SHALL NOT remove a notice already shown.

#### Scenario: Newer version published
- **WHEN** the app starts running version 1.2.0 and the latest release is 1.3.0
- **THEN** after start-up the main window shows that 1.3.0 is available

#### Scenario: Opened again soon after
- **WHEN** the user closes the app and opens it again an hour later
- **THEN** the app checks again at start-up

#### Scenario: Left open for a week
- **WHEN** the app stays open for a week and 1.3.0 is published on the third day
- **THEN** the next daily check shows that 1.3.0 is available without restarting the app

#### Scenario: Offline
- **WHEN** the automatic check can't reach GitHub
- **THEN** nothing is shown and the app works normally

### Requirement: Friendly update notice
When a newer release is found, the main window SHALL show a short, friendly notice that names the new and the running version, links to the release notes, and doesn't block or interrupt the user's work.

#### Scenario: Newer version
- **WHEN** the app runs 1.2.0 and 1.3.0 is published
- **THEN** the notice reads along the lines of "ShogunAi 1.3.0 is out! You're on 1.2.0." with a link to what's new, and the user can keep working without dismissing it

### Requirement: Update instructions for each way of installing
The notice SHALL detect how the app was installed and remind the user of the matching way to update: the Homebrew command, the Scoop command, the winget command, or the installer to download for the platform. Commands SHALL be shown with a copy action and installers with a download action; the app SHALL NOT run a command or install anything itself. When the way can't be detected, it SHALL offer the release page.

#### Scenario: Homebrew
- **WHEN** the app was installed with the Homebrew cask
- **THEN** the notice reminds the user to run `brew upgrade --cask shogunai`, with a copy action

#### Scenario: Scoop
- **WHEN** the app was installed with Scoop
- **THEN** the notice reminds the user to run `scoop update shogunai`

#### Scenario: winget
- **WHEN** the app runs on Windows, outside Scoop, and winget lists `LuxionServer.ShogunAi` as installed
- **THEN** the notice reminds the user to run `winget upgrade LuxionServer.ShogunAi`

#### Scenario: Windows installer without winget
- **WHEN** the app runs on Windows, outside Scoop, and winget isn't available or doesn't list it
- **THEN** the notice offers to download the new `.msi`

#### Scenario: macOS without Homebrew
- **WHEN** the app was installed from a `.dmg` on an Apple Silicon Mac
- **THEN** the notice offers to download the new `-arm64.dmg`

#### Scenario: Linux package
- **WHEN** the app was installed from the `.deb`
- **THEN** the notice offers to download the new `.deb` and shows the command to install it after downloading

### Requirement: Skip a version
The update notice SHALL offer "Skip this version". A skipped version SHALL NOT be announced again by automatic checks, but a newer one SHALL be. Closing the notice without skipping SHALL hide it until the next start.

#### Scenario: Skipped then newer
- **WHEN** the user skipped 1.3.0 and later 1.3.1 is published
- **THEN** the next automatic check announces 1.3.1

### Requirement: Check on demand
The Help menu, or the application menu on macOS, SHALL offer "Check for Updates…", which checks immediately regardless of the schedule and of skipped versions, and SHALL say whether the app is up to date, which version is available, or why the check failed.

#### Scenario: Up to date
- **WHEN** the user chooses "Check for Updates…" and the running version is the latest
- **THEN** the app says ShogunAi is up to date

#### Scenario: Check fails
- **WHEN** the user chooses "Check for Updates…" without network
- **THEN** the app says the check failed and why

### Requirement: Turn automatic checks off
Preferences SHALL offer "Check for updates automatically", on by default. When it is off, the app SHALL NOT contact GitHub except when the user chooses "Check for Updates…".

#### Scenario: Checks off
- **WHEN** the user turns the option off and restarts the app
- **THEN** no request is made at start-up
