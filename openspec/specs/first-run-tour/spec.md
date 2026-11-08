# first-run-tour Specification

## Purpose
Teaches a new user what ShogunAi does on their own machine: a throwaway sample repository with worktrees in every state, an agent that walks them through each feature, and the `shogun` command installed before they need it.

## Requirements

### Requirement: Where the tour is offered
The app SHALL offer to start the tour from the first-launch empty state and from Help > "ShogunAi Tour" at any time.

#### Scenario: First launch
- **WHEN** the app starts with no saved projects
- **THEN** the empty state offers "Take the tour" next to "Add project"

#### Scenario: Help menu
- **WHEN** the user has projects and chooses Help > "ShogunAi Tour"
- **THEN** the tour's dialog opens

### Requirement: Offer the tour once to existing users
When the app starts with saved projects, the tour has never been started and its notice was never dismissed, the app SHALL show a dismissible notice offering the tour. Starting the tour or dismissing the notice SHALL be remembered across restarts, and the notice SHALL NOT be shown again.

#### Scenario: Upgrade with projects
- **WHEN** a user who already had projects opens a version with the tour for the first time
- **THEN** a notice offers "Take the tour" and "Dismiss"

#### Scenario: Dismissed
- **WHEN** the user dismisses the notice and restarts the app
- **THEN** the notice isn't shown, and Help > "ShogunAi Tour" still starts the tour

#### Scenario: Started from the empty state
- **WHEN** a new user started the tour from the empty state and later has projects
- **THEN** the notice is never shown

### Requirement: The tour's steps
Starting the tour SHALL open a dialog that goes through, in order: a welcome that says what will be created and where, the command-line tool step, the creation of the sample repository, and opening the tour's agent. The user SHALL be able to cancel before the sample repository is created, and then nothing is written.

#### Scenario: Cancel at the welcome
- **WHEN** the user cancels the dialog at the welcome step
- **THEN** no folder, project or setting other than the notice's is written

### Requirement: Command-line tool step
The command-line tool step SHALL show whether `shogun` is available, installed but not on `PATH`, or missing, and SHALL offer "Install command-line tool" (or the manual steps when the app can't install it) and "Skip". The step SHALL be omitted when the tool is already available.

#### Scenario: Tool missing
- **WHEN** the tool is missing and the app can install it
- **THEN** the step offers "Install command-line tool" and "Skip", and after a successful installation it reports the folder and moves on

#### Scenario: Tool already available
- **WHEN** `shogun` is already on `PATH`, for example installed with Homebrew
- **THEN** the dialog goes from the welcome straight to creating the sample repository

#### Scenario: Skipped
- **WHEN** the user skips the step
- **THEN** the tour continues, and its agent explains how to install the tool when it reaches the command-line steps

#### Scenario: Not on PATH yet
- **WHEN** the installation put the tool in a folder that isn't on `PATH`
- **THEN** the step shows the line or folder to add and says that terminals and agents must be restarted, and lets the user continue

### Requirement: Sample repository
The tour SHALL create a Git repository at `~/.shogunai/tour/sample-repo` with main branch `main`, a bare remote at `~/.shogunai/tour/origin.git` as `origin`, and a `.shogunai.json` that declares a copied file, environment variables with a reserved port, an environment file and a post-creation command. It SHALL register it as the project "ShogunAi tour", with worktrees under `~/.shogunai/tour/worktrees`.

#### Scenario: Created
- **WHEN** the creation step finishes
- **THEN** the sidebar lists "ShogunAi tour", and its view shows the sample's worktrees

#### Scenario: Works without network or accounts
- **WHEN** the machine is offline and has no Git hosting account configured
- **THEN** the sample repository, its remote and its worktrees are created all the same

#### Scenario: Creation fails
- **WHEN** a Git command fails while creating the sample, for example because Git isn't installed
- **THEN** the dialog shows the command and its error output, leaves no project registered and removes the partly created folder

### Requirement: Worktrees in every state
The sample repository SHALL have worktrees that the app shows under each Git-derived state: "New", "Unfinished" (uncommitted changes), "Stopped" (pushed commits of its own, behind `main`), "Merged", "Inactive" (last commit older than the longest inactivity period the preferences allow), a locked worktree and "Folder missing".

#### Scenario: All worktrees by state
- **WHEN** the tour project's view is grouped by state after the sample is created
- **THEN** it shows worktrees under "Unfinished", "New", "Stopped", "Merged", "Inactive" and "Folder missing", and one row has the "locked" label

### Requirement: The tour's script
The sample repository SHALL contain an `AGENTS.md` with the tour's script and a `CLAUDE.md` that includes it. The script SHALL tell the agent to speak the language the app is set to, to cover status, cleanup, provisioning, the command line, hooks, environment and providers one step at a time, waiting for the user before each next step, and to change nothing outside `~/.shogunai/tour`.

#### Scenario: App in Spanish
- **WHEN** the app's language is Spanish when the tour starts
- **THEN** the script tells the agent to speak to the user in Spanish

#### Scenario: Tool skipped
- **WHEN** the agent reaches the command-line step and `shogun` can't be run
- **THEN** the script tells it to explain how to install the tool from the app instead of running `shogun` commands

### Requirement: The tour's agents
The tour project's agents SHALL start with the prompt "Start the ShogunAi tour": Claude Code by default and Codex as a second agent, without the Headroom wrapper.

#### Scenario: Copied launch command
- **WHEN** the user copies the launch command of the tour's main worktree on macOS
- **THEN** the command changes into `~/.shogunai/tour/sample-repo` and runs `claude "Start the ShogunAi tour"`

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

### Requirement: Tour banner
While the tour project exists, its view SHALL show a banner saying it's the tour's sample, with "Open the tour's agent" and "End tour".

#### Scenario: Reopen the agent
- **WHEN** the user activates "Open the tour's agent" in the banner
- **THEN** the app offers the same ways to open the agent as the dialog's last step

### Requirement: End the tour
"End tour" SHALL ask for confirmation, then remove the sample's worktrees releasing their reserved ports, unregister the tour project and delete `~/.shogunai/tour`. No other project or folder SHALL be touched.

#### Scenario: End
- **WHEN** the user confirms "End tour"
- **THEN** the tour project disappears from the sidebar, `~/.shogunai/tour` no longer exists and the ports its worktrees reserved are free again

#### Scenario: Folder already deleted
- **WHEN** the user deleted `~/.shogunai/tour` by hand and then ends the tour
- **THEN** the tour project is unregistered without an error

### Requirement: Restart the tour
Starting the tour while the tour project exists SHALL offer to restart it, which ends the existing tour and creates it again from scratch, or to open the existing tour project.

#### Scenario: Restart
- **WHEN** the tour project exists and the user chooses Help > "ShogunAi Tour" and then "Restart"
- **THEN** the sample is recreated with every worktree in its initial state
