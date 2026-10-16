# repository-provisioning Specification

## Purpose
Defines how a repository declares what each new worktree needs from the base repository (`.shogunai.json` and `.worktreeinclude`), and how that declaration is applied when a worktree is created.

## Requirements

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

### Requirement: Provision an existing worktree
The system SHALL apply the project's local files and the repository's provisioning to an existing linked worktree of the repository, with the same file selection as during creation, overwriting files that already exist there. It SHALL NOT remove the worktree when a copy fails.

#### Scenario: Worktree created outside ShogunAi
- **WHEN** a linked worktree was created with `git worktree add` and is provisioned
- **THEN** the base repository's untracked files matching the provisioning patterns, and the local files, are copied into it at the same relative paths

#### Scenario: File already present
- **WHEN** the worktree already has a file that provisioning copies
- **THEN** it's replaced by the base repository's version

#### Scenario: Copy fails
- **WHEN** copying one of the files fails
- **THEN** provisioning fails with the copy error and the worktree is left in place

#### Scenario: Main worktree or unknown path
- **WHEN** the path is the base repository itself or isn't one of its worktrees
- **THEN** provisioning fails without copying anything

### Requirement: Declare providers in the repository
The system SHALL read the optional `providers` field of `.shogunai.json` as an object keyed by provider id, whose values are objects with that provider's options. A repository declares the Gradle provider with `"providers": { "gradle": {} }`. Unknown provider ids SHALL be ignored, and a repository without the field declares no provider.

#### Scenario: Gradle provider declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "gradle": {} } }`
- **THEN** the repository declares the Gradle provider

#### Scenario: No providers
- **WHEN** `.shogunai.json` has no `providers` field, or the repository has no `.shogunai.json`
- **THEN** the repository declares no provider and everything else works as before

#### Scenario: Unknown provider
- **WHEN** `providers` contains `{ "gradle": {}, "xcode": {} }`
- **THEN** the repository declares the Gradle provider and `xcode` is ignored

#### Scenario: Providers kept when writing
- **WHEN** the user moves a local file to `.shogunai.json` in a repository whose file declares providers
- **THEN** the `providers` field is kept unchanged

### Requirement: Invalid provider declaration
The system SHALL treat `.shogunai.json` as invalid, with the same error and effects as an invalid `copy` field, when `providers` isn't an object or a known provider's value isn't an object.

#### Scenario: Providers is not an object
- **WHEN** `.shogunai.json` contains `{ "providers": ["gradle"] }` and the user creates a worktree
- **THEN** creation fails with an error that names `.shogunai.json` and no worktree or branch is created

#### Scenario: Known provider with a wrong value
- **WHEN** `.shogunai.json` contains `{ "providers": { "gradle": true } }`
- **THEN** the file is reported as invalid with the same error

### Requirement: Prepare the environment when provisioning
Creating a worktree (on a new or an existing branch) and provisioning an existing worktree SHALL resolve the worktree's environment, reserving its ports, and write the environment file when one is declared. During creation, a failure to reserve ports or to write the file SHALL remove the new worktree, as a failed copy does; when provisioning, the worktree SHALL be left in place.

#### Scenario: Creation reserves ports
- **WHEN** the repository declares `{ "env": { "PORT": "${port}" } }` and a worktree is created
- **THEN** the new worktree has a reserved port before creation finishes

#### Scenario: Environment file on creation
- **WHEN** the repository declares `envFile` and a worktree is created
- **THEN** the new worktree contains the environment file

#### Scenario: Writing fails during creation
- **WHEN** writing the environment file fails after the worktree was added
- **THEN** the worktree is removed, best-effort, and creation fails with the error

#### Scenario: Provisioning an existing worktree
- **WHEN** a worktree created by another tool is provisioned in a repository that declares `env` and `envFile`
- **THEN** its ports are reserved and the environment file is written
