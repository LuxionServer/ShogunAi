## MODIFIED Requirements

### Requirement: Confirm before discarding unsaved changes
The system SHALL ask for confirmation before discarding unsaved edits whenever the user leaves the project settings while any field differs from its initial value: by selecting another entry in the sidebar, through a shortcut or menu entry that changes the view, or by closing the window. Activating "Discard" in the save bar SHALL discard the edits and return to the previous view without asking again.

#### Scenario: Cancel with unsaved changes
- **WHEN** the user has changed at least one field and activates "Discard" in the save bar
- **THEN** the app discards the changes and returns to the previous view without showing a confirmation dialog

#### Scenario: Cancel without changes
- **WHEN** the user leaves the settings without having changed any field
- **THEN** the app shows the chosen view immediately without showing a confirmation dialog

#### Scenario: Leave through the sidebar
- **WHEN** the user has changed a field and selects another project in the sidebar
- **THEN** the app asks for confirmation, shows the other project only if the user confirms, and keeps the edits otherwise

#### Scenario: Close the window
- **WHEN** the user has changed a field and closes the main window
- **THEN** the app asks for confirmation before quitting

### Requirement: Show the repository's provisioning
The project configuration screen SHALL show, read-only, the patterns the base repository declares in `.shogunai.json` (`copy` and `link`) and in `.worktreeinclude`, each with the file it comes from and `link` patterns marked as linked, and SHALL refresh them when the base repository path changes. When the repository declares post-creation commands, the screen SHALL say how many there are and where they're listed. When `.shogunai.json` can't be read, the screen SHALL say so instead of listing its patterns.

#### Scenario: Repository with both files
- **WHEN** the user opens the configuration of a project whose repository has `.shogunai.json` with `local.properties` and `.worktreeinclude` with `.env`
- **THEN** the screen lists `local.properties` from `.shogunai.json` and `.env` from `.worktreeinclude`

#### Scenario: Linked patterns
- **WHEN** the repository's `.shogunai.json` declares `node_modules` under `link`
- **THEN** the screen lists `node_modules` from `.shogunai.json`, marked as linked

#### Scenario: Only post-creation commands
- **WHEN** the repository's `.shogunai.json` declares two post-creation commands and no patterns
- **THEN** the screen says two commands run after creation and points to the repository configuration section, and doesn't say the repository declares nothing

#### Scenario: Repository without provisioning files
- **WHEN** the base repository has neither file
- **THEN** the screen says the repository declares nothing to copy

#### Scenario: Base repository path changes
- **WHEN** the user changes the base repository path in the form to another repository
- **THEN** the listed patterns are those of the new repository

#### Scenario: Unreadable repository file
- **WHEN** the repository's `.shogunai.json` is malformed
- **THEN** the screen shows that the file can't be read

## ADDED Requirements

### Requirement: Settings title names the project
The project settings view of an existing project SHALL show a title that names the project as saved, and SHALL keep the "Add project" title while adding one.

#### Scenario: Existing project
- **WHEN** the user opens the settings of the project `main-repo`
- **THEN** the title reads "Settings · main-repo"

#### Scenario: Name edited but not saved
- **WHEN** the user changes the name field to `other-name` without saving
- **THEN** the title still names `main-repo`
