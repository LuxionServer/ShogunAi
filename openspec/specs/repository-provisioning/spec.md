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
- **WHEN** `providers` contains `{ "gradle": {}, "cocoapods": {} }`
- **THEN** the repository declares the Gradle provider and `cocoapods` is ignored

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

### Requirement: Declare the emulator provider
The system SHALL read `providers.emulator` in `.shogunai.json` as an object with an optional string `avd`, the AVD the repository prefers. A non-object value or a non-string `avd` SHALL make the file invalid.

#### Scenario: Provider with a preferred AVD
- **WHEN** `.shogunai.json` contains `{ "providers": { "emulator": { "avd": "Pixel_8_API_35" } } }`
- **THEN** the repository declares the emulator provider preferring `Pixel_8_API_35`

#### Scenario: Provider without options
- **WHEN** `.shogunai.json` contains `{ "providers": { "emulator": {} } }`
- **THEN** the repository declares the emulator provider with no preferred AVD

#### Scenario: Invalid declaration
- **WHEN** `providers.emulator` is `true` or its `avd` is `35`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`

### Requirement: Declare the iOS simulator provider
The system SHALL read `providers.ios-simulator` in `.shogunai.json` as an object with an optional string `device` (a device type name such as `iPhone 16`) and an optional string `runtime` (such as `iOS 18.5`). A non-object value or a non-string option SHALL make the file invalid.

#### Scenario: Provider with preferences
- **WHEN** `.shogunai.json` contains `{ "providers": { "ios-simulator": { "device": "iPhone 16", "runtime": "iOS 18.5" } } }`
- **THEN** the repository declares the iOS simulator provider with those preferences

#### Scenario: Provider without options
- **WHEN** `.shogunai.json` contains `{ "providers": { "ios-simulator": {} } }`
- **THEN** the repository declares the provider with no preferences

#### Scenario: Invalid declaration
- **WHEN** `providers.ios-simulator` is `true` or its `device` is `16`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`

### Requirement: Declare the Gradle provider settings
The system SHALL read three optional settings in `providers.gradle`: `daemonMaxHeap` and `kotlinDaemonMaxHeap`, each a string with a JVM memory size (digits followed by `k`, `m` or `g`, in either case), and `idleTimeoutMinutes`, a positive whole number. A setting that is present with another type or format SHALL make the file invalid; a provider without settings declares none.

#### Scenario: All settings declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30, "kotlinDaemonMaxHeap": "1024m" } } }`
- **THEN** the repository declares the Gradle provider with a 2g daemon heap, a 30-minute idle timeout and a 1024m Kotlin daemon heap

#### Scenario: Provider without settings
- **WHEN** `.shogunai.json` contains `{ "providers": { "gradle": {} } }`
- **THEN** the repository declares the Gradle provider with no settings

#### Scenario: Invalid memory size
- **WHEN** `daemonMaxHeap` is `2 GB`, `""` or `2048`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json` and the setting

#### Scenario: Invalid idle timeout
- **WHEN** `idleTimeoutMinutes` is `0`, `-5`, `1.5` or `"30"`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json` and the setting

### Requirement: Declare the Xcode provider
The system SHALL read `providers.xcode` in `.shogunai.json` as an object with an optional boolean `compilationCache`, false when absent. A non-object value or a non-boolean `compilationCache` SHALL make the file invalid.

#### Scenario: Provider with compilation caching
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": { "compilationCache": true } } }`
- **THEN** the repository declares the Xcode provider with compilation caching

#### Scenario: Provider without options
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": {} } }`
- **THEN** the repository declares the Xcode provider without compilation caching

#### Scenario: Invalid declaration
- **WHEN** `providers.xcode` is `true` or its `compilationCache` is `"yes"`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`

### Requirement: Declare post-creation commands
The system SHALL read the optional `hooks.postCreate` field of `.shogunai.json` as a list of commands, each a non-empty list of strings given as argv. A single non-empty list of strings SHALL be read as one command. A repository without the field declares no command.

#### Scenario: Several commands
- **WHEN** `.shogunai.json` contains `{ "hooks": { "postCreate": [["npm", "install"], ["direnv", "allow"]] } }`
- **THEN** the repository declares `npm install` followed by `direnv allow` as post-creation commands

#### Scenario: Single command shorthand
- **WHEN** `.shogunai.json` contains `{ "hooks": { "postCreate": ["npm", "install"] } }`
- **THEN** the repository declares `npm install` as its only post-creation command

#### Scenario: No hooks
- **WHEN** `.shogunai.json` has no `hooks` field, or `hooks` has no `postCreate`
- **THEN** the repository declares no post-creation command and provisioning works as before

### Requirement: Invalid post-creation commands
The system SHALL treat `.shogunai.json` as invalid, with the same error and effects as an invalid `copy` field, when `hooks` isn't an object, `postCreate` isn't a list, or a command is empty or holds anything other than strings.

#### Scenario: Command as a single string
- **WHEN** `.shogunai.json` contains `{ "hooks": { "postCreate": "npm install" } }` and the user creates a worktree
- **THEN** creation fails with an error that names `.shogunai.json` and no worktree or branch is created

#### Scenario: Empty command
- **WHEN** `postCreate` is `[[]]` or `[["npm", 1]]`
- **THEN** the file is reported as invalid with the same error

### Requirement: Run post-creation commands
After a worktree's files are in place and its environment prepared, the system SHALL run the post-creation commands in order, each as argv with the worktree as working directory, stopping at the first that fails. A failure SHALL report the command, its exit code and its error output, and SHALL NOT remove the worktree.

#### Scenario: Commands succeed
- **WHEN** the repository declares `[["npm", "install"]]` and a worktree is created
- **THEN** `npm install` runs inside the new worktree after the declared files were copied, and creation succeeds

#### Scenario: Command fails during creation
- **WHEN** the first of two post-creation commands exits with code 1 while creating a worktree
- **THEN** the second command doesn't run, creation fails with an error naming the command, its exit code and its error output, and the worktree and its branch are kept

#### Scenario: Command can't be started
- **WHEN** a post-creation command names a program that isn't installed
- **THEN** creation fails with an error naming the command, and the worktree is kept

#### Scenario: Provisioning an existing worktree
- **WHEN** a worktree created by another tool is provisioned in a repository that declares post-creation commands
- **THEN** the commands run inside that worktree after its files are copied, and a failure fails provisioning

### Requirement: Declare linked files
The system SHALL read the optional `link` field of `.shogunai.json` as a list of patterns with the same syntax and file selection as `copy`. A `link` value that isn't a list of strings SHALL make the file invalid, with the same error and effects as an invalid `copy` field.

#### Scenario: Link patterns
- **WHEN** `.shogunai.json` contains `{ "link": ["/local.properties", "keys/"] }`
- **THEN** the repository's linked patterns are `/local.properties` and `keys/`

#### Scenario: Invalid link field
- **WHEN** `.shogunai.json` contains `{ "link": "local.properties" }` and the user creates a worktree
- **THEN** creation fails with an error that names `.shogunai.json` and no worktree or branch is created

### Requirement: Link files instead of copying them
For each untracked file of the base repository matching a `link` pattern, the system SHALL create, at the same relative path in the worktree, a symbolic link to the base repository's file, in every creation flow and when provisioning an existing worktree. A file matching both `link` and `copy` SHALL be linked. The project's local files SHALL still be copied.

#### Scenario: Linked secret
- **WHEN** `local.properties` is ignored by Git, matches a `link` pattern and a worktree is created
- **THEN** the worktree's `local.properties` is a symbolic link to the base repository's `local.properties`

#### Scenario: Rotated secret
- **WHEN** the base repository's linked `local.properties` is edited after the worktree was created
- **THEN** the worktree reads the edited content without being provisioned again

#### Scenario: File in both lists
- **WHEN** `.env` matches both a `copy` and a `link` pattern
- **THEN** the worktree gets a symbolic link to it, not a copy

#### Scenario: Existing file replaced by a link
- **WHEN** an existing worktree has a regular `local.properties` and is provisioned with `local.properties` declared in `link`
- **THEN** the regular file is replaced by a symbolic link to the base repository's file

#### Scenario: Link fails during creation
- **WHEN** creating a symbolic link fails for a reason other than the platform not supporting it
- **THEN** creation fails and the worktree is removed, as when a copy fails

### Requirement: Copy when links aren't available
When the platform or file system doesn't allow creating symbolic links, as on Windows without the required privilege, the system SHALL copy the file instead and report which linked files were copied.

#### Scenario: Windows without symbolic link privilege
- **WHEN** a worktree is created on Windows by a user who can't create symbolic links and `.env` matches a `link` pattern
- **THEN** `.env` is copied into the worktree, creation succeeds, and `.env` is reported as copied instead of linked

### Requirement: Compare a worktree's provisioned files
The system SHALL compare, for a linked worktree, each file provisioning would give it with the base repository's, and report each file that is missing, whose content differs, or that should be linked but isn't a symbolic link to the base repository's file. Files that match SHALL NOT be reported.

#### Scenario: Stale copy
- **WHEN** `.env` was copied into a worktree and later changed in the base repository
- **THEN** `.env` is reported as differing

#### Scenario: Missing file
- **WHEN** `local.properties` is declared but the worktree has no such file
- **THEN** `local.properties` is reported as missing

#### Scenario: Link replaced by a regular file
- **WHEN** `local.properties` is declared in `link` but the worktree holds a regular file with the same content
- **THEN** `local.properties` is reported as not linked

#### Scenario: Everything matches
- **WHEN** every declared file of the worktree matches the base repository's
- **THEN** nothing is reported

### Requirement: Keep the new fields when writing
Writing `.shogunai.json`, such as when the user moves a local file to it, SHALL keep the `hooks` and `link` fields unchanged.

#### Scenario: Moving a local file
- **WHEN** the user moves a local file to `.shogunai.json` in a repository whose file declares `hooks` and `link`
- **THEN** both fields are kept unchanged and the moved file is added to `copy`
