# herdr-plugin-health Specification

## Purpose
Notices when ShogunAi's Herdr plugin, which provisions the worktrees Herdr creates, is missing, disabled or broken, and reinstalls it from the repository from the command line or the app's Settings.

## Requirements

### Requirement: Tell the Herdr plugin's state
ShogunAi SHALL read Herdr's installed plugins and classify its plugin (`shogunai.worktrees`) as installed, disabled, broken or missing. It SHALL be broken when Herdr reports warnings for it, whatever its enabled flag, and disabled when it has no warnings and Herdr reports it disabled.

#### Scenario: Linked to a folder that no longer exists
- **WHEN** the plugin is linked to a folder that was deleted and Herdr lists it enabled with the warning `manifest unavailable: No such file or directory (os error 2)`
- **THEN** the plugin is broken, with that warning and the folder it is linked to

#### Scenario: Working install
- **WHEN** Herdr lists the plugin enabled and without warnings, installed from GitHub or linked to an existing folder
- **THEN** the plugin is installed

#### Scenario: Disabled by the user
- **WHEN** Herdr lists the plugin disabled and without warnings
- **THEN** the plugin is disabled

#### Scenario: Not installed
- **WHEN** Herdr lists no plugin with the id `shogunai.worktrees`
- **THEN** the plugin is missing

### Requirement: Report the Herdr plugin in hooks status
`shogun hooks status` SHALL add a line for the Herdr plugin, after the Claude Code ones, with its state, its source and, when broken, Herdr's warnings and the command that reinstalls it; `--json` SHALL add a `herdr` object with the same. Without Herdr installed there SHALL be no line and `herdr` SHALL be `null`.

#### Scenario: Broken plugin
- **WHEN** the plugin is linked to a deleted folder and the user runs `shogun hooks status`
- **THEN** the output says the Herdr plugin is broken, shows Herdr's warning and the folder, suggests `shogun hooks install --herdr`, and the command exits with code 0

#### Scenario: Missing plugin
- **WHEN** Herdr is installed without the plugin
- **THEN** the output says the Herdr plugin is missing and suggests `shogun hooks install --herdr`

#### Scenario: Herdr not installed
- **WHEN** the `herdr` command can't be found
- **THEN** the output has only the Claude Code lines, and `--json` prints `"herdr": null`

#### Scenario: Herdr fails
- **WHEN** `herdr plugin list` exits with an error
- **THEN** the line says the plugin couldn't be checked with Herdr's message, `--json` gives the state `unknown` with that message, and the command still exits with code 0

### Requirement: Reinstall the Herdr plugin from the command line
`shogun hooks install --herdr` SHALL, when the plugin is broken or missing, list the Herdr commands it will run, ask before running them unless `--yes` is given, and reinstall the plugin from the repository. When the plugin is installed or disabled it SHALL say so and run nothing.

#### Scenario: Reinstall a broken linked plugin
- **WHEN** the plugin is linked to a deleted folder and the user runs `shogun hooks install --herdr --yes`
- **THEN** ShogunAi runs `herdr plugin unlink shogunai.worktrees` and then `herdr plugin install LuxionServer/ShogunAi/integrations/herdr --yes`, and afterwards the plugin is installed

#### Scenario: Reinstall a broken GitHub install
- **WHEN** the plugin was installed from GitHub and Herdr reports a warning for it
- **THEN** the first command is `herdr plugin uninstall shogunai.worktrees` instead of `unlink`

#### Scenario: Install a missing plugin
- **WHEN** the plugin is missing and the user confirms
- **THEN** only `herdr plugin install LuxionServer/ShogunAi/integrations/herdr --yes` runs

#### Scenario: Not confirmed
- **WHEN** the user answers anything but yes
- **THEN** no Herdr command runs and the output says nothing was changed

#### Scenario: Already installed or disabled
- **WHEN** the plugin is installed, or disabled by the user
- **THEN** the command says so, runs no Herdr command and exits with code 0

#### Scenario: A Herdr command fails
- **WHEN** a reinstall command fails, e.g. because there is no network
- **THEN** the command prints Herdr's error on standard error, runs no further command and exits with code 1

#### Scenario: Herdr not installed
- **WHEN** the `herdr` command can't be found
- **THEN** the command says Herdr isn't installed and exits with code 1

#### Scenario: Options that don't apply
- **WHEN** `--herdr` is given with `--project`, or with an action other than `install`
- **THEN** the command fails with a usage error

### Requirement: The Herdr plugin in the app's Settings
When Herdr is installed, the hooks section of the app's Settings SHALL show the Herdr plugin's state, with Herdr's warnings when broken, and a prominent "Reinstall" (broken) or "Install" (missing) action that lists the Herdr commands and runs them after confirmation. Without Herdr, the section SHALL show nothing about it.

#### Scenario: Reinstall from Settings
- **WHEN** the plugin is linked to a deleted folder and the user activates "Reinstall" and confirms
- **THEN** the Herdr commands run and the section shows the plugin as installed without restarting the app

#### Scenario: Cancelled
- **WHEN** the user cancels the confirmation
- **THEN** no Herdr command runs and the section still shows the plugin as broken

#### Scenario: Reinstall fails
- **WHEN** a Herdr command fails
- **THEN** the section shows Herdr's error and the plugin's state read again afterwards

#### Scenario: Installed or disabled
- **WHEN** the plugin is installed or disabled
- **THEN** the section shows its state and offers no action

#### Scenario: Herdr not installed
- **WHEN** Herdr isn't installed
- **THEN** the hooks section shows no Herdr plugin part
