# herdr-integration Specification

## Purpose
Connects ShogunAi with Herdr, the terminal multiplexer for AI agents: ShogunAi worktrees open in Herdr grouped under their repository with their environment and agent, and worktrees created from Herdr by mistake are still provisioned and flagged.

## Requirements

### Requirement: Open a worktree in Herdr from the desktop app
The worktree row SHALL offer an "Open in Herdr" action that opens the worktree in Herdr as a worktree of its repository, grouped under the repository's space, focused, with a single pane in the worktree whose shell has the worktree's environment, and runs the project's agent launch command in that pane.

#### Scenario: Worktree with an environment
- **WHEN** the user opens in Herdr a worktree whose repository declares `env` or a provider that adds variables (e.g. the emulator provider)
- **THEN** a focused Herdr workspace opens in the worktree under the repository's space, its shell has every variable `shogun env` prints for that worktree (e.g. `PORT`, `ANDROID_SERIAL`), and the agent command runs in it with those variables

#### Scenario: Worktree without an environment
- **WHEN** the repository declares no environment
- **THEN** the workspace opens in the worktree under the repository's space and runs the agent command, with no extra variables

#### Scenario: Agent command
- **WHEN** the project's agent launch configuration resolves to `headroom wrap claude`
- **THEN** that is the command run in the workspace, the same one the copied launch command uses

### Requirement: Reuse the worktree's Herdr workspace
When Herdr already has the worktree open, the "Open in Herdr" action SHALL focus that workspace instead of opening another, and SHALL NOT type anything into its panes.

#### Scenario: Already open
- **WHEN** the user opens in Herdr a worktree Herdr already has open
- **THEN** its workspace is focused, no new workspace is created and nothing is typed into its panes

### Requirement: Report why a worktree can't be opened in Herdr
The "Open in Herdr" action SHALL show a message instead of failing silently when Herdr isn't installed or its command fails, and SHALL NOT change the worktree in either case.

#### Scenario: Herdr not installed
- **WHEN** the `herdr` command can't be found
- **THEN** the app shows that Herdr isn't installed, with a link to herdr.dev

#### Scenario: Herdr not running or failing
- **WHEN** the `herdr` command exits with an error, e.g. because no Herdr server is running
- **THEN** the app shows that the worktree couldn't be opened in Herdr, with Herdr's error message

### Requirement: Ship a Herdr plugin for worktrees created from Herdr
The repository SHALL ship a Herdr plugin manifest in `integrations/herdr/`, installable with `herdr plugin install LuxionServer/ShogunAi/integrations/herdr`, that declares support for macOS, Linux and Windows and runs `shogun hook herdr` on Herdr's `worktree.created` event, passing it as an argument list without a shell.

#### Scenario: Plugin installed
- **WHEN** the user installs the plugin and Herdr creates a worktree (from its UI, `herdr worktree create` or its socket API)
- **THEN** Herdr runs `shogun hook herdr` with the event in its environment

#### Scenario: Manifest accepted by Herdr
- **WHEN** the manifest is linked with `herdr plugin link integrations/herdr`
- **THEN** Herdr links it without errors or warnings and lists its `worktree.created` hook

### Requirement: Provision worktrees for Herdr's worktree.created event
`shogun hook herdr` SHALL read the event name from `HERDR_PLUGIN_EVENT` and its JSON from `HERDR_PLUGIN_EVENT_JSON` and, for `worktree.created`, provision the worktree at the event's `data.worktree.path` like `shogun provision <path>`, printing each copied file's relative path.

#### Scenario: Worktree created by Herdr
- **WHEN** the hook runs with `HERDR_PLUGIN_EVENT=worktree.created` and an event whose `data.worktree.path` is a linked worktree of a repository
- **THEN** that worktree gets the files declared by `.shogunai.json`, `.worktreeinclude` and the repository's ShogunAi project, its ports are reserved, its `envFile` is written, and the command exits with code 0

#### Scenario: Repository without ShogunAi configuration
- **WHEN** the worktree's repository has no `.shogunai.json`, no `.worktreeinclude` and no ShogunAi project
- **THEN** nothing is copied and the command exits with code 0

#### Scenario: Other event
- **WHEN** the hook runs with an event other than `worktree.created`
- **THEN** it does nothing and exits with code 0

#### Scenario: Missing or invalid event
- **WHEN** `HERDR_PLUGIN_EVENT` or `HERDR_PLUGIN_EVENT_JSON` is missing, the JSON is invalid or it has no `data.worktree.path`
- **THEN** the command prints an error on standard error and exits with code 2

#### Scenario: Provisioning fails
- **WHEN** provisioning fails, e.g. because `.shogunai.json` is unreadable or the path isn't a worktree of its repository
- **THEN** the error is printed on standard error, where Herdr's plugin log keeps it, and the command exits with code 1

### Requirement: Flag worktrees created from Herdr outside the worktrees folder
After provisioning, when the repository has a ShogunAi project and the worktree isn't inside the project's worktrees folder, `shogun hook herdr` SHALL show a Herdr notification naming the worktree and the project's worktrees folder and recommending creating worktrees from ShogunAi, and SHALL still exit with code 0.

#### Scenario: Created in Herdr's folder
- **WHEN** Herdr creates `~/.herdr/worktrees/main-repo/feature-task-7` for a repository whose ShogunAi project keeps worktrees in `~/projects`
- **THEN** the worktree is provisioned and Herdr shows a notification saying it's outside `~/projects` and that new worktrees should be created from ShogunAi

#### Scenario: Inside the worktrees folder or without a project
- **WHEN** the worktree is inside the project's worktrees folder, or the repository has no ShogunAi project
- **THEN** no notification is shown

#### Scenario: Notification fails
- **WHEN** Herdr can't show the notification
- **THEN** the failure is noted on standard error and the command still exits with code 0
