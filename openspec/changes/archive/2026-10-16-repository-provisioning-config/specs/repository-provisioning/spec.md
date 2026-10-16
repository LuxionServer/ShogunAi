# Spec Delta

## Purpose

Defines how a repository declares what each new worktree needs from the base repository (`.shogunai.json` and `.worktreeinclude`), and how that declaration is applied when a worktree is created.

## ADDED Requirements

### Requirement: Repository provisioning file
The system SHALL read an optional `.shogunai.json` file at the root of the base repository. Its `copy` field is a list of patterns that use Git's ignore-pattern syntax and are relative to the repository root. Unknown fields SHALL be ignored, so newer files still load.

#### Scenario: File with copy patterns
- **WHEN** the base repository contains `.shogunai.json` with `{ "copy": ["local.properties", ".env*"] }`
- **THEN** the repository's provisioning patterns are `local.properties` and `.env*`

#### Scenario: No file
- **WHEN** the base repository has no `.shogunai.json`
- **THEN** the repository declares no patterns from that file, and worktree creation works as before

#### Scenario: Unknown fields
- **WHEN** `.shogunai.json` contains fields other than `copy`
- **THEN** they're ignored and the `copy` patterns are still read

### Requirement: Claude Code's worktree include file
The system SHALL read an optional `.worktreeinclude` file at the root of the base repository, one pattern per line in Git's ignore-pattern syntax, skipping blank lines and lines starting with `#`, and use its patterns in addition to those of `.shogunai.json`.

#### Scenario: Patterns from .worktreeinclude
- **WHEN** the base repository contains `.worktreeinclude` with the lines `# secrets`, an empty line and `.env.local`
- **THEN** `.env.local` is a provisioning pattern of the repository

#### Scenario: Both files present
- **WHEN** the base repository has both `.shogunai.json` and `.worktreeinclude`
- **THEN** the patterns of both files apply, and a file matched by both is copied once

### Requirement: Select the files to copy
For each new worktree the system SHALL copy every file of the base repository that is not tracked by Git and matches at least one provisioning pattern, keeping its path relative to the repository root. Tracked files SHALL NOT be copied, because the checkout already brings them.

#### Scenario: Ignored file matched by a pattern
- **WHEN** `local.properties` is ignored by Git, exists in the base repository and matches a pattern
- **THEN** the new worktree contains a copy of it at the same relative path

#### Scenario: Folder pattern
- **WHEN** the pattern is `.idea/runConfigurations/` and that folder holds untracked files in the base repository
- **THEN** each of those files is copied to the same relative path in the new worktree, creating the folders it needs

#### Scenario: Tracked file is not copied
- **WHEN** a pattern matches a file that is tracked by Git
- **THEN** that file is not copied; the worktree has the version from its branch

#### Scenario: Pattern without matches
- **WHEN** a pattern matches no file in the base repository
- **THEN** worktree creation succeeds without copying anything for that pattern

### Requirement: Provisioning applies to every creation flow
The system SHALL apply the repository's provisioning together with the project's local files both when creating a worktree on a new branch and when creating it from an existing branch, with the same rollback as local files: if a copy fails, the new worktree is removed and creation fails.

#### Scenario: Worktree from an existing branch
- **WHEN** the user creates a worktree from an existing branch in a repository whose `.shogunai.json` matches `.env`
- **THEN** the new worktree contains a copy of the base repository's `.env`

#### Scenario: Copy fails
- **WHEN** copying a file selected by a pattern fails after the worktree was added
- **THEN** the worktree is removed, best-effort, and creation fails with the same error as a failed copy of a local file

### Requirement: Invalid repository provisioning file
The system SHALL refuse to create a worktree when `.shogunai.json` exists but can't be read as a provisioning file, with an error naming the file, before running any Git command that creates a branch or a worktree.

#### Scenario: Malformed JSON
- **WHEN** `.shogunai.json` contains invalid JSON and the user creates a worktree
- **THEN** creation fails with an error that names `.shogunai.json` and no worktree or branch is created

#### Scenario: Wrong field type
- **WHEN** `.shogunai.json` has `copy` set to something other than a list of strings
- **THEN** creation fails with the same error and no worktree or branch is created
