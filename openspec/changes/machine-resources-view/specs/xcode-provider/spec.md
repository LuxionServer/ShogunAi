# Spec Delta

## REMOVED Requirements

### Requirement: DerivedData on the worktree screen
**Reason**: DerivedData lives in one machine-wide location shared by every project, so it moves out of one project's view.
**Migration**: The "Xcode DerivedData" view of the sidebar's "Machine" section lists every folder with its owner across saved projects (`machine-resources`: "Xcode DerivedData across projects").
