## MODIFIED Requirements

### Requirement: Section label and summary
An inspector section SHALL show its name as an uppercase section label declared as a heading for assistive technologies, and, when it has one, SHALL show a short summary at the end of the label's line only while the section is collapsed, since the expanded content already shows it.

#### Scenario: Heading semantics
- **WHEN** the worktree detail shows its Status section
- **THEN** its label has heading semantics, so a screen reader can jump to it

#### Scenario: Summary while collapsed
- **WHEN** the user collapses the Environment section of a worktree with 2 ports and 5 variables
- **THEN** the section's content is hidden and its label shows "2 ports · 5 vars" (or its translation)

#### Scenario: No summary while expanded
- **WHEN** the Environment section of a worktree with 2 ports and 5 variables is expanded
- **THEN** its label line shows no summary

### Requirement: Four states of a section
An inspector section SHALL show placeholder rows while its data hasn't been read yet, one muted sentence when it was read and has nothing to show, its content otherwise, and SHALL NOT be drawn when it doesn't apply to the worktree. A section MAY declare that it is drawn only when it has something that needs attention, and then SHALL NOT be drawn while loading or when it has nothing to show. Reloading SHALL keep the previous content.

#### Scenario: Not read yet
- **WHEN** a worktree is selected and its status hasn't been read yet
- **THEN** the Status section is drawn with placeholder rows instead of being hidden

#### Scenario: Not applicable
- **WHEN** a worktree's repository declares no emulator, simulator, Compose or headless app, holds no desktop turn and its size isn't known yet
- **THEN** the Resources section shows placeholder rows for the disk only, and nothing about devices, Compose or the app

#### Scenario: Empty
- **WHEN** a worktree has no agent running
- **THEN** the Agent section says in one line that no agent is running

#### Scenario: Drawn only when it needs attention
- **WHEN** a linked worktree's provisioned files all match
- **THEN** no Provisioning section is drawn

### Requirement: Fixed order of the inspector
The worktree detail SHALL show, in this order: the header (branch, main chip, origin when it isn't ShogunAi, and path), the actions, the worktree's notices, then the sections Status, Agent, Environment, Resources and Provisioning. Resources SHALL group the devices, Compose, the headless app, the desktop turn and the disk. New information SHALL go into an existing section; a new section SHALL need at least three properties of its own.

#### Scenario: Order of sections
- **WHEN** a linked worktree with an agent, ports, a running emulator and stale provisioned files is selected
- **THEN** the section labels read Status, Agent, Environment, Resources and Provisioning from top to bottom
