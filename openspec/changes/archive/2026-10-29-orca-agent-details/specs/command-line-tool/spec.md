## ADDED Requirements

### Requirement: Report Orca's agent details in status
`shogun status --json` SHALL add to each agent `activity`: `null` when the agent carries no details, otherwise an object with `waitingOn` (`null` or an object with `tool` and `input`, `input` being `null` when missing), `prompt` and `lastMessage` (each a string or `null`).

#### Scenario: Agent waiting on a tool
- **WHEN** Orca reports Claude as waiting on `Bash` with input `curl -s https://example.com -o page.html` and prompt `Download the page`
- **THEN** the JSON agent has `activity` with `waitingOn` `{"tool": "Bash", "input": "curl -s https://example.com -o page.html"}`, `prompt` `Download the page` and `lastMessage` `null`

#### Scenario: Agent without details
- **WHEN** Claude runs in a worktree that Orca doesn't report
- **THEN** the JSON agent has `activity` `null`

### Requirement: Report the ports Orca sees in status
`shogun status --json` SHALL add to each worktree `listeningPorts`: `null` when Orca couldn't be asked, otherwise an array, sorted by port, of objects with `port`, `process` and `url` (`url` being `null` when missing). `shogun status` SHALL add `listening=<ports>` to the line of a worktree Orca sees listening, with the port numbers sorted and separated by commas.

#### Scenario: Observed ports
- **WHEN** Orca sees a worktree listen on 10011 by `java` and on 8765 by `Python` at `http://127.0.0.1:8765`
- **THEN** its JSON entry has `listeningPorts` `[{"port": 8765, "process": "Python", "url": "http://127.0.0.1:8765"}, {"port": 10011, "process": "java", "url": null}]` and its line shows `listening=8765,10011`

#### Scenario: Orca running, no ports
- **WHEN** Orca is running and sees no port for a worktree
- **THEN** its JSON entry has `listeningPorts` `[]` and its line has no `listening=`

#### Scenario: Orca unavailable
- **WHEN** Orca isn't installed or isn't running
- **THEN** every worktree's JSON entry has `listeningPorts` `null`, and no line has `listening=`
