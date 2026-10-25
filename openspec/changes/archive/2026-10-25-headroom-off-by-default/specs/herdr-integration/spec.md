## MODIFIED Requirements

### Requirement: Open a worktree in Herdr from the desktop app
The worktree's detail and its row's context menu SHALL offer an "Open in Herdr" action that opens the worktree in Herdr as a worktree of its repository, grouped under the repository's space, focused, with a single pane in the worktree whose shell has the worktree's environment, and runs the project's agent launch command in that pane.

#### Scenario: Worktree with an environment
- **WHEN** the user opens in Herdr a worktree whose repository declares `env` or a provider that adds variables (e.g. the emulator provider)
- **THEN** a focused Herdr workspace opens in the worktree under the repository's space, its shell has every variable `shogun env` prints for that worktree (e.g. `PORT`, `ANDROID_SERIAL`), and the agent command runs in it with those variables

#### Scenario: Worktree without an environment
- **WHEN** the repository declares no environment
- **THEN** the workspace opens in the worktree under the repository's space and runs the agent command, with no extra variables

#### Scenario: Agent command
- **WHEN** the project's agent launch configuration resolves to `claude`, or to `headroom wrap claude` with Headroom turned on
- **THEN** that is the command run in the workspace, the same one the copied launch command uses
