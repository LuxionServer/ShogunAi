# Spec Delta

## MODIFIED Requirements

### Requirement: Create a project
The system SHALL let the user create a new project by entering a name, base repository path, worktrees root path, and a list of secret files, matching the fields of `ProjectConfig`. Required fields (name, base repository path) SHALL show inline validation feedback next to the field itself once the user has edited that field or tried to save, and SHALL NOT show it on a form the user hasn't touched.

#### Scenario: Successful project creation
- **WHEN** the user fills in name, base repository path, and worktrees root, and saves
- **THEN** the app saves a new `Project` via `ProjectRepository` and shows the worktree view for it

#### Scenario: Required field missing
- **WHEN** the user tries to save without a name or without a base repository path
- **THEN** the app shows the validation error next to each missing field, moves focus to the first one, and does not save the project

#### Scenario: Required field shows inline feedback
- **WHEN** the user empties the "Name" or "Base repository path" field after editing it
- **THEN** the app marks that field with an error state and a supporting message explaining it is required

#### Scenario: Untouched new form
- **WHEN** the add-project form opens and the user hasn't edited any field or tried to save
- **THEN** no field is shown in an error state

### Requirement: Confirm before discarding unsaved changes
The system SHALL ask for confirmation before discarding unsaved edits whenever the user leaves the project settings while any field differs from its initial value: with "Discard", by selecting another entry in the sidebar, through a shortcut or menu entry that changes the view, or by closing the window.

#### Scenario: Cancel with unsaved changes
- **WHEN** the user has changed at least one field and activates "Discard"
- **THEN** the app shows a confirmation dialog before discarding the changes and returning to the previous view

#### Scenario: Cancel without changes
- **WHEN** the user leaves the settings without having changed any field
- **THEN** the app shows the chosen view immediately without showing a confirmation dialog

#### Scenario: Leave through the sidebar
- **WHEN** the user has changed a field and selects another project in the sidebar
- **THEN** the app asks for confirmation, shows the other project only if the user confirms, and keeps the edits otherwise

#### Scenario: Close the window
- **WHEN** the user has changed a field and closes the main window
- **THEN** the app asks for confirmation before quitting

## ADDED Requirements

### Requirement: Settings organized in sections
The project settings SHALL be organized in sections: General (name, base repository, worktrees folder, main branch), Worktree files (the repository's provisioning and the local files), Agent, and, when the repository declares them, Android emulator and iOS simulator. On wide windows a section index SHALL let the user jump to each section.

#### Scenario: Jump to a section
- **WHEN** the user activates "Agent" in the section index
- **THEN** the view scrolls to the Agent section

#### Scenario: Repository without providers
- **WHEN** the repository declares neither the emulator nor the iOS simulator provider
- **THEN** neither section nor its index entry is shown

### Requirement: Save bar while there are unsaved changes
While any field differs from its initial value, the project settings SHALL show a save bar at the bottom of the view with "Discard" and "Save", and Cmd/Ctrl+S SHALL save. Without changes, the save bar SHALL NOT be shown, except while adding a project, where "Add project" and "Cancel" are always shown.

#### Scenario: Edit a field
- **WHEN** the user changes the agent command of an existing project
- **THEN** the save bar appears, and pressing Cmd/Ctrl+S saves the project and hides it

#### Scenario: Revert the edit
- **WHEN** the user changes a field and then restores its original value
- **THEN** the save bar disappears

### Requirement: Add a project from its repository folder
Adding a project SHALL start by choosing the repository folder. Once chosen, the form SHALL prefill the name with the folder's name, suggest a worktrees folder named `<folder>-worktrees` next to the repository, and show the main branch detected in the repository as the main branch field's placeholder. A folder that isn't a Git repository SHALL be rejected with a message, and the user SHALL be able to choose another.

#### Scenario: Choose a repository
- **WHEN** the user adds a project and chooses `~/projects/main-repo`, whose default branch is `main`
- **THEN** the form shows the name `main-repo`, the worktrees folder `~/projects/main-repo-worktrees` and `main` as the detected main branch

#### Scenario: Not a repository
- **WHEN** the user chooses a folder that isn't a Git repository
- **THEN** the app says the folder isn't a Git repository and asks for another, without opening the rest of the form

### Requirement: Drop a repository folder to add it
Dropping a folder on the main window SHALL start adding a project from that folder, as if it had been chosen in "Add project". Dropping anything else SHALL be ignored.

#### Scenario: Drop a repository
- **WHEN** the user drops `~/projects/api` on the main window
- **THEN** the add-project form opens prefilled from `~/projects/api`

#### Scenario: Drop a file
- **WHEN** the user drops a file that isn't a folder
- **THEN** nothing happens
