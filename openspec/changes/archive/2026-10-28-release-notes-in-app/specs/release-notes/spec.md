# Spec Delta

## Purpose

Defines how each ShogunAi release's notes are written for users, in English and Spanish, and published in the GitHub release so that both people and the desktop app can read them.

## ADDED Requirements

### Requirement: Bilingual notes per release
Each release SHALL have its notes written by hand in two files of the repository, one in English and one in Spanish, named after the version it releases. The notes SHALL describe what changes for the user in plain language, without pull request numbers, authors or internal names.

#### Scenario: Notes for a release
- **WHEN** the maintainer prepares release 0.9.4
- **THEN** the release branch contains the English notes for 0.9.4 and their Spanish translation, each in its own file named after 0.9.4

### Requirement: Notes published in the release
The release workflow SHALL publish the notes as the GitHub release body: the English notes visible, and the Spanish notes in a collapsible section. Each language SHALL be enclosed in markers that do not show on the rendered page, so a reader can extract each language on its own.

#### Scenario: Release page
- **WHEN** release 0.9.4 is published with both notes files
- **THEN** its GitHub page shows the English notes and a collapsed "Español" section with the Spanish notes, and no marker is visible

#### Scenario: Reading one language
- **WHEN** a reader extracts the Spanish notes from the body of release 0.9.4
- **THEN** it gets exactly the content of the Spanish notes file

### Requirement: Missing notes don't block a release
When the notes files of the version being released are missing, the workflow SHALL publish the release with GitHub's generated notes and SHALL log a warning, instead of failing.

#### Scenario: Forgotten notes
- **WHEN** release 0.9.4 is merged without its notes files
- **THEN** the release is published with generated notes and the workflow run shows a warning naming the missing files

#### Scenario: Only one language written
- **WHEN** release 0.9.4 has the English notes file but not the Spanish one
- **THEN** the release body contains the English notes, and the workflow logs a warning naming the missing Spanish file
