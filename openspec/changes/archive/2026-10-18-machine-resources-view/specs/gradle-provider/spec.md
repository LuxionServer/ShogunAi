# Spec Delta

## REMOVED Requirements

### Requirement: Gradle daemons on the worktree screen
**Reason**: Daemons belong to the machine and serve builds of any repository, so they move out of one project's view.
**Migration**: The "Gradle daemons" view of the sidebar's "Machine" section lists every daemon with its owner across saved projects (`machine-resources`: "Gradle daemons across projects").

### Requirement: Gradle settings on the worktree screen
**Reason**: The settings are shown next to the daemons they affect, which are no longer on the worktree screen.
**Migration**: The machine's "Gradle daemons" view states each project's worktree build settings (`machine-resources`: "Gradle settings and build cache per project").
