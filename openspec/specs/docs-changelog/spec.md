# docs-changelog Specification

## Purpose
Gives the documentation site a changelog page, in English and Spanish, built from the notes written for each release, so readers see what every version brought without anyone maintaining a separate history.

## Requirements

### Requirement: Changelog generated from the release notes
The documentation site SHALL have a changelog page built from the English notes files of `.github/release-notes/` every time the site is built, with one section per release titled with its version. The page SHALL NOT have a source file that has to be edited by hand.

#### Scenario: Notes of a new release
- **WHEN** the release branch of 0.9.6 adds `.github/release-notes/0.9.6.md` and the site is built
- **THEN** the changelog page has a 0.9.6 section with the content of that file, with no other file edited

#### Scenario: No notes at all
- **WHEN** the site is built and `.github/release-notes/` holds no notes file
- **THEN** the changelog page is still built and says that no release has notes yet

### Requirement: Newest release first
The changelog page SHALL list releases from the newest version to the oldest, comparing each part of the version as a number.

#### Scenario: Two-digit minor version
- **WHEN** notes exist for 0.9.5, 0.10.0 and 0.9.10
- **THEN** the page lists 0.10.0, then 0.9.10, then 0.9.5

### Requirement: Changelog in the reader's language
The Spanish version of the site SHALL have its own changelog page showing each release's Spanish notes file, and SHALL show the English notes of a release that has no Spanish file. A release with only a Spanish file SHALL NOT be listed in either language.

#### Scenario: Spanish notes present
- **WHEN** a reader opens the Spanish changelog page and 0.9.5 has `0.9.5.es.md`
- **THEN** the 0.9.5 section shows the content of `0.9.5.es.md`

#### Scenario: Spanish notes missing
- **WHEN** a reader opens the Spanish changelog page and 0.9.6 has `0.9.6.md` but no `0.9.6.es.md`
- **THEN** the 0.9.6 section shows the English notes

#### Scenario: Only Spanish notes
- **WHEN** 0.9.7 has `0.9.7.es.md` but no `0.9.7.md`
- **THEN** neither changelog page lists 0.9.7

### Requirement: Link to each GitHub release
Each release section SHALL link to that release's GitHub page, and the page SHALL link to the full list of GitHub releases for the versions published before release notes existed.

#### Scenario: Release link
- **WHEN** a reader looks at the 0.9.5 section
- **THEN** it links to the GitHub release tagged `v0.9.5`

#### Scenario: Older releases
- **WHEN** a reader looks for a version older than the oldest one with notes
- **THEN** the page points them to the repository's list of GitHub releases

### Requirement: Notes nested under their release
The headings inside each release's notes SHALL appear below that release's heading in the page outline, keeping their relative levels, so every release's groups (for example "What's new" and "Fixes") belong to it. Lines inside code blocks SHALL be kept unchanged.

#### Scenario: Notes with group headings
- **WHEN** the 0.9.5 notes start with a `## What's new` heading
- **THEN** on the changelog page "What's new" is a subheading of the 0.9.5 section

### Requirement: Only release notes files are read
The changelog SHALL only read files in `.github/release-notes/` named `X.Y.Z.md` or `X.Y.Z.es.md` with a numeric version, and SHALL ignore any other file there.

#### Scenario: Unrelated file
- **WHEN** `.github/release-notes/` also holds a `README.md`
- **THEN** the changelog page has no section for it and the build does not fail

### Requirement: Live preview while editing notes
While the documentation is served locally for preview, editing, adding or removing a notes file SHALL rebuild the changelog page.

#### Scenario: Editing notes while previewing
- **WHEN** the maintainer runs `mkdocs serve` and edits `.github/release-notes/0.9.6.md`
- **THEN** the served changelog page shows the edited notes without restarting the server
