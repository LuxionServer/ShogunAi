# Spec Delta

## Purpose

Defines how the `.deb` the release publishes installs and uninstalls on Debian and Ubuntu machines, whether or not they have a desktop.

## ADDED Requirements

### Requirement: Install without a system menus folder
The `.deb` SHALL install and finish configuring on a machine without a writable system menus folder (`/etc/xdg/menus`), such as a server or a minimal container, printing a warning that the app wasn't added to the applications menu.

#### Scenario: Minimal container
- **WHEN** `apt-get install ./ShogunAi.deb` runs on an Ubuntu machine without `/etc/xdg/menus`
- **THEN** the command exits with status 0, the package is fully installed, the app's launcher is installed under `/opt/shogunai` and the output warns that no applications menu entry was added

### Requirement: Uninstall without a system menus folder
Removing the `.deb` SHALL succeed on a machine without a writable system menus folder, whether it was installed there or the folder was removed afterwards.

#### Scenario: Remove from a minimal container
- **WHEN** `apt-get remove shogunai` runs on a machine without `/etc/xdg/menus`
- **THEN** the command exits with status 0 and the package is no longer installed

### Requirement: Applications menu entry on a desktop
On a machine with a system menus folder, installing the `.deb` SHALL add ShogunAi to the applications menu and removing it SHALL remove that entry.

#### Scenario: Desktop install and removal
- **WHEN** the `.deb` is installed on a machine with `/etc/xdg/menus`, and later removed
- **THEN** after installing, the ShogunAi desktop entry is in the system applications folder `xdg-desktop-menu` chose (`/usr/local/share/applications` or `/usr/share/applications`); after removing, it is in neither
