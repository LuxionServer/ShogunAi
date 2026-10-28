# Spec Delta

## MODIFIED Requirements

### Requirement: Friendly update notice
When a newer release is found, the main window SHALL show a short, friendly notice that names the new and the running version, offers its release notes, and doesn't block or interrupt the user's work.

#### Scenario: Newer version
- **WHEN** the app runs 1.2.0 and 1.3.0 is published
- **THEN** the notice reads along the lines of "ShogunAi 1.3.0 is out! You're on 1.2.0." with an info action and a "What's new" action, and the user can keep working without dismissing it

## ADDED Requirements

### Requirement: Release notes inside the app
The info action and "What's new", in the update notice and in the "Check for Updates…" result, SHALL open a dialog in the app with the release notes of the new version, formatted (headings, lists, emphasis, links) and scrollable, and with a link to the release page on GitHub.

#### Scenario: Reading what's new
- **WHEN** the notice announces 1.3.0 and the user clicks its info action
- **THEN** a dialog shows the notes of 1.3.0 with their formatting, and the user can open the release page from it

#### Scenario: From the on-demand check
- **WHEN** "Check for Updates…" finds 1.3.0 and the user clicks "What's new"
- **THEN** the same notes dialog opens

### Requirement: Release notes in the user's language
The release notes SHALL be shown in the app's language. When a release has no notes in that language, the app SHALL show its English notes.

#### Scenario: Spanish user
- **WHEN** the app is in Spanish and 1.3.0 has Spanish notes
- **THEN** the dialog shows the Spanish notes

#### Scenario: Language switched with the dialog open
- **WHEN** the notes dialog is open and the user switches the app language
- **THEN** the dialog shows the notes in the new language

#### Scenario: No Spanish notes
- **WHEN** the app is in Spanish and 1.3.0 only has English notes
- **THEN** the dialog shows the English notes

### Requirement: Notes of every version skipped over
When the running version is several releases behind, the dialog SHALL show the notes of every published version newer than the running one, newest first, each under its version number. Pre-releases SHALL NOT be included.

#### Scenario: Two versions behind
- **WHEN** the app runs 1.1.0 and 1.2.0 and 1.3.0 are published
- **THEN** the dialog shows the notes of 1.3.0 and then those of 1.2.0, each headed by its version

#### Scenario: Older versions not repeated
- **WHEN** the app runs 1.2.0 and 1.3.0 is the latest release
- **THEN** the dialog shows only the notes of 1.3.0

### Requirement: Readable notes for older releases
For a release published without hand-written notes, the app SHALL show GitHub's generated list of changes without authors, pull request links or the "Full Changelog" line. When a release has no notes at all, the dialog SHALL say so and keep the link to the release page.

#### Scenario: Generated notes
- **WHEN** a release's body only contains "* Upgrade Compose by @someone in https://github.com/owner/repo/pull/12" and a "Full Changelog" line
- **THEN** the dialog shows "Upgrade Compose" as a list item and nothing else for that release

#### Scenario: Empty notes
- **WHEN** a release's body is empty
- **THEN** the dialog says there are no notes for that version and offers the release page
