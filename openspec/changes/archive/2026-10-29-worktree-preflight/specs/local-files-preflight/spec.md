# Spec Delta

## Purpose

Makes creating or provisioning a worktree check, before it changes anything, that every local file it has to copy or link is available in the base folder, and report each one that isn't with where it was looked for and how to fix it.

## ADDED Requirements

### Requirement: Check local files before acting
Before creating a worktree from a new or existing branch, and before provisioning an existing worktree, the system SHALL check every local file it will copy or link from the base folder: exact entries of the project's local files, the files of copied local folders, and every file matched by a pattern of the project's local files, `.shogunai.json` or `.worktreeinclude`. The check SHALL run before any Git command that creates a branch or a worktree and before any file is copied, linked or deleted.

#### Scenario: Pattern-matched file can't be copied
- **WHEN** `.shogunai.json` copies `*.properties`, the base folder's `keys.properties` is a symbolic link to a file that doesn't exist, and a worktree is created
- **THEN** creation fails without running `git worktree add`, no branch or folder is created, and no file is copied

#### Scenario: Every file is available
- **WHEN** every local file to copy or link exists and can be read
- **THEN** the worktree is created and provisioned as before

#### Scenario: Provisioning an existing worktree
- **WHEN** a file to copy into an existing worktree can't be read and the worktree is provisioned
- **THEN** provisioning fails before copying, linking or deleting any file of the worktree

### Requirement: What makes a local file unavailable
A local file SHALL be unavailable when it doesn't exist in the base folder, when it is a symbolic link whose target doesn't exist, or when it can't be opened for reading. An entry inside a copied local folder that can't be read SHALL be unavailable instead of being skipped. A pattern that matches no file SHALL NOT make anything unavailable, and a symbolic link inside a copied local folder SHALL keep being skipped and reported as before.

#### Scenario: Missing exact entry
- **WHEN** the project's local files include `local.properties` and the base folder has no such file
- **THEN** `local.properties` is unavailable because it wasn't found

#### Scenario: Broken symbolic link
- **WHEN** a file to copy is a symbolic link to `/missing/keys.properties`, which doesn't exist
- **THEN** the file is unavailable as a broken link, naming `/missing/keys.properties`

#### Scenario: Unreadable file
- **WHEN** a file to copy exists but reading it is denied
- **THEN** the file is unavailable because it can't be read, with the system's reason

#### Scenario: Unreadable entry of a local folder
- **WHEN** the local folder `keys` holds a subfolder `private` that can't be read
- **THEN** `keys/private` is unavailable, and the worktree isn't created without it

#### Scenario: Pattern that matches nothing
- **WHEN** the local files include `*.p12` and no file matches it
- **THEN** nothing is unavailable because of it

### Requirement: Report every unavailable local file
When local files are unavailable, the operation SHALL fail with `WorktreeError.LocalFilesUnavailable`, listing every unavailable file at once with its path relative to the base folder, the full path where it was looked for, why it is unavailable, and whether it comes from the project's local files or from the repository's `.shogunai.json` or `.worktreeinclude`. The error SHALL say that nothing was changed.

#### Scenario: Several problems at once
- **WHEN** `local.properties` is missing and `keys.properties` is a broken link
- **THEN** a single error lists both files, each with its reason and full path

### Requirement: Say how to fix each unavailable file
The error's text SHALL give, for each unavailable file, the fix for its reason: create a missing file at the path shown; restore a broken link's target or replace the link with the file; make an unreadable file readable for the user. It SHALL also offer removing the file from where it is declared: the project's local files, or the patterns of `.shogunai.json` or `.worktreeinclude`.

#### Scenario: Command-line output
- **WHEN** `shogun new TASK-123` fails because `keys.properties`, matched by `.shogunai.json`, is a broken link
- **THEN** it prints an error with one line for `keys.properties` naming the link's missing target, telling to restore it or replace the link with the file, or to stop matching it in `.shogunai.json` or `.worktreeinclude`, and saying nothing was changed

#### Scenario: Desktop app
- **WHEN** creating a worktree from the app fails because local files are unavailable
- **THEN** the dialog shows, in the selected language, a summary naming the files and the base folder, a next step built from the reasons found, technical details with one line per file, and the action that opens the project's settings
