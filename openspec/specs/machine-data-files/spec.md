# machine-data-files Specification

## Purpose
Defines how ShogunAi keeps its own files in the user's `~/.shogunai` folder so that the desktop app and the CLI, running at once and possibly at different versions, never lose or silently replace each other's data.

## Requirements

### Requirement: Machine files covered
The rules of this capability SHALL apply to `projects.json`, `settings.json`, `trust.json` and `multirepos.json` in the user's ShogunAi folder.

#### Scenario: Covered file
- **WHEN** the desktop app or the CLI writes `trust.json`
- **THEN** the write follows the schema version, compatibility, concurrency and unreadable-file rules of this capability

### Requirement: Schema version of each file
A file whose root is a JSON object SHALL carry its schema version as `"schemaVersion"` at its root, written on every write. A file without it SHALL be read as version 1. `projects.json` SHALL stay a JSON array, read and written as version 1, so released versions keep reading it.

#### Scenario: File written before versions existed
- **WHEN** `settings.json` was written by an earlier version and has no `schemaVersion`
- **THEN** it is read as version 1 with every setting it holds, and the next write adds `"schemaVersion": 1`

#### Scenario: Projects file
- **WHEN** a project is saved
- **THEN** `projects.json` is still a JSON array of projects with no version field

### Requirement: Migrate older files on read
When a file's schema version is lower than the version this release writes, the system SHALL upgrade its contents step by step, one version at a time and in order, before using them, and SHALL write the current version on the next write.

#### Scenario: File two versions behind
- **WHEN** a file is at version 1 and this release writes version 3
- **THEN** the upgrade from 1 to 2 runs before the upgrade from 2 to 3, and the next write stores version 3

### Requirement: Keep fields this version doesn't know
Rewriting a file SHALL keep the fields this version doesn't know, inside objects it knows and inside map entries it keeps, and inside list items that carry the same `id`. A field this version knows SHALL take the value it writes, and an entry it removes SHALL stay removed.

#### Scenario: Setting added by a newer version
- **WHEN** a newer version stored `"newOption": true` in `settings.json` and this version changes the theme
- **THEN** the file keeps `"newOption": true` together with the new theme

#### Scenario: Field added to a project by a newer version
- **WHEN** a project in `projects.json` has a field this version doesn't know and the user saves another project
- **THEN** that project keeps the field

#### Scenario: Removed entry
- **WHEN** a revoked approval in `trust.json` had a field this version doesn't know
- **THEN** the approval is removed with its fields

### Requirement: Files written by a newer version
When a file's schema version is higher than the version this release writes, the system SHALL read what it understands, SHALL NOT write the file, and SHALL fail any change to it with an error that names the file and says it was written by a newer version of ShogunAi. If its contents can't be read, it SHALL be reported as unreadable without being moved aside.

#### Scenario: Saving a project over a newer file
- **WHEN** `projects.json` was written at a higher version and the user saves a project
- **THEN** the save fails saying the file was written by a newer version of ShogunAi, and the file is unchanged

#### Scenario: Newer settings file
- **WHEN** `settings.json` is at a higher version
- **THEN** the app reads the settings it understands and doesn't change the file

### Requirement: Unreadable files are kept before being replaced
A file that exists but can't be read or decoded SHALL be reported as unreadable, naming the file. Before a write replaces it, the system SHALL keep it next to it as `<file>.unreadable-<timestamp>` and report the problem, so it is never replaced silently.

#### Scenario: Corrupted multirepo registry
- **WHEN** `multirepos.json` isn't valid JSON and the user adds a multirepo
- **THEN** the old file is kept as `multirepos.json.unreadable-<timestamp>` and the new registry holds the added multirepo

### Requirement: Concurrent writers keep every change
Every read and every read-modify-write of a machine file SHALL run under a lock shared by the desktop app and the CLI, reading the latest contents before changing them. When the lock can't be taken, the operation SHALL fail naming the file instead of running unlocked. Writes SHALL be atomic, so a reader never sees a half-written file.

#### Scenario: App and CLI writing at once
- **WHEN** the app and the CLI each add an entry to the same file at the same time
- **THEN** the file holds both entries afterwards

#### Scenario: Lock file can't be created
- **WHEN** the lock file next to `settings.json` can't be created
- **THEN** the operation fails reporting that `settings.json` can't be locked, and nothing is written
