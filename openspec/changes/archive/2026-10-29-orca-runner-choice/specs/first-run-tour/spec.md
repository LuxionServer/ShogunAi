## MODIFIED Requirements

### Requirement: Open the tour's agent
The last step of the dialog SHALL offer the ways to open the sample's main worktree that are available on the machine (the runner in use, Herdr or Orca, the terminal, Claude Desktop, copying the launch command), and SHALL open it with the chosen one and the tour project's default agent, then close and show the tour project's view.

#### Scenario: Open in the terminal
- **WHEN** the user chooses "Open in terminal" at the last step
- **THEN** a terminal opens in the sample's main worktree running the tour's agent, and the app shows the tour project's view

#### Scenario: Open in Orca
- **WHEN** Orca is the runner in use and the user chooses "Open in Orca" at the last step
- **THEN** the sample's main worktree opens in Orca running the tour's agent, and the app shows the tour project's view

#### Scenario: Folder trust
- **WHEN** the last step or the banner offers the ways to open the agent
- **THEN** it explains that the agent first asks whether the user trusts the sample's folder and that accepting it starts the tour, and the app doesn't change the agent's own trust settings

#### Scenario: Launch fails
- **WHEN** opening the agent fails
- **THEN** the tour project's view shows the launcher's error as it does for any worktree, and the tour banner still offers the ways to open the agent
