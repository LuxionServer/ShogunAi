## MODIFIED Requirements

### Requirement: Open the multirepo agent from the app
The multirepo view SHALL offer "Open multirepo agent in Herdr", which opens the multirepo agent as `shogun open --herdr` does in the multirepo folder, and SHALL tell apart, in English and Spanish, that the agent was opened, started in its already open workspace, or focused.

#### Scenario: Open the multirepo agent
- **WHEN** the user activates it in `product`'s view
- **THEN** the multirepo agent's Herdr workspace opens in the multirepo folder, or its live agent is focused if open

#### Scenario: Open workspace without an agent
- **WHEN** the multirepo's Herdr workspace is open but has no live agent
- **THEN** the agent is started in a new pane of it and the notice says the agent was started, not that it was focused

#### Scenario: Agent doesn't start
- **WHEN** Herdr doesn't detect the multirepo agent in time
- **THEN** the view shows the error naming the pane and the command
