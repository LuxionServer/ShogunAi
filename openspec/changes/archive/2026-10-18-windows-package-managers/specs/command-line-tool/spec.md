# Spec Delta

## MODIFIED Requirements

### Requirement: Install the command on each platform
The `shogun` command SHALL be available from each release: on macOS and Linux through the desktop app's launcher, which runs the command instead of the window when it receives arguments; on Windows through the Scoop manifest, which runs the command with the runtime bundled in the app; and on any platform with Java 21 or later through a `shogun-<version>.zip` attached to the release.

#### Scenario: Desktop app launched with arguments
- **WHEN** the desktop app's executable is run with `status` as argument
- **THEN** it prints the status and exits without opening a window

#### Scenario: Desktop app launched without arguments
- **WHEN** the desktop app is opened normally
- **THEN** the window opens as before

#### Scenario: Release assets
- **WHEN** a release is published
- **THEN** it includes `shogun-<version>.zip` with launch scripts for Unix and Windows

#### Scenario: Windows through Scoop
- **WHEN** a Windows user installs ShogunAi with `scoop install shogunai`
- **THEN** `shogun status` works from a new terminal without Java installed
