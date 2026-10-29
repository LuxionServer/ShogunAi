## MODIFIED Requirements

### Requirement: Open the multirepo agent in Orca from the app
The multirepo view SHALL offer "Open multirepo agent in Orca" in its header's "More" (⋯) menu, after "Open multirepo agent in Herdr" and only when Orca is installed, which opens the multirepo agent as `shogun open --orca` does in the multirepo folder, and SHALL tell apart, in English and Spanish, that the agent was started or focused.

#### Scenario: Open the multirepo agent in Orca
- **WHEN** the user activates it in `product`'s view and Orca has a workspace in the multirepo folder without a live agent
- **THEN** the agent is started in a new terminal of that workspace and the notice says it was started

#### Scenario: Live agent
- **WHEN** that workspace already has a live agent
- **THEN** its terminal is focused and the notice says the running agent was focused

#### Scenario: No workspace for the folder
- **WHEN** the multirepo folder isn't a Git repository and Orca has no workspace for it
- **THEN** the view shows the error explaining how to add the folder in Orca's app, and nothing changes in Orca

#### Scenario: Agent doesn't start
- **WHEN** Orca doesn't report the multirepo agent in time
- **THEN** the view shows the error naming the terminal and the command

#### Scenario: Orca not installed
- **WHEN** Orca isn't installed
- **THEN** the view's ⋯ menu doesn't offer "Open multirepo agent in Orca"
