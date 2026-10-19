# worktree-detail Specification

## Purpose
Defines how the user selects a worktree in a list and sees everything about it, with its actions, in a detail pane, so list rows stay short and scannable.

## Requirements

### Requirement: Select a worktree
The user SHALL be able to select one worktree in the list by clicking its row or by moving with the up and down arrow keys while the list has focus. The selected row SHALL be visually marked.

#### Scenario: Click a row
- **WHEN** the user clicks a worktree's row
- **THEN** the row is marked as selected and the detail pane shows that worktree

#### Scenario: Move with the keyboard
- **WHEN** the list has focus with the second worktree selected and the user presses the down arrow
- **THEN** the third worktree becomes selected and its detail is shown

### Requirement: Keep the selection across reloads
The selection SHALL be kept by worktree path when the list reloads, including auto refresh. When the selected worktree is no longer listed, the selection SHALL be cleared.

#### Scenario: Auto refresh
- **WHEN** the list reloads because the repository changed and the selected worktree is still listed
- **THEN** it stays selected and its detail shows the refreshed data

#### Scenario: Selected worktree removed elsewhere
- **WHEN** the selected worktree is removed by another tool and the list reloads
- **THEN** nothing is selected and the detail pane shows the project summary

### Requirement: Detail of the selected worktree
The detail pane SHALL show the selected worktree's branch (or that it is detached), its full path as selectable text with a copy action, its origin, its full status, the agents running in it, its size and its reserved ports. Information that doesn't apply SHALL be omitted.

#### Scenario: Worktree with an agent and ports
- **WHEN** the selected worktree has Claude running and `PORT` reserved as 20001
- **THEN** the detail shows Claude as running and `PORT 20001`

#### Scenario: Copy the path
- **WHEN** the user activates the copy action next to the path
- **THEN** the worktree's full path is copied to the clipboard

### Requirement: Detail actions
The detail pane SHALL offer "Copy launch command" as its primary action, "Open in Herdr" as a secondary action that is disabled with a loading indicator while that worktree is being opened, and, for every worktree except the main one, a "Delete worktree" action styled as destructive.

#### Scenario: Main worktree
- **WHEN** the main worktree is selected
- **THEN** the detail offers "Copy launch command" and "Open in Herdr", and no delete action

#### Scenario: Opening in Herdr
- **WHEN** the user activates "Open in Herdr" in the detail and Herdr hasn't answered yet
- **THEN** the action shows a loading indicator and is disabled until the opening finishes

### Requirement: Project summary when nothing is selected
When no worktree is selected, the detail pane SHALL show the project's summary: the number of worktrees, their total size, and how many can be cleaned up, with the "Clean up" action when there are any.

#### Scenario: Nothing selected
- **WHEN** a project with five worktrees, two of them merged and clean, is shown with no selection
- **THEN** the detail pane shows five worktrees, their total size and two to clean up with a "Clean up" action

### Requirement: Row quick actions
Hovering a row SHALL reveal a copy-launch-command icon action. Each row SHALL have a context menu with "Copy launch command", "Open in Herdr", "Copy path" and, except for the main worktree, "Delete worktree".

#### Scenario: Copy from the row
- **WHEN** the user hovers a row and activates its copy icon
- **THEN** that worktree's launch command is copied without changing the selection

#### Scenario: Context menu
- **WHEN** the user opens the context menu of a non-main worktree's row
- **THEN** it offers copy launch command, open in Herdr, copy path and delete worktree

### Requirement: Keyboard shortcuts on the selected worktree
With a worktree selected, Cmd/Ctrl+Shift+C SHALL copy its launch command, and Cmd/Ctrl+Backspace SHALL open the delete confirmation for it unless it is the main worktree. Escape SHALL clear the selection.

#### Scenario: Copy with the keyboard
- **WHEN** a worktree is selected and the user presses Cmd/Ctrl+Shift+C
- **THEN** its launch command is copied to the clipboard

#### Scenario: Delete with the keyboard
- **WHEN** a non-main worktree is selected and the user presses Cmd/Ctrl+Backspace
- **THEN** the delete confirmation dialog opens for it

### Requirement: Detail over the list on narrow windows
When the content area is narrower than a threshold, the detail pane SHALL open over the list when a worktree is selected, and Escape or a close action SHALL hide it and clear the selection.

#### Scenario: Narrow window
- **WHEN** the content area is narrow and the user selects a worktree
- **THEN** the detail pane covers the list, and pressing Escape returns to the list

### Requirement: Stopping a device shows progress
While a worktree's emulator or simulator is stopping, its Stop action SHALL show that it is in progress and SHALL ignore further clicks until it ends.

#### Scenario: Double click on Stop
- **WHEN** the user clicks Stop twice on a running emulator
- **THEN** one stop is sent and the action shows progress until the emulator is stopped

### Requirement: Copying a path is confirmed
Copying a worktree's path SHALL show the same transient confirmation as copying the launch command.

#### Scenario: Copy path
- **WHEN** the user copies a worktree's path from its detail
- **THEN** a confirmation says the path was copied

### Requirement: Provisioning state in the detail
The detail of a linked worktree SHALL show whether its provisioned files match the base repository's, listing the missing, differing and not-linked files, and SHALL offer "Provision" when any file is missing or differs, or when the worktree was created by another tool. Provisioning from the detail SHALL behave like `shogun provision` for that worktree.

#### Scenario: Worktree created by another tool
- **WHEN** a worktree created with plain `git worktree add` is selected in a repository that declares copied files
- **THEN** the detail lists the missing files and offers "Provision", which copies them, reserves its ports and writes its environment file

#### Scenario: Stale copy
- **WHEN** `.env` changed in the base repository after it was copied
- **THEN** the detail lists `.env` as differing and offers "Provision"

#### Scenario: Up to date
- **WHEN** every provisioned file matches
- **THEN** the detail says the files are up to date and doesn't offer "Provision"

### Requirement: Environment variables in the detail
The detail SHALL show the worktree's resolved environment variables with their values, in the order `shogun env` prints them, and offer to copy them in the dotenv format.

#### Scenario: Variables declared
- **WHEN** a worktree's environment has `PORT=20001` and `APP_SUFFIX=-TASK-123`
- **THEN** the detail shows both, and copying them puts `PORT=20001` and `APP_SUFFIX=-TASK-123` on the clipboard
