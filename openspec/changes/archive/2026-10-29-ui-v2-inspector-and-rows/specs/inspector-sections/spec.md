# Spec Delta

## Purpose

Defines how a detail pane (the inspector) groups its information: sections with a label, a summary and four states, key/value properties that can be selected and copied, and a fixed section order so new information has a known place.

## ADDED Requirements

### Requirement: Section label and summary
An inspector section SHALL show its name as an uppercase section label declared as a heading for assistive technologies, and, when it has one, SHALL show a short summary at the end of the label's line that stays visible while the section is collapsed.

#### Scenario: Heading semantics
- **WHEN** the worktree detail shows its Status section
- **THEN** its label has heading semantics, so a screen reader can jump to it

#### Scenario: Summary while collapsed
- **WHEN** the user collapses the Environment section of a worktree with 2 ports and 5 variables
- **THEN** the section's content is hidden and its label still shows "2 ports · 5 vars" (or its translation)

### Requirement: Collapsible sections
An inspector section SHALL be collapsible by clicking its label line or activating it with the keyboard, and SHALL announce whether it is expanded or collapsed. Sections SHALL start expanded.

#### Scenario: Collapse and expand
- **WHEN** the user activates an expanded section's label line twice
- **THEN** the content is hidden after the first activation and shown again after the second

### Requirement: Four states of a section
An inspector section SHALL show placeholder rows while its data hasn't been read yet, one muted sentence when it was read and has nothing to show, its content otherwise, and SHALL NOT be drawn when it doesn't apply to the worktree. Reloading SHALL keep the previous content.

#### Scenario: Not read yet
- **WHEN** a worktree is selected and its status hasn't been read yet
- **THEN** the Status section is drawn with placeholder rows instead of being hidden

#### Scenario: Not applicable
- **WHEN** a worktree's repository declares no emulator, simulator, Compose or headless app, holds no desktop turn and its size isn't known yet
- **THEN** the Resources section shows placeholder rows for the disk only, and nothing about devices, Compose or the app

#### Scenario: Empty
- **WHEN** a worktree has no agent running
- **THEN** the Agent section says no agent is running

### Requirement: Properties as a key and value grid
An inspector section's data SHALL be shown as a property list: one row per property with the key in the interface font and the value in the monospaced data font, keys aligned in one column. Values SHALL be selectable as text.

#### Scenario: Ports
- **WHEN** a worktree has `PORT` 4123 and `API_PORT` 4124
- **THEN** the Environment section shows two rows, `PORT` with `4123` and `API_PORT` with `4124`, values in the data font

### Requirement: Copy a property in place
A property marked as copyable SHALL offer a copy action on its row that puts the value on the clipboard and confirms in place, turning into a check mark and "Copied" for 1.5 seconds, without a global message.

#### Scenario: Copy a variable
- **WHEN** the user activates the copy action of `APP_SUFFIX` with value `-TASK-123`
- **THEN** `-TASK-123` is on the clipboard and the action shows "Copied" for 1.5 seconds

### Requirement: Fixed order of the inspector
The worktree detail SHALL show, in this order: the header (branch, origin and path), the actions, the worktree's notices, then the sections Status, Agent, Environment, Resources and Provisioning. Resources SHALL group the devices, Compose, the headless app, the desktop turn and the disk. New information SHALL go into an existing section; a new section SHALL need at least three properties of its own.

#### Scenario: Order of sections
- **WHEN** a linked worktree with an agent, ports, a running emulator and provisioned files is selected
- **THEN** the section labels read Status, Agent, Environment, Resources and Provisioning from top to bottom
