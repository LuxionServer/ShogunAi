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
Creating a worktree (on a new or an existing branch) and provisioning an existing worktree SHALL, once the repository's approval has been settled under the asking rules of the entry point, resolve the worktree's environment, reserving its ports, and write the environment file when one is declared and the repository is approved. While the repository isn't approved, the declared variables and the environment file SHALL be left out (see `worktree-environment`). During creation, a failure to reserve ports or to write the file SHALL remove the new worktree, as a failed copy does; when provisioning, the worktree SHALL be left in place.

#### Scenario: Creation reserves ports
- **WHEN** the repository declares `{ "env": { "PORT": "${port}" } }`, is approved, and a worktree is created
- **THEN** the new worktree has a reserved port before creation finishes

#### Scenario: Environment file on creation
- **WHEN** the repository declares `envFile`, is approved, and a worktree is created
- **THEN** the new worktree contains the environment file

#### Scenario: Writing fails during creation
- **WHEN** writing the environment file fails after the worktree was added
- **THEN** the worktree is removed, best-effort, and creation fails with the error

#### Scenario: Provisioning an existing worktree
- **WHEN** a worktree created by another tool is provisioned in an approved repository that declares `env` and `envFile`
- **THEN** its ports are reserved and the environment file is written

#### Scenario: Approved during creation
- **WHEN** the repository declares `PORT` and `envFile`, isn't approved, and the user approves it when asked during creation
- **THEN** the new worktree has a reserved port for `PORT` and the environment file

#### Scenario: Skipped during creation
- **WHEN** the repository declares `PORT` and `envFile`, isn't approved, and the user skips it
- **THEN** the worktree is created without a port for `PORT` nor an environment file, and the result names `PORT` as skipped

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
The system SHALL read `providers.xcode` in `.shogunai.json` as an object with the optional booleans `compilationCache` and `cocoaPods`, each false when absent. A non-object value or a non-boolean `compilationCache` or `cocoaPods` SHALL make the file invalid.

#### Scenario: Provider with compilation caching
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": { "compilationCache": true } } }`
- **THEN** the repository declares the Xcode provider with compilation caching

#### Scenario: Provider with CocoaPods
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": { "cocoaPods": true } } }`
- **THEN** the repository declares the Xcode provider with CocoaPods and without compilation caching

#### Scenario: Provider without options
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": {} } }`
- **THEN** the repository declares the Xcode provider without compilation caching or CocoaPods

#### Scenario: Invalid declaration
- **WHEN** `providers.xcode` is `true`, its `compilationCache` is `"yes"` or its `cocoaPods` is `1`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`

### Requirement: Write providers and environment into the repository file
The system SHALL write the `providers`, `env` and `envFile` fields of `.shogunai.json` from the project settings, creating the file when it doesn't exist. Every other field, every `copy` pattern and the options of unknown providers SHALL be kept unchanged, and the file SHALL be replaced atomically.

#### Scenario: Keep other fields
- **WHEN** `.shogunai.json` contains `copy` patterns, an unknown field `"team": "mobile"` and `"providers": { "cocoapods": {} }`, and the user turns on Gradle and saves
- **THEN** the file keeps the patterns, `"team": "mobile"` and `providers.cocoapods`, and adds `providers.gradle`

#### Scenario: Create the file
- **WHEN** the repository has no `.shogunai.json` and the user adds an environment variable and saves
- **THEN** `.shogunai.json` is created with only the `env` field

#### Scenario: Write fails
- **WHEN** writing `.shogunai.json` fails
- **THEN** the app shows an error, keeps the settings open with the edits, and the project's own settings aren't saved either

### Requirement: Never overwrite an unreadable repository file
The system SHALL NOT write `.shogunai.json` when the existing file couldn't be read when the settings were opened, or when its contents changed on disk since then.

#### Scenario: Edited on disk meanwhile
- **WHEN** the user edits the repository configuration, another tool changes `.shogunai.json`, and the user saves
- **THEN** the app doesn't write the file, says it changed on disk, and offers to reload the section

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
After a worktree's files are in place and its environment prepared, the system SHALL run the post-creation commands in order, each as argv with the worktree as working directory, stopping at the first that fails, only when the repository is approved on this machine (see `repository-trust`); an unapproved repository follows the asking rules of the entry point, asked once for its commands and its declared variables before the environment is prepared. A failure SHALL report the command, its exit code and its error output, and SHALL NOT remove the worktree.

#### Scenario: Commands succeed
- **WHEN** the repository declares `[["npm", "install"]]`, the commands are approved and a worktree is created
- **THEN** `npm install` runs inside the new worktree after the declared files were copied, and creation succeeds

#### Scenario: Commands not approved
- **WHEN** the repository declares `[["npm", "install"]]`, the commands aren't approved and the user doesn't approve them
- **THEN** the worktree is created with its files and the environment of its providers, `npm install` doesn't run, and the result says the commands were skipped

#### Scenario: Command fails during creation
- **WHEN** the first of two approved post-creation commands exits with code 1 while creating a worktree
- **THEN** the second command doesn't run, creation fails with an error naming the command, its exit code and its error output, and the worktree and its branch are kept

#### Scenario: Command can't be started
- **WHEN** an approved post-creation command names a program that isn't installed
- **THEN** creation fails with an error naming the command, and the worktree is kept

#### Scenario: Provisioning an existing worktree
- **WHEN** a worktree created by another tool is provisioned in a repository that declares approved post-creation commands
- **THEN** the commands run inside that worktree after its files are copied, and a failure fails provisioning

#### Scenario: One question for commands and variables
- **WHEN** the repository declares commands and `env`, isn't approved, and a worktree is created on a terminal
- **THEN** the user is asked once, and the answer applies to both the commands and the variables

### Requirement: Declare linked files
The system SHALL read the optional `link` field of `.shogunai.json` as a list of patterns with the same syntax and file selection as `copy`. A `link` value that isn't a list of strings SHALL make the file invalid, with the same error and effects as an invalid `copy` field.

#### Scenario: Link patterns
- **WHEN** `.shogunai.json` contains `{ "link": ["/local.properties", "keys/"] }`
- **THEN** the repository's linked patterns are `/local.properties` and `keys/`

#### Scenario: Invalid link field
- **WHEN** `.shogunai.json` contains `{ "link": "local.properties" }` and the user creates a worktree
- **THEN** creation fails with an error that names `.shogunai.json` and no worktree or branch is created

### Requirement: Link files instead of copying them
For each untracked file of the base repository matching a `link` pattern, the system SHALL create, at the same relative path in the worktree, a symbolic link to the base repository's file, in every creation flow and when provisioning an existing worktree. A file matching both `link` and `copy` SHALL be linked. A local files entry SHALL follow its own mode instead: an entry in copy mode is copied even when a `link` pattern matches it.

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

#### Scenario: Local entry in copy mode
- **WHEN** `.env` matches a `link` pattern and is also a local files entry in copy mode
- **THEN** the worktree gets a copy of `.env`

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

### Requirement: Provisioned files stay inside the worktree
Before copying or linking a file into a worktree, the system SHALL resolve the real location of its destination's parent folder, following symbolic links, and SHALL refuse the file when that location isn't inside the worktree's real folder. A refused file SHALL fail the operation with an error naming the file, and SHALL NOT replace or delete anything at the outside location.

#### Scenario: Symlinked folder on the branch
- **WHEN** the base repository has the ignored file `config/.env`, matching a `copy` pattern, and the new worktree's branch tracks `config` as a symbolic link to a folder outside the worktree
- **THEN** `config/.env` isn't copied, nothing is written in the outside folder, and the operation fails with an error naming `config/.env`

#### Scenario: Regular folder
- **WHEN** the destination's parent is a regular folder inside the worktree
- **THEN** the file is copied or linked as before

### Requirement: Copy local folders
For each folder in the project's local files that is in copy mode, the system SHALL copy every file under it, at any depth and including hidden files, to the same relative path in the worktree, creating the folders it needs. The folder SHALL be read each time a worktree is created or provisioned, so files added to it later are included. Its files are copied whether or not Git tracks them.

#### Scenario: Nested and hidden files
- **WHEN** the local files include `keys` and the base repository's `keys` holds `a.pem`, `.hidden` and `ci/b.pem`
- **THEN** the new worktree contains `keys/a.pem`, `keys/.hidden` and `keys/ci/b.pem` with the same content

#### Scenario: File added to the folder later
- **WHEN** a file is added to a local folder after the project was saved and a worktree is created
- **THEN** the new worktree contains that file

#### Scenario: Folder already exists in the worktree
- **WHEN** the worktree already holds `keys/a.pem` with other content and `keys/old.pem`, and the base repository's `keys` holds `a.pem`
- **THEN** `keys/a.pem` is overwritten with the base repository's content and `keys/old.pem` is kept

#### Scenario: Symbolic links inside the folder
- **WHEN** a local folder holds a symbolic link to a file or folder
- **THEN** the link is skipped, nothing it points to is copied, and the link is reported as skipped

#### Scenario: Empty folder
- **WHEN** a local folder holds no files
- **THEN** creation succeeds and nothing is copied for it

#### Scenario: Provisioning an existing worktree
- **WHEN** an existing linked worktree is provisioned and a local folder is among the local files
- **THEN** the folder's files are copied into it, overwriting those that already exist

#### Scenario: Drift check on a local folder
- **WHEN** the provisioning of a worktree is checked and a file of a local folder is missing from it or differs from the base repository's
- **THEN** that file is reported with its path relative to the repository root, like any other copied file

### Requirement: Declare the Maven provider
The system SHALL read `providers.maven` of `.shogunai.json` as an object with the optional field `mavenMaxHeap`, a memory size such as `1g` or `512m`. A non-object value or an invalid size SHALL make the file invalid with an error that names `.shogunai.json` and the field.

#### Scenario: Maven declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "maven": { "mavenMaxHeap": "1g" } } }`
- **THEN** the repository declares the Maven provider with a heap of `1g`

#### Scenario: Maven without options
- **WHEN** `providers.maven` is `{}`
- **THEN** the repository declares the Maven provider without a heap

#### Scenario: Invalid heap
- **WHEN** `mavenMaxHeap` is `1 GB` or `1000`
- **THEN** the file is reported as invalid with an error that names the field

### Requirement: Declare the Docker Compose provider
The system SHALL read `providers.docker-compose` of `.shogunai.json` as an object with the optional field `portVariables`, a list of unique, valid and non-reserved variable names. Any other shape SHALL make the file invalid with an error that names `.shogunai.json` and the field.

#### Scenario: Compose declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "docker-compose": { "portVariables": ["DB_PORT"] } } }`
- **THEN** the repository declares the Compose provider with the port variable `DB_PORT`

#### Scenario: Not a list
- **WHEN** `portVariables` is `"DB_PORT"`
- **THEN** the file is reported as invalid with an error that names the field

#### Scenario: Duplicate name
- **WHEN** `portVariables` is `["DB_PORT", "DB_PORT"]`
- **THEN** the file is reported as invalid with an error that names the variable

#### Scenario: Written back
- **WHEN** a repository declaring both new providers is saved from the settings
- **THEN** `.shogunai.json` keeps their options and every other field it had

### Requirement: Link local entries
The system SHALL give each local files entry a mode, copy or link, copy when not set. A local file in link mode SHALL become, in every creation flow and when provisioning an existing worktree, a symbolic link to the base repository's file, replacing a regular file at that path, and SHALL follow the fallback to a copy when links aren't available.

#### Scenario: Linked local file
- **WHEN** the local files include `local.properties` in link mode and a worktree is created
- **THEN** the worktree's `local.properties` is a symbolic link to the base repository's file

#### Scenario: Entry saved by an earlier version
- **WHEN** a project saved by an earlier version lists `local.properties` as a local file
- **THEN** it's in copy mode and is copied as before

#### Scenario: Link not available
- **WHEN** a local file in link mode is provisioned on Windows by a user who can't create symbolic links
- **THEN** it's copied and reported as copied instead of linked

### Requirement: Link local folders
A local folder in link mode SHALL become a single symbolic link, at the folder's path in the worktree, to the base repository's folder, so files added to or deleted from the base folder are seen in the worktree at once. An existing symbolic link at that path SHALL be replaced. When links aren't available, the folder's files SHALL be copied as in copy mode and reported as copied instead of linked.

#### Scenario: Linked folder
- **WHEN** the local files include `keys` in link mode and a worktree is created
- **THEN** the worktree's `keys` is a symbolic link to the base repository's `keys`

#### Scenario: File deleted from a linked folder
- **WHEN** a file is deleted from the base repository's linked `keys` after the worktree was created
- **THEN** the worktree no longer sees that file, without being provisioned again

#### Scenario: Link not available for a folder
- **WHEN** a local folder in link mode is provisioned where symbolic links can't be created
- **THEN** its files are copied, each reported as copied instead of linked

### Requirement: Switch a local folder between copy and link
When a linked folder's destination is a real folder in the worktree, the system SHALL replace it with the link only if every file under it is one ShogunAi recorded as copied from that folder; otherwise the operation SHALL fail naming the folder and SHALL NOT delete anything. When a copied folder's destination is a symbolic link to the base folder, the system SHALL remove that link before copying.

#### Scenario: From copy to link
- **WHEN** a worktree holds `keys/a.pem`, recorded as copied from the local folder `keys`, and the entry is switched to link mode and provisioned
- **THEN** the worktree's `keys` becomes a symbolic link to the base repository's `keys`

#### Scenario: Folder holds the worktree's own files
- **WHEN** a linked folder entry `keys` is provisioned and the worktree's `keys` holds a file ShogunAi didn't copy, such as one tracked by the branch
- **THEN** the operation fails naming `keys`, and every file in the worktree's `keys` is kept

#### Scenario: From link to copy
- **WHEN** the worktree's `keys` is a symbolic link to the base folder and the entry is switched to copy mode and provisioned
- **THEN** the link is removed, `keys` is a real folder with copies of the base folder's files, and nothing is written into the base folder

### Requirement: Patterns in the local files
A local files entry containing `*`, `?` or `[` SHALL be a pattern in Git's ignore-pattern syntax, relative to the repository root, that selects the untracked files of the base repository it matches, as a `copy` pattern does, provisioned in the entry's mode. A pattern that matches no file SHALL NOT fail creation or provisioning.

#### Scenario: Pattern of keystores
- **WHEN** the local files include the pattern `*.jks` and the base repository has the ignored files `release.jks` and `app/debug.jks`
- **THEN** the new worktree contains copies of `release.jks` and `app/debug.jks`

#### Scenario: Tracked file not selected
- **WHEN** the local files include `*.properties` and `gradle.properties` is tracked by Git
- **THEN** `gradle.properties` isn't overwritten; the worktree keeps its branch's version

#### Scenario: Pattern without matches
- **WHEN** the local files include `*.p12` and no file matches
- **THEN** creation succeeds without copying anything for it

#### Scenario: Linked pattern
- **WHEN** the local files include `*.jks` in link mode
- **THEN** each matched file becomes a symbolic link to the base repository's file

### Requirement: Report the symbolic links skipped in local folders
Creating a worktree and provisioning an existing one SHALL report each symbolic link skipped inside a local folder in copy mode, with its path relative to the repository root. Skipping a link SHALL NOT fail the operation.

#### Scenario: Link inside a folder
- **WHEN** the base repository's local folder `keys` holds `a.pem` and a symbolic link `shared.pem`, and the worktree is provisioned
- **THEN** `keys/a.pem` is copied and `keys/shared.pem` is reported as skipped

#### Scenario: No links
- **WHEN** no local folder holds a symbolic link
- **THEN** no skipped link is reported

### Requirement: Remember the files copied from local folders
The system SHALL record, for each worktree and outside its working tree, the files it copied from each local folder, replacing a folder's record each time the worktree is created or provisioned and dropping the record of folders no longer in the local files. A failure to write the record SHALL be handled like a failed copy. A missing or unreadable record SHALL be treated as empty.

#### Scenario: Record written
- **WHEN** a worktree is created with the local folder `keys` holding `a.pem` and `ci/b.pem`
- **THEN** the worktree's record lists `a.pem` and `ci/b.pem` for `keys`, and `git status` in the worktree shows no new file

#### Scenario: Folder removed from the local files
- **WHEN** `keys` is removed from the local files and the worktree is provisioned
- **THEN** the record no longer lists `keys` and the worktree's `keys` files are kept

### Requirement: Remove files deleted from a local folder
Before copying a local folder, the system SHALL delete from the worktree each file recorded as copied from that folder that no longer exists in the base folder, when it's a regular file in the worktree. Files the record doesn't list SHALL NOT be deleted. A base folder that doesn't exist SHALL still fail with its missing-entry error before anything is deleted.

#### Scenario: File deleted from the base folder
- **WHEN** `keys/old.pem` was copied into a worktree, is later deleted from the base repository's `keys`, and the worktree is provisioned
- **THEN** `keys/old.pem` is deleted from the worktree and reported as removed

#### Scenario: Worktree's own file
- **WHEN** the worktree's `keys` holds `notes.txt`, which ShogunAi never copied
- **THEN** provisioning keeps `notes.txt`

#### Scenario: Base folder missing
- **WHEN** the base repository's `keys` folder no longer exists and the worktree is provisioned
- **THEN** provisioning fails with the missing-entry error and no file of the worktree's `keys` is deleted

#### Scenario: Worktree provisioned before the record existed
- **WHEN** a worktree has no record and is provisioned
- **THEN** no file is deleted, and the record is written for the next time

### Requirement: Report files deleted from the base folder as drift
Comparing a worktree's provisioned files SHALL also report, as deleted in base, each file recorded as copied from a listed local folder that is still in the worktree but no longer in the base folder. A linked local file or folder SHALL be reported as missing when absent and as not linked when it isn't a symbolic link to the base repository's entry.

#### Scenario: Deleted in base
- **WHEN** `keys/old.pem` was copied into a worktree and later deleted from the base folder
- **THEN** checking the worktree reports `keys/old.pem` as deleted in base and deletes nothing

#### Scenario: Linked folder replaced by a real folder
- **WHEN** `keys` is a local folder in link mode and the worktree's `keys` is a real folder
- **THEN** `keys` is reported as not linked
