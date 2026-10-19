# Spec Delta

## ADDED Requirements

### Requirement: Editing the base path keeps unsaved edits
While the user types the base repository path in project settings, the app SHALL NOT discard unsaved repository settings. It SHALL read the repository at the new path only when the path settles (after a short pause, on leaving the field or after choosing a folder), and SHALL keep the user's unsaved repository edits when the repository is the same.

#### Scenario: Fix a typo in the path
- **WHEN** the user changes the Gradle memory, then edits the base path and returns it to the same repository
- **THEN** the Gradle memory change is still there and the save bar still shows unsaved changes

#### Scenario: Different repository
- **WHEN** the user points the path to another repository that has its own `.shogunai.json`
- **THEN** the app asks whether to discard the unsaved repository edits before loading the other repository's settings

### Requirement: Validate the project form
Saving project settings SHALL require a base path that is the top folder of a Git repository, checked the same way as "Add project", a non-blank worktrees folder and a non-blank agent command, and SHALL reject a repository that another project already uses.

#### Scenario: Subfolder chosen
- **WHEN** the user chooses a subfolder of a repository with Browse in project settings
- **THEN** the form says the folder isn't the repository's top folder and can't be saved

#### Scenario: Repository already added
- **WHEN** the base path is the repository of another project
- **THEN** the form names that project and can't be saved

#### Scenario: Blank agent command
- **WHEN** the agent command is blank
- **THEN** the form says it is required and can't be saved

### Requirement: Preview again after reloading the repository file
After the repository file was reloaded from disk, for example after a "changed on disk" conflict, the next save that writes it SHALL show the preview again.

#### Scenario: Conflict, reload and save
- **WHEN** a save fails because `.shogunai.json` changed on disk, the user reloads it, edits again and saves
- **THEN** the preview dialog is shown before the file is written
