# Spec Delta

## MODIFIED Requirements

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form, shown as "Local files": exact paths of files or folders, or patterns, stored only on this machine, copied or linked into each new worktree in addition to what the repository declares. The browse dialog SHALL allow selecting files and folders. The system SHALL NOT add an entry that is already present in the list.

#### Scenario: Add a secret file entry
- **WHEN** the user adds a file name to the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the new entry

#### Scenario: Add a folder entry
- **WHEN** the user selects a folder inside the base repository with the browse dialog and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the folder's path relative to the base repository

#### Scenario: Add a pattern entry
- **WHEN** the user types `*.jks` in the local files field and adds it
- **THEN** the list shows `*.jks` marked as a pattern, and it's saved as an entry

#### Scenario: Remove a secret file entry
- **WHEN** the user removes a file name from the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` no longer includes that entry

#### Scenario: Duplicate secret file entry is ignored
- **WHEN** the user adds a file name that is already present in the secret files list
- **THEN** the list is unchanged and no duplicate entry is added

#### Scenario: Duplicate folder entry with a trailing separator is ignored
- **WHEN** the list contains `keys` and the user adds `keys/`
- **THEN** the list is unchanged and no duplicate entry is added

#### Scenario: Existing projects keep their files
- **WHEN** the app loads a project saved by an earlier version with secret files
- **THEN** those entries appear as local files in copy mode and are still copied into new worktrees

## ADDED Requirements

### Requirement: Choose copy or link for each local entry
Each local files entry SHALL show its mode, copy or link, and let the user switch it as an unsaved change. New entries SHALL start in copy mode. Removing an entry SHALL also forget its mode, and changing the base repository path SHALL keep the mode of every entry.

#### Scenario: Link a folder
- **WHEN** the user switches the local entry `keys` to link and saves
- **THEN** the project is saved with `keys` in link mode, and the next worktree gets `keys` as a symbolic link

#### Scenario: Remove a linked entry
- **WHEN** the user removes the linked entry `keys`, adds `keys` again and saves
- **THEN** `keys` is in copy mode

#### Scenario: Base repository moved
- **WHEN** the project has `local.properties` in link mode and the user changes the base repository path and saves
- **THEN** `local.properties` is still in link mode

### Requirement: Patterns in the local files are relative to the repository
The form SHALL treat an entry containing `*`, `?` or `[` as a pattern and SHALL refuse a pattern that is an absolute path, with a message saying patterns are relative to the repository, without adding it.

#### Scenario: Absolute pattern
- **WHEN** the user adds `/Users/me/keys/*.jks`
- **THEN** the entry isn't added and the form says patterns are relative to the repository

### Requirement: Show the symbolic links a local folder skips
Under a local folder entry in copy mode, the form SHALL say how many symbolic links inside the folder will be skipped, and SHALL say nothing when there are none or the entry is linked.

#### Scenario: Folder with links
- **WHEN** the base repository's local folder `keys` holds two symbolic links
- **THEN** the `keys` entry says two symbolic links are skipped

#### Scenario: Linked folder
- **WHEN** the same `keys` entry is switched to link mode
- **THEN** no skipped links are mentioned

### Requirement: Move linked and pattern entries into the repository
"Move to .shogunai.json" SHALL move a linked entry into `link` instead of `copy`, and SHALL move a pattern entry unchanged, not anchored, into the list that matches its mode.

#### Scenario: Linked file and pattern
- **WHEN** the local files are `local.properties` in link mode and `*.jks` in copy mode, the user activates "Move to .shogunai.json" and saves
- **THEN** `.shogunai.json` has `/local.properties` in `link` and `*.jks` in `copy`, and the project has no local files left

### Requirement: Suggest linking the detected mobile secrets
The "Worktree files" section SHALL list each suggested mobile secret with its path and kind and an action to link it from `.shogunai.json`, and each keystore warning with its file, key and value. Using the action SHALL add the anchored pattern to `link` as a pending, unsaved change shown with the repository's patterns, written only when the user saves and confirms the preview. The action SHALL NOT be offered while `.shogunai.json` can't be read.

#### Scenario: Suggestion shown
- **WHEN** the user opens the settings of a project whose base repository has an ignored `app/google-services.json` that nothing provisions
- **THEN** the section suggests linking `app/google-services.json` as Firebase configuration

#### Scenario: Accept a suggestion
- **WHEN** the user links the suggested `app/google-services.json`
- **THEN** `/app/google-services.json` appears among the repository's patterns as pending and linked, the change counts as unsaved, and `.shogunai.json` is unchanged until the user saves

#### Scenario: Keystore warning
- **WHEN** `keystore.properties` contains `storeFile=../signing/release.jks`
- **THEN** the section warns that `storeFile` in `keystore.properties` points outside the repository and won't resolve from a worktree

#### Scenario: Unreadable repository file
- **WHEN** `.shogunai.json` is malformed and a mobile secret is suggested
- **THEN** the suggestion is listed without the action to link it

#### Scenario: Nothing to suggest
- **WHEN** every detected mobile secret is already provisioned and there is no warning
- **THEN** the section shows no mobile secrets suggestion
