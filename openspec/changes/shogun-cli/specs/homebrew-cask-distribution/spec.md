# Spec Delta

## ADDED Requirements

### Requirement: Install the shogun command with the Cask
The Cask SHALL link the desktop app's executable as `shogun` in Homebrew's `bin` folder, so installing the app also installs the command line tool, and uninstalling removes both.

#### Scenario: Command available after install
- **WHEN** the user runs `brew install --cask shogunai`
- **THEN** `shogun --version` works from a new terminal and prints the installed version

#### Scenario: Uninstall
- **WHEN** the user runs `brew uninstall --cask shogunai`
- **THEN** the `shogun` link is removed together with the app
