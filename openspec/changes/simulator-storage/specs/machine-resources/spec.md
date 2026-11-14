## ADDED Requirements

### Requirement: Simulator storage in the iOS simulators view
On macOS, the iOS simulators view SHALL show a storage section with the simulator runtimes (version, size, last use, users or the reason they can't be deleted), the unavailable devices with their count and size, and the dyld caches with their size and stale folders, following the `simulator-storage` rules. It SHALL be read in the background, like the other machine resources.

#### Scenario: Runtimes listed
- **WHEN** the user opens the iOS simulators view and iOS 26.5 and iOS 27.0 are installed
- **THEN** the storage section lists both with their size, last use and users, and the total

#### Scenario: Stale dyld cache
- **WHEN** the dyld caches have a folder of a previous macOS build
- **THEN** the section marks it as stale and offers to copy the command that removes it

### Requirement: Delete a runtime from the iOS simulators view
Each runtime row SHALL offer a Delete action, disabled with its reason when the runtime can't be deleted. Activating it SHALL ask for confirmation naming the runtime and its size, and after deleting SHALL report the space freed, offer to delete the unavailable devices, and refresh the view.

#### Scenario: Delete an unused runtime
- **WHEN** the user activates Delete on iOS 18.5, which has no users, and confirms
- **THEN** it is deleted, the view reports the space freed and offers to delete the unavailable devices

#### Scenario: Runtime in use
- **WHEN** a worktree's simulator runs on iOS 26.5
- **THEN** its Delete action is disabled and says which worktree uses it

### Requirement: Delete unavailable devices from the iOS simulators view
The storage section SHALL offer a "Delete unavailable devices" action, disabled when there are none, that asks for confirmation with their count and size and reports the space freed.

#### Scenario: Delete unavailable devices
- **WHEN** four devices are unavailable and the user confirms
- **THEN** they are deleted and the view reports the space freed

### Requirement: iOS simulators view for Xcode projects
On macOS, the Machine section SHALL offer the iOS simulators view when a saved project declares the iOS simulator provider or the Xcode provider. When only the Xcode provider is declared, the view SHALL show the storage section without the worktree simulators.

#### Scenario: Only Xcode declared
- **WHEN** the only saved project declares the Xcode provider and not the iOS simulator provider
- **THEN** Machine offers the iOS simulators view, which shows the simulator storage alone
