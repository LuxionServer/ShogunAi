# Spec Delta

## ADDED Requirements

### Requirement: The app never forgets a multirepo on its own
Loading or reloading the known multirepos SHALL NOT change `~/.shogunai/multirepos.json`: an entry, its approval and its local agent SHALL stay until the user removes it.

#### Scenario: Volume unmounted when the app opens
- **WHEN** `~/.shogunai/multirepos.json` lists `/Volumes/External/main-multirepo`, approved and with a local agent, and the app opens while `/Volumes/External` isn't mounted
- **THEN** `~/.shogunai/multirepos.json` is unchanged

#### Scenario: Volume mounted again
- **WHEN** the volume is mounted again and the app reloads
- **THEN** the sidebar shows the multirepo with its projects as before, approved and with its local agent

### Requirement: Unavailable multirepos in the sidebar
The sidebar SHALL list each known multirepo whose declaration can't be read, named after its folder, marked as unavailable with the reason (the folder can't be reached, the folder has no declaration, or the declaration is invalid) and with no projects nested under it.

#### Scenario: Folder unreachable
- **WHEN** the known multirepo `/Volumes/External/main-multirepo` can't be reached
- **THEN** the sidebar shows `main-multirepo` marked as unavailable, its tooltip says its folder can't be reached, and the projects of its repositories are listed at the top level

#### Scenario: Declaration deleted
- **WHEN** the known multirepo's folder exists but has no `.shogunai-multirepo.json`
- **THEN** the sidebar shows it as unavailable because the folder has no declaration

#### Scenario: Declaration invalid
- **WHEN** the known multirepo's declaration isn't valid JSON
- **THEN** the sidebar shows it as unavailable with the problem found in the declaration

### Requirement: Remove an unavailable multirepo
Selecting an unavailable multirepo SHALL show its folder, why it's unavailable and the actions "Try again", which reloads the known multirepos, and "Remove", which after a confirmation forgets the entry with its approval and local agent and touches no file in its folder.

#### Scenario: Remove after confirming
- **WHEN** the user selects the unavailable `main-multirepo`, clicks "Remove" and confirms
- **THEN** `~/.shogunai/multirepos.json` no longer lists it nor keeps its approval or local agent, it leaves the sidebar and the app shows the active worktrees

#### Scenario: Removal cancelled
- **WHEN** the user clicks "Remove" and cancels the confirmation
- **THEN** nothing changes and the multirepo stays listed as unavailable

#### Scenario: Remove from the context menu
- **WHEN** the user opens the context menu of an unavailable multirepo in the sidebar
- **THEN** it offers "Remove", with the same confirmation, and the reordering actions

#### Scenario: Try again once reachable
- **WHEN** the volume is mounted again and the user clicks "Try again"
- **THEN** the multirepo shows its worktrees as any available multirepo

## REMOVED Requirements

### Requirement: The app forgets multirepos whose folder is gone
**Reason**: A folder that can't be reached isn't necessarily gone: an unmounted volume or a network drive that is down made the app forget the multirepo with its approval and local agent.
**Migration**: The multirepo stays in the sidebar as unavailable; remove it explicitly with "Remove".
