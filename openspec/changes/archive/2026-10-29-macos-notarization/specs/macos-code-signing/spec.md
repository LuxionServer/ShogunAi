# Spec Delta

## Purpose

Makes the macOS installers published by every release signed with a Developer ID certificate, notarized by Apple and stapled, so Gatekeeper opens the app without warnings.

## ADDED Requirements

### Requirement: Signed and notarized macOS installers
Every macOS `.dmg` published by a release SHALL contain the app signed with the project's Developer ID Application certificate under the hardened runtime, SHALL itself be signed with that certificate, and SHALL be notarized by Apple with the notarization ticket stapled to it.

#### Scenario: Gatekeeper accepts the installer
- **WHEN** a release's `ShogunAi-<version>-arm64.dmg` or `ShogunAi-<version>-x64.dmg` is checked with `spctl -a -t open --context context:primary-signature -vv`
- **THEN** it is accepted with source "Notarized Developer ID", and `xcrun stapler validate` on it succeeds

#### Scenario: Gatekeeper accepts the app
- **WHEN** the app is copied from the `.dmg` to `/Applications` and checked with `spctl -a -t exec -vv`
- **THEN** it is accepted with source "Notarized Developer ID", and `codesign --verify --deep --strict` reports no errors

#### Scenario: First launch of a downloaded installer
- **WHEN** a user downloads the `.dmg` with a browser or Homebrew, so it carries the quarantine attribute, and opens the app for the first time
- **THEN** macOS opens it without the "the developer cannot be verified" warning

### Requirement: The signed app keeps working under the hardened runtime
The signed app SHALL keep every behavior of the unsigned app, including running its JVM and native libraries and controlling other apps through Apple Events.

#### Scenario: App starts
- **WHEN** the signed app is opened
- **THEN** its main window appears, as with the unsigned app

#### Scenario: Automation through Apple Events
- **WHEN** the signed app opens a worktree in Terminal or brings another app's window forward
- **THEN** macOS asks the user, once, for permission to control that app, with the app's own usage description, and the action works once granted

#### Scenario: Command installed from the app bundle
- **WHEN** `shogun --version` runs through the link to the signed app's executable
- **THEN** it prints the installed version

### Requirement: No release without signed installers
The release pipeline SHALL NOT publish a release whose macOS installers are unsigned or not notarized.

#### Scenario: Signing secrets missing
- **WHEN** a release runs and any of the signing or notarization secrets is missing
- **THEN** the macOS build fails visibly in CI, naming the missing secret, and the release stays in draft

#### Scenario: Notarization rejected
- **WHEN** Apple rejects or does not finish a notarization submission
- **THEN** the macOS build fails visibly in CI showing the notary log, and the release stays in draft

### Requirement: Unsigned builds outside the release pipeline
Builds made without a signing identity, such as local builds and test runs, SHALL produce an unsigned app exactly as before, without needing any certificate or secret.

#### Scenario: Local package
- **WHEN** a developer runs `./gradlew :desktopApp:packageReleaseDmg` without a signing identity
- **THEN** the `.dmg` is built unsigned and the build succeeds

### Requirement: Signed installers on demand
Maintainers SHALL be able to build signed and notarized macOS installers on demand, without publishing a release, to check the signing setup.

#### Scenario: Manual run
- **WHEN** a maintainer runs the macOS installer workflow by hand with a version
- **THEN** it produces both signed, notarized and stapled `.dmg` files as workflow artifacts, and no release, tag or package manager is touched

### Requirement: Signing credentials only in CI secrets
The certificate, its password and the notarization credentials SHALL live only in the repository's Actions secrets, never in a file of the repository or in a workflow's logs, and the runner's temporary keychain SHALL be deleted when the job ends.

#### Scenario: Job ends
- **WHEN** a macOS installer job finishes, whether it succeeded or failed
- **THEN** the temporary keychain and the decoded certificate and API key files are removed from the runner
