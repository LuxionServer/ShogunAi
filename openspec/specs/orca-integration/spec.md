# orca-integration Specification

## Purpose
Connects ShogunAi with Orca, the desktop app for running coding agents in worktrees. ShogunAi worktrees open in Orca under their repository, with their environment and the project's agent. Worktrees Orca creates are provisioned and released through its repository hooks.

## Requirements

### Requirement: Open a worktree in Orca from the desktop app
When Orca is installed, the worktree's detail, its row's context menu, the command palette, the tray and the menu bar SHALL offer an "Open in Orca" action, as the primary action when Orca is the default runner and in "Open with…" otherwise, that opens the worktree in Orca under its repository. The action SHALL open a focused terminal in the worktree whose shell has the worktree's environment, and SHALL run the project's agent launch command in it.

#### Scenario: Worktree with an environment
- **WHEN** the user opens in Orca a worktree whose repository declares `env` or a provider that adds variables
- **THEN** Orca shows the worktree under its repository with a focused terminal in it, the terminal's shell has every variable `shogun env` prints for that worktree, and the agent command runs with them

#### Scenario: Worktree without an environment
- **WHEN** the repository declares no environment
- **THEN** the terminal opens in the worktree and runs the agent command, with no extra variables

#### Scenario: Agent command and permissions
- **WHEN** the project's agent launch configuration resolves to `claude`
- **THEN** that command runs, the same one the copied launch command uses, with the session name ShogunAi gives Claude sessions, and Orca's own agent launch settings, such as its full-access default, aren't applied

### Requirement: Make the worktree visible in Orca
Before opening a worktree, ShogunAi SHALL register its repository with Orca when Orca doesn't know it. When Orca would hide worktrees it didn't create for that repository, ShogunAi SHALL set the repository to show them. It SHALL NOT change any other repository's setting.

#### Scenario: Repository unknown to Orca
- **WHEN** the user opens in Orca a worktree of a repository Orca has never registered
- **THEN** Orca registers the repository and lists the worktree under it

#### Scenario: External worktrees hidden
- **WHEN** Orca knows the repository but hides worktrees it didn't create
- **THEN** that repository is set to show them, and the worktree appears in Orca's sidebar

#### Scenario: Already visible
- **WHEN** Orca already shows the repository's external worktrees
- **THEN** no repository setting changes

### Requirement: Reuse the worktree's live agent in Orca
When Orca already has a terminal with a live agent in the worktree, opening it in Orca SHALL focus that terminal, SHALL NOT type anything into any terminal and SHALL NOT open another one. When none of the worktree's terminals has a live agent, it SHALL open a new terminal with the same environment and agent command as a fresh open, and leave the other terminals as they are.

#### Scenario: Agent running
- **WHEN** the user opens in Orca a worktree where Orca reports a `claude` agent in one of its terminals
- **THEN** that terminal is focused and nothing is created or typed

#### Scenario: Agent finished its turn
- **WHEN** the worktree's only agent reports `done`
- **THEN** it counts as live and its terminal is focused

#### Scenario: Terminals without an agent
- **WHEN** the worktree has Orca terminals but none runs an agent
- **THEN** a new terminal runs the agent command with the worktree's environment, and the existing terminals stay open

### Requirement: Start Orca when it isn't running
When Orca is installed but its desktop app isn't running, opening a worktree in Orca SHALL start Orca and wait up to 30 seconds for it to accept commands before opening the worktree.

#### Scenario: Orca closed
- **WHEN** the user opens a worktree in Orca while Orca is closed
- **THEN** Orca starts, and the worktree opens in it with its agent

#### Scenario: Orca doesn't come up
- **WHEN** Orca still doesn't accept commands 30 seconds after being started
- **THEN** the open fails with an error saying Orca didn't start

### Requirement: Report why a worktree can't be opened in Orca
Opening in Orca SHALL fail with a message, and SHALL NOT change the worktree, when Orca isn't installed or its command fails.

#### Scenario: Orca not installed
- **WHEN** neither the `orca` command nor, on Linux, `orca-ide` can be found
- **THEN** the app shows that Orca isn't installed, with a link to onorca.dev

#### Scenario: Orca command fails
- **WHEN** an `orca` command returns an error
- **THEN** the app shows that the worktree couldn't be opened in Orca, with Orca's error message

### Requirement: Verify the agent started in Orca
After starting the agent command in a new Orca terminal, ShogunAi SHALL wait up to 10 seconds for Orca to report an agent in that terminal, or for Claude Code's session registry to show a live session in the workspace's folder that runs in that terminal or has the name given to the new session. When the time runs out, the open SHALL fail with an error naming the terminal and the command, and the terminal SHALL stay open.

#### Scenario: Agent detected
- **WHEN** a new terminal runs `claude` and Orca reports an agent in it two seconds later
- **THEN** the open succeeds

#### Scenario: Agent only in the registry
- **WHEN** a new terminal runs `claude -n main-repo-task-123`, Orca reports no agent in it, and a live session named `main-repo-task-123` appears in the worktree's folder
- **THEN** the open succeeds

#### Scenario: Agent never detected
- **WHEN** neither Orca nor Claude Code's session registry shows the agent 10 seconds after the new terminal started
- **THEN** the open fails with an error naming the terminal and the command, and the terminal stays open

#### Scenario: Agent only focused
- **WHEN** the open only focuses a live agent
- **THEN** nothing is started and nothing is waited for

### Requirement: Open a worktree in Orca from the command line
`shogun open <target> --orca`, or `shogun open <target>` without a runner flag when Orca is the runner in use, SHALL open the worktree in Orca exactly as the desktop app's "Open in Orca" does. It SHALL report on standard error, with exit code 1, that Orca isn't installed, that it didn't start, that its command failed, or that the agent didn't start. `--json` SHALL report whether the agent was started in a new terminal or a live one was focused.

#### Scenario: Worktree with an environment
- **WHEN** the user runs `shogun open TASK-123 --orca` and the repository declares `env`
- **THEN** the terminal's shell has every variable `shogun env` prints for that worktree, and the agent command runs with them

#### Scenario: Orca in use without a flag
- **WHEN** Orca is the runner in use and the user runs `shogun open TASK-123`
- **THEN** the worktree opens in Orca as with `shogun open TASK-123 --orca`

#### Scenario: Orca not installed
- **WHEN** the `orca` command can't be found
- **THEN** the error says Orca isn't installed, names onorca.dev, and the command exits with code 1

#### Scenario: Both targets given
- **WHEN** the user passes both `--herdr` and `--orca`
- **THEN** the command fails with a usage error and opens nothing

### Requirement: Report the Orca outcome in the desktop app
After "Open in Orca", the desktop app SHALL say, in English and Spanish, whether the agent was started in a new terminal or a live agent was focused. It SHALL show an agent that didn't start as an error with the terminal and the command as technical details.

#### Scenario: Live agent focused
- **WHEN** "Open in Orca" focuses the worktree's live agent
- **THEN** the confirmation says the running agent was focused

#### Scenario: Agent didn't start
- **WHEN** Orca doesn't report the agent in time
- **THEN** the worktree shows the error with the terminal and the command as technical details

### Requirement: Provision worktrees from Orca's setup script
`shogun hook orca setup` SHALL provision the worktree at `ORCA_WORKTREE_PATH` like `shogun provision <path>`, printing each copied file's relative path, so that a repository's `orca.yaml` can run it as its `scripts.setup`.

#### Scenario: Worktree created by Orca
- **WHEN** Orca creates a worktree of a repository whose `orca.yaml` runs `shogun hook orca setup`
- **THEN** that worktree gets the files declared by `.shogunai.json`, `.worktreeinclude` and the repository's ShogunAi project, its ports are reserved, its `envFile` is written, and the command exits with code 0

#### Scenario: Repository without ShogunAi configuration
- **WHEN** the repository has no `.shogunai.json`, no `.worktreeinclude` and no ShogunAi project
- **THEN** nothing is copied and the command exits with code 0

#### Scenario: Missing variable
- **WHEN** `ORCA_WORKTREE_PATH` isn't set
- **THEN** the command prints an error on standard error and exits with code 2

#### Scenario: Provisioning fails
- **WHEN** provisioning fails, e.g. because `.shogunai.json` is unreadable
- **THEN** the error is printed on standard error, where Orca's setup terminal shows it, and the command exits with code 1

### Requirement: Flag worktrees created from Orca outside the worktrees folder
After provisioning, when the repository has a ShogunAi project and the worktree isn't inside the project's worktrees folder, `shogun hook orca setup` SHALL print a note naming the project's worktrees folder and recommending creating worktrees from ShogunAi, and SHALL still exit with code 0.

#### Scenario: Created in Orca's folder
- **WHEN** Orca creates `~/orca/workspaces/main-repo/TASK-7` for a repository whose ShogunAi project keeps worktrees in `~/projects`
- **THEN** the worktree is provisioned and the setup terminal shows that it's outside `~/projects` and that new worktrees should be created from ShogunAi

#### Scenario: Inside the worktrees folder
- **WHEN** the worktree is inside the project's worktrees folder, or the repository has no ShogunAi project
- **THEN** no note is printed

### Requirement: Release a worktree from Orca's archive script
`shogun hook orca archive` SHALL free what ShogunAi holds for the worktree at `ORCA_WORKTREE_PATH` before Orca removes it, the same as `shogun remove` does: its emulator, its simulator and its idle Gradle daemons. It SHALL NOT remove the worktree or its branch, and it SHALL exit with code 0 even when freeing something fails, so that Orca's removal goes on.

#### Scenario: Worktree with an emulator
- **WHEN** Orca archives a worktree whose emulator ShogunAi started
- **THEN** the emulator is stopped, the worktree and its branch are left to Orca, and the command exits with code 0

#### Scenario: Freeing fails
- **WHEN** stopping the worktree's simulator fails
- **THEN** the failure is printed on standard error and the command exits with code 0

#### Scenario: Unknown action
- **WHEN** `shogun hook orca` runs with an action other than `setup` or `archive`
- **THEN** it prints a usage error and exits with code 2

### Requirement: Close the Orca terminals of a removed worktree
Every ShogunAi removal path SHALL close the Orca terminals of each worktree it actually removes, after the removal succeeds: the desktop app's remove action and "Clean up", `shogun remove`, `shogun clean --yes` and `shogun hook worktree-remove`. When Git can't remove the worktree because its folder is locked by a process, ShogunAi SHALL close the worktree's Orca terminals and retry the removal once.

#### Scenario: Worktree open in Orca
- **WHEN** the user removes a worktree with Orca terminals
- **THEN** the worktree is removed and its Orca terminals are closed

#### Scenario: Removal fails
- **WHEN** removing a worktree with Orca terminals fails for a reason other than a locked folder
- **THEN** its Orca terminals stay open

#### Scenario: Folder locked by Orca's shells
- **WHEN** the removal fails because the folder is in use and the worktree has Orca terminals
- **THEN** its Orca terminals are closed, the removal is retried once and succeeds

### Requirement: Orca never blocks a removal
A removal SHALL behave as before, with no Orca message, when Orca isn't installed, isn't running or has no terminals in the worktree. ShogunAi SHALL NOT start Orca to remove a worktree. When closing the terminals fails, the removal SHALL still succeed and a warning SHALL name the worktree and Orca's error.

#### Scenario: Orca not running
- **WHEN** a worktree is removed while Orca is installed but closed
- **THEN** the removal behaves as before, Orca isn't started and no Orca message is shown

#### Scenario: Closing fails
- **WHEN** the worktree is removed but closing its Orca terminals fails
- **THEN** the removal is reported as done, the desktop app shows a warning with Orca's message, and the command line prints it on standard error without changing the exit code

### Requirement: Warn before closing Orca terminals
Before the user confirms a removal, ShogunAi SHALL say which of the worktrees to remove have Orca terminals and that those terminals, and the agents in them, will be closed. Looking this up SHALL NOT delay or block the confirmation.

#### Scenario: Remove dialog
- **WHEN** the user opens the remove dialog of a worktree with Orca terminals
- **THEN** the dialog says its Orca terminals and the agent in them will be closed

#### Scenario: Cleanup dry run
- **WHEN** the user runs `shogun clean` without `--yes` and a candidate has Orca terminals
- **THEN** the output includes `would close Orca terminals of <path>` for it

#### Scenario: Orca not reachable
- **WHEN** Orca isn't running or can't be reached
- **THEN** the dialog shows no Orca notice and works as before

### Requirement: Reading Orca's agents doesn't slow the list
Reading agents from Orca SHALL run at most once per check for all projects. It SHALL run nothing when Orca isn't installed and SHALL NOT start Orca. After a failed read it SHALL wait at least 30 seconds before trying again, and a failed or slow read SHALL NOT delay showing the rest of the worktrees' state.

#### Scenario: Orca closed
- **WHEN** Orca isn't running during several consecutive checks
- **THEN** ShogunAi asks Orca at most once every 30 seconds, never starts it, and shows agents without Orca states meanwhile

### Requirement: Find a live Claude that Orca doesn't report
When none of a workspace's Orca terminals reports an agent, opening it in Orca SHALL look in Claude Code's session registry for a live session in the workspace's folder whose process runs in one of those terminals. It SHALL focus that terminal and report `focused` with the session's name, and SHALL NOT start another agent. It SHALL prefer the session with the expected name, then the most recently started one.

#### Scenario: Orca lost track of the agent
- **WHEN** the user opens in Orca a worktree whose terminal runs a live Claude session named `main-repo-task-123`, but Orca reports no agent in any of its terminals
- **THEN** that terminal is focused, the result is `focused` with `session_name` `main-repo-task-123`, and no terminal is created

#### Scenario: Session outside Orca
- **WHEN** a live Claude session runs in the worktree's folder but not in any of the worktree's Orca terminals, for example in another terminal app
- **THEN** a new Orca terminal runs the agent command as in a fresh open

#### Scenario: Terminal unknown
- **WHEN** the terminal a live session runs in can't be determined, for example because the system doesn't expose another process's environment
- **THEN** a new Orca terminal runs the agent command as in a fresh open

#### Scenario: Multirepo agent
- **WHEN** the user opens the multirepo agent in Orca and its folder's terminal runs a live Claude session that Orca doesn't report
- **THEN** that terminal is focused and no other agent is started

### Requirement: Show a started emulator in Orca's emulator view
When ShogunAi starts a worktree's emulator, from the command line or the desktop app, and that worktree is open in a running Orca, ShogunAi SHALL show the emulator in the worktree's emulator view in Orca, with that emulator selected. It SHALL do so whether the start launched the emulator or found it already running.

#### Scenario: Worktree open in Orca
- **WHEN** the user runs `shogun emulator start` in a worktree that Orca lists, and its emulator `emulator-5556` boots
- **THEN** the worktree's emulator view in Orca shows `emulator-5556` with live video

#### Scenario: Already running
- **WHEN** the worktree's emulator is already running and the user starts it again
- **THEN** Orca's emulator view shows it again, reusing the view it already had, without a second one

#### Scenario: Started from the desktop app
- **WHEN** the user starts a worktree's emulator from its detail or from the machine view's Android emulators, and Orca lists that worktree
- **THEN** the emulator is shown in that worktree's emulator view in Orca

#### Scenario: Another worktree is active in Orca
- **WHEN** the emulator starts while Orca shows a different worktree
- **THEN** the emulator is shown in its own worktree's view and Orca keeps showing the worktree it was showing

### Requirement: Showing the emulator in Orca never gets in the way
Showing an emulator in Orca SHALL be best effort: ShogunAi SHALL NOT start Orca for it, SHALL NOT try when Orca isn't installed, isn't running or doesn't list the worktree, and a failure to show it SHALL NOT fail the emulator's start or the open, nor change the emulator.

#### Scenario: Orca closed
- **WHEN** the user starts a worktree's emulator while Orca is installed but not running
- **THEN** the emulator starts, Orca isn't started and no Orca command is run besides its status

#### Scenario: Worktree not in Orca
- **WHEN** Orca is running but doesn't list the worktree
- **THEN** the emulator starts and nothing is shown in Orca

#### Scenario: Orca rejects it
- **WHEN** Orca's command to show the emulator fails
- **THEN** the start still succeeds and the emulator keeps running

### Requirement: Report whether the emulator was shown in Orca
`shogun emulator start` SHALL say on standard error, after the start, when the emulator was shown in Orca and when showing it failed, with Orca's message, and say nothing about Orca otherwise. Its standard output SHALL stay unchanged. With `--json`, the start SHALL report `orcaView` as `shown`, `not-open` or `failed`.

#### Scenario: Shown
- **WHEN** the start shows `emulator-5556` in Orca
- **THEN** standard error says it's shown in Orca's emulator view, and standard output is the same as without Orca

#### Scenario: Failed
- **WHEN** Orca's command to show the emulator fails with a message
- **THEN** standard error says the emulator couldn't be shown in Orca, with that message, and the command exits with code 0

#### Scenario: JSON
- **WHEN** the user runs `shogun emulator start --json` and the worktree isn't open in Orca
- **THEN** the JSON has `"orcaView": "not-open"`

### Requirement: Show the running emulator when opening a worktree in Orca
Opening a worktree in Orca, from the command line, the desktop app or a group, SHALL show the worktree's emulator in its emulator view when the repository declares the emulator provider and the emulator is running, before the agent's terminal is started or focused, so the agent's terminal stays in front. It SHALL NOT start a stopped emulator. Failing to show it SHALL NOT fail the open.

#### Scenario: Emulator running
- **WHEN** the user opens in Orca a worktree whose emulator `emulator-5556` is running
- **THEN** Orca's emulator view of the worktree shows `emulator-5556`, and the agent's terminal is the one in front

#### Scenario: Emulator stopped
- **WHEN** the worktree's emulator isn't running or was never acquired
- **THEN** the worktree opens as before and no emulator is started or shown

#### Scenario: Reported by the command line
- **WHEN** `shogun open TASK-123 --orca --json` shows `emulator-5556`
- **THEN** the JSON has `"emulator": "emulator-5556"`, and it is `null` when no emulator was shown

### Requirement: Stopping the emulator needs nothing from Orca
Stopping or releasing a worktree's emulator SHALL NOT run any Orca command. Orca's stream of that emulator ends when the emulator exits, and starting the emulator again SHALL show it live in the same view.

#### Scenario: Stop while shown
- **WHEN** the user stops a worktree emulator that Orca shows
- **THEN** the emulator exits and no Orca command runs

#### Scenario: Start again
- **WHEN** the user starts that emulator again while the worktree is open in Orca
- **THEN** Orca's emulator view shows it live again
