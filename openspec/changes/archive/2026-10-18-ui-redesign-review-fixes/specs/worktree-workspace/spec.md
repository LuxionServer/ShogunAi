# Spec Delta

## ADDED Requirements

### Requirement: Created worktree whose post-create command fails
When the worktree is created but its post-create command fails, the "New worktree" dialog SHALL NOT offer to create it again. It SHALL show the error with its technical details, state that the worktree was created, add it to the list and offer to close the dialog and to copy its launch command.

#### Scenario: Post-create command fails
- **WHEN** the worktree is created and its post-create command exits with an error
- **THEN** the dialog shows the error and its technical details, says the worktree exists, "Create worktree" is no longer available and the worktree is in the list

#### Scenario: Copy the launch command after the failure
- **WHEN** the user activates "Copy launch command" in that dialog
- **THEN** the created worktree's launch command is copied
