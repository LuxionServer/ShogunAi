## ADDED Requirements

### Requirement: Unavailable branches can't be selected
The system SHALL prevent selecting, in the "Existing branch" dropdown, a branch that is already checked out in the base repository or in another worktree, whatever the input method (mouse, keyboard or assistive technologies), and SHALL keep the "Create worktree" action disabled while no available branch is selected.

#### Scenario: Pressing a disabled branch through accessibility
- **WHEN** an assistive technology triggers the press action of a branch rendered disabled because it already has a worktree
- **THEN** the selection doesn't change and the dropdown stays open

#### Scenario: Create action with an unavailable branch
- **WHEN** the selected branch is not among the loaded local branches or is already checked out
- **THEN** the "Create worktree" action is disabled
