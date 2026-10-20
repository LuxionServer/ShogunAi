## ADDED Requirements

### Requirement: Trim idle daemons to the pool limit
The desktop app SHALL check the Gradle daemon pool each time it refreshes the machine summaries and, when more Gradle daemons run than the limit, stop idle ones as the Gradle daemon pool defines. Each daemon stopped this way SHALL be reported in the Gradle daemons view with the reason. Without the desktop app running, `shogun pool trim` SHALL do the same on demand.

#### Scenario: App running
- **WHEN** the Gradle limit is 2, the app is running and an agent's build leaves a third Gradle daemon idle
- **THEN** within the next summary refresh the longest-idle daemon is stopped and the Gradle daemons view says it was stopped to stay within the limit

#### Scenario: One per worktree
- **WHEN** the profile is One per worktree
- **THEN** no daemon is stopped to honour a limit
