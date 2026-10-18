# Spec Delta

## ADDED Requirements

### Requirement: Close the Herdr workspace of a removed worktree
Every ShogunAi removal path (the desktop app's remove action and "Clean up", `shogun clean --yes` and `shogun hook worktree-remove`) SHALL close the Herdr workspace of each worktree it actually removes, looking the workspace up before removing and closing it only after the removal succeeds.

#### Scenario: Worktree open in Herdr
- **WHEN** the user removes a worktree that Herdr has open
- **THEN** the worktree is removed and its Herdr workspace is closed

#### Scenario: Cleanup with several worktrees open in Herdr
- **WHEN** `shogun clean --yes` or the desktop "Clean up" removes two worktrees open in Herdr and prunes a missing one that Herdr also has open
- **THEN** the three Herdr workspaces are closed

#### Scenario: Removal fails
- **WHEN** removing a worktree open in Herdr fails, e.g. because it has uncommitted changes and isn't forced
- **THEN** its Herdr workspace stays open

#### Scenario: Claude Code removes the worktree
- **WHEN** `shogun hook worktree-remove` removes a worktree that Herdr has open
- **THEN** its Herdr workspace is closed

### Requirement: Herdr never blocks a removal
A removal SHALL behave as before when Herdr isn't installed, isn't running or doesn't have the worktree open, with no Herdr message; when closing the workspace fails, the removal SHALL still succeed and a warning SHALL name the worktree and Herdr's error.

#### Scenario: Herdr not available
- **WHEN** a worktree is removed while Herdr isn't installed, its server isn't running or it doesn't have the worktree open
- **THEN** the removal behaves as before and no Herdr message is shown

#### Scenario: Closing fails
- **WHEN** the worktree is removed but `herdr workspace close` fails
- **THEN** the removal is reported as done, the desktop app shows a warning with Herdr's message, and the command line prints it on standard error without changing the exit code

### Requirement: Warn before closing Herdr workspaces
Before the user confirms a removal, ShogunAi SHALL say which of the worktrees to remove are open in Herdr and that their workspace, and the agent running there, will be closed. The lookup SHALL NOT delay or block the confirmation.

#### Scenario: Remove dialog
- **WHEN** the user opens the remove dialog of a worktree that Herdr has open
- **THEN** the dialog says that its Herdr workspace and the agent in it will be closed

#### Scenario: Cleanup dialog
- **WHEN** the user opens the "Clean up" dialog and one of the listed worktrees is open in Herdr
- **THEN** that worktree is marked as open in Herdr, with the same notice

#### Scenario: Worktree not open in Herdr
- **WHEN** the worktree isn't open in Herdr, or Herdr can't be reached
- **THEN** the dialog shows no Herdr notice and works as before

#### Scenario: Cleanup dry run
- **WHEN** the user runs `shogun clean` without `--yes` and a candidate is open in Herdr
- **THEN** the output includes `would close Herdr workspace of <path>` for it

### Requirement: Delete the branch of worktrees removed from Herdr
For Herdr's `worktree.removed` event, `shogun hook herdr` SHALL read the removed worktree's `data.worktree.path` and `data.worktree.branch`, find its repository from `data.workspace.worktree.repo_root` or else from the ShogunAi project whose worktrees folder contains the path, and, when that repository has a ShogunAi project, delete the branch only if Git considers it merged, keeping it otherwise.

#### Scenario: Merged branch
- **WHEN** Herdr removes a worktree of a repository with a ShogunAi project and its branch is merged
- **THEN** the branch is deleted and the command exits with code 0

#### Scenario: Unmerged branch
- **WHEN** the removed worktree's branch has commits that aren't merged
- **THEN** the branch is kept, a note saying so goes to standard error, and the command exits with code 0

#### Scenario: Repository without a ShogunAi project
- **WHEN** the removed worktree's repository has no ShogunAi project
- **THEN** no branch is deleted and the command exits with code 0

#### Scenario: Detached worktree
- **WHEN** the event has no `data.worktree.branch`
- **THEN** nothing is deleted and the command exits with code 0

#### Scenario: Repository can't be found
- **WHEN** the event has no `data.workspace.worktree.repo_root` and no ShogunAi project's worktrees folder contains the path
- **THEN** a note goes to standard error and the command exits with code 0

#### Scenario: Missing path
- **WHEN** the `worktree.removed` event has no `data.worktree.path`
- **THEN** the command prints an error on standard error and exits with code 2

#### Scenario: Removal started from ShogunAi
- **WHEN** ShogunAi removes a worktree and closes its Herdr workspace
- **THEN** Herdr emits no `worktree.removed` event and the hook doesn't run

## MODIFIED Requirements

### Requirement: Ship a Herdr plugin for worktrees created from Herdr
The repository SHALL ship a Herdr plugin manifest in `integrations/herdr/`, installable with `herdr plugin install LuxionServer/ShogunAi/integrations/herdr`, that declares support for macOS, Linux and Windows and runs `shogun hook herdr` on Herdr's `worktree.created` and `worktree.removed` events, passing it as an argument list without a shell.

#### Scenario: Plugin installed
- **WHEN** the user installs the plugin and Herdr creates a worktree (from its UI, `herdr worktree create` or its socket API)
- **THEN** Herdr runs `shogun hook herdr` with the event in its environment

#### Scenario: Worktree removed from Herdr
- **WHEN** the plugin is installed and Herdr removes a worktree (from its UI, `herdr worktree remove` or its socket API)
- **THEN** Herdr runs `shogun hook herdr` with the `worktree.removed` event in its environment

#### Scenario: Manifest accepted by Herdr
- **WHEN** the manifest is linked with `herdr plugin link integrations/herdr`
- **THEN** Herdr links it without errors or warnings and lists its `worktree.created` and `worktree.removed` hooks

### Requirement: Provision worktrees for Herdr's worktree.created event
`shogun hook herdr` SHALL read the event name from `HERDR_PLUGIN_EVENT` and its JSON from `HERDR_PLUGIN_EVENT_JSON` and, for `worktree.created`, provision the worktree at the event's `data.worktree.path` like `shogun provision <path>`, printing each copied file's relative path.

#### Scenario: Worktree created by Herdr
- **WHEN** the hook runs with `HERDR_PLUGIN_EVENT=worktree.created` and an event whose `data.worktree.path` is a linked worktree of a repository
- **THEN** that worktree gets the files declared by `.shogunai.json`, `.worktreeinclude` and the repository's ShogunAi project, its ports are reserved, its `envFile` is written, and the command exits with code 0

#### Scenario: Repository without ShogunAi configuration
- **WHEN** the worktree's repository has no `.shogunai.json`, no `.worktreeinclude` and no ShogunAi project
- **THEN** nothing is copied and the command exits with code 0

#### Scenario: Other event
- **WHEN** the hook runs with an event other than `worktree.created` and `worktree.removed`
- **THEN** it does nothing and exits with code 0

#### Scenario: Missing or invalid event
- **WHEN** `HERDR_PLUGIN_EVENT` or `HERDR_PLUGIN_EVENT_JSON` is missing, the JSON is invalid or it has no `data.worktree.path`
- **THEN** the command prints an error on standard error and exits with code 2

#### Scenario: Provisioning fails
- **WHEN** provisioning fails, e.g. because `.shogunai.json` is unreadable or the path isn't a worktree of its repository
- **THEN** the error is printed on standard error, where Herdr's plugin log keeps it, and the command exits with code 1
