## MODIFIED Requirements

### Requirement: Start a worktree's emulator
Starting SHALL first acquire the worktree's emulator from the emulator pool when the worktree doesn't hold one, then launch the chosen AVD on the worktree's console port in read-only mode, so the same AVD can run once per worktree, without a window when the machine setting or the start asks for it, detached from the caller, and wait until the device reports it has booted or a timeout passes. Starting an emulator that is already running SHALL succeed without launching another.

#### Scenario: Start and wait
- **WHEN** the user starts the emulator of a worktree whose serial is `emulator-5556`
- **THEN** the AVD runs on port 5556 in read-only mode and the start returns once `emulator-5556` has booted

#### Scenario: First start acquires the port
- **WHEN** the user starts the emulator of a worktree that holds no console port and the pool has room
- **THEN** a console port is reserved, the emulator starts on it and the result names its serial

#### Scenario: Pool full
- **WHEN** the user starts the emulator of a worktree that holds no console port and no emulator in the full pool is releasable
- **THEN** nothing is reserved or launched and the start fails with the pool's error

#### Scenario: Already running
- **WHEN** `emulator-5556` is already online
- **THEN** starting reports it as running, launches nothing and renews the lease

#### Scenario: Without a window
- **WHEN** the project's machine setting asks for no window
- **THEN** the emulator is started with no window

#### Scenario: Boot timeout
- **WHEN** the device doesn't report a completed boot within the timeout
- **THEN** the start fails with an error, leaving the emulator running so it can finish booting

#### Scenario: Another worktree's emulator doesn't answer
- **WHEN** a worktree whose serial is `emulator-5560` starts its emulator while `emulator-5554`, held by another worktree, doesn't answer its boot property query
- **THEN** the start of `emulator-5560` succeeds once it has booted, and no error names `emulator-5554`

### Requirement: Stop and report a worktree's emulator
The system SHALL report a worktree's emulator as not acquired when the worktree holds no console port, and otherwise as stopped, booting, running or unresponsive from `adb`'s device list and boot property, unresponsive when it is listed but its boot property query gets no answer. It SHALL stop it by asking that emulator to exit through `adb -s <serial> emu kill`, keeping the worktree's reservation. Stopping an emulator that isn't running or isn't acquired SHALL succeed and change nothing.

#### Scenario: Status
- **WHEN** `adb` lists `emulator-5556` as a device whose boot has completed
- **THEN** the worktree's emulator is reported as running

#### Scenario: Status of an emulator that doesn't answer
- **WHEN** `adb` lists the worktree's `emulator-5556` as a device but its boot property query gets no answer
- **THEN** the status succeeds and reports `emulator-5556` as unresponsive, together with the advice to wait and check again, or stop it and start it again

#### Scenario: Not acquired
- **WHEN** a worktree never started its emulator
- **THEN** its emulator is reported as not acquired and no `adb` query is made for it

#### Scenario: Stop
- **WHEN** the user stops a running worktree emulator
- **THEN** that emulator exits, the others keep running, and the worktree keeps its serial

#### Scenario: Stop when stopped
- **WHEN** the worktree's emulator isn't running
- **THEN** stopping succeeds and nothing else happens

## ADDED Requirements

### Requirement: One emulator that doesn't answer doesn't hold up the others
Reading emulator states SHALL query the boot property of each emulator it needs concurrently, each within a 5-second limit, and SHALL report an emulator whose query doesn't answer in time as unresponsive instead of failing the read. A read about one worktree's emulator SHALL query only that emulator.

#### Scenario: One hung emulator among healthy ones
- **WHEN** the emulator states are read while `emulator-5554` doesn't answer its boot property query and `emulator-5556` has booted
- **THEN** the read succeeds within about 5 seconds, reporting `emulator-5554` as unresponsive and `emulator-5556` as running

#### Scenario: Several hung emulators
- **WHEN** three listed emulators don't answer their boot property query
- **THEN** the read takes about 5 seconds, not 5 seconds per emulator, and reports all three as unresponsive

#### Scenario: Status of one worktree
- **WHEN** the worktree whose serial is `emulator-5560` asks for its emulator's status while `emulator-5556` doesn't answer
- **THEN** only `emulator-5560`'s boot property is queried, and the status answers without waiting for `emulator-5556`

#### Scenario: Unresponsive emulator asked again
- **WHEN** an emulator reported as unresponsive answers its boot property query on a later read
- **THEN** it is reported as booting or running from that answer

### Requirement: Explain an emulator that doesn't answer after starting
When the worktree's own emulator is still not answering `adb` at the end of the start's wait, the start SHALL fail with an error that names the emulator, says it is running but not answering `adb`, and tells the agent it can wait and start again, or stop it and start it again, without showing the internal `adb` command.

#### Scenario: The worktree's own emulator doesn't answer
- **WHEN** the worktree's `emulator-5560` is listed by `adb` but its boot property query keeps getting no answer until the boot timeout
- **THEN** the start fails with an error saying `emulator-5560` is running but not answering `adb`, that the agent can wait and run the start again, or stop it and start it again, and the error doesn't contain the `adb` command line

### Requirement: Explain a stop that gets no answer
When the emulator doesn't answer the request to exit, the stop SHALL fail with an error that names the emulator and its console port and tells the agent to end that emulator's process and start it again, without showing the internal `adb` command.

#### Scenario: Stop gets no answer
- **WHEN** the user stops the worktree's `emulator-5556` and `adb -s emulator-5556 emu kill` doesn't finish within its time limit
- **THEN** the stop fails with an error saying `emulator-5556` didn't answer the request to exit and that its emulator process, the one started on port 5556, has to be ended before starting it again
