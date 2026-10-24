## ADDED Requirements

### Requirement: Leftover Docker resources in the Compose view
The Compose view of the Machine section SHALL list, after the projects, the volumes, networks and images left by the projects of removed worktrees of every saved project that declares the provider, each with its kind, name and project, and SHALL offer an action to remove them that keeps volumes, and a separate one that also removes volumes and asks for confirmation first. The outcome SHALL be shown with each failure and its reason.

#### Scenario: Leftovers listed
- **WHEN** a removed worktree of a saved project left a network and a volume
- **THEN** the Compose view lists both with their kind and project after the projects

#### Scenario: Remove keeping volumes
- **WHEN** the user activates the removal that keeps volumes
- **THEN** the network is removed, the volume is still listed, and the view says what was removed

#### Scenario: Remove volumes after confirming
- **WHEN** the user activates the removal of volumes and confirms
- **THEN** the volume is removed too

#### Scenario: Nothing left
- **WHEN** no removed worktree left any resource
- **THEN** the view shows no leftovers section and no removal action
