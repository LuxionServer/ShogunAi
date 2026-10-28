## ADDED Requirements

### Requirement: Maven daemons across projects
The Maven daemons view SHALL list every Maven daemon on the machine with its process id, memory, state and owner, the total memory, and actions to stop one daemon or every idle one, following the Maven provider's stop rules. The result of a stop SHALL stay visible until dismissed.

#### Scenario: State shown
- **WHEN** one Maven daemon is idle since 12:57 and another is busy
- **THEN** the first row says it's idle with how long, and the second says it's busy

#### Scenario: Stop idle daemons
- **WHEN** the user activates "Stop idle daemons" with two idle Maven daemons and one busy
- **THEN** the two idle ones are stopped, the busy one is kept, the view says so and the list refreshes

#### Scenario: Stop one daemon
- **WHEN** the user stops a Maven daemon whose state is unknown
- **THEN** it isn't stopped and the view says its state is unknown
