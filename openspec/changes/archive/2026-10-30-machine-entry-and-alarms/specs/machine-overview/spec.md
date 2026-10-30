# Spec Delta

## Purpose

Gives the main window one way into the machine's shared resources from any state, listing the desktop turn and every resource that applies on this machine, and explaining how to get more when no project declares a provider.

## ADDED Requirements

### Requirement: Machine overview lists the machine's resources
The Machine overview SHALL list the desktop turn and every Machine resource that applies on this machine, in the status bar's order, one row each with its icon, full name, figure and full summary. Activating a row SHALL open that resource's view. The desktop turn SHALL be listed even when nobody holds it.

#### Scenario: Gradle declared
- **WHEN** one saved project declares the Gradle provider and nobody holds the desktop turn
- **THEN** the overview lists Desktop, saying it's free, followed by Gradle daemons with its figure

#### Scenario: Open a resource from the overview
- **WHEN** the user activates the Gradle daemons row of the overview
- **THEN** the content area shows the Gradle daemons view

### Requirement: Overview without providers
When no saved project declares a provider, the Machine overview SHALL say so and explain that declaring a provider in a project's settings adds its daemons, devices and caches to Machine, below the desktop turn's row.

#### Scenario: Nothing declared
- **WHEN** no saved project declares a provider
- **THEN** the overview lists only Desktop and explains how a provider adds resources to Machine

### Requirement: The overview is restored
The Machine overview SHALL be a view of its own that the window reopens on the next launch when it was the last one shown.

#### Scenario: Reopen on the overview
- **WHEN** the user closes the app while the Machine overview is shown and opens it again
- **THEN** the Machine overview is shown
