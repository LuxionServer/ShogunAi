# Proposal

## Why

Reviewing the redesigned interface end to end left five small findings in the roadmap ("Polish after the interface redesign"). One of them misleads the user and cleanup: a worktree whose branch has no commits of its own reads as "merged into main" as soon as the main branch moves on, so a worktree created minutes ago can show up as a cleanup candidate. The others are friction: a double confirmation, a title that doesn't say which project, declared files that aren't listed, and machine views whose rows say "—".

## What Changes

- A branch that never had commits of its own reads as "not started" instead of merged, in the desktop app and in `shogun status`, and is never a cleanup candidate for being merged.
- "Discard" in the project settings' save bar discards without asking again; leaving with unsaved edits by any other way still asks.
- The project settings title names the project ("Settings · <project>").
- The repository's provisioning list in project settings also shows the `link` patterns, marked as linked, and no longer says the repository declares nothing when it only declares `link` patterns or post-creation commands.
- The Android emulators and iOS simulators views show a column header row and, for a worktree whose device isn't running or doesn't exist yet, the device it will use instead of "—".

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-status`: a branch without commits of its own is "not started", not merged.
- `project-configuration`: "Discard" in the save bar doesn't ask again; the title names the project; the provisioning list includes `link` patterns and post-creation commands.
- `machine-resources`: column labels and the device a stopped worktree emulator or simulator will use.

## Impact

- `core`: `WorktreeStatus` gains a "not started" reading; `ReadWorktreeStatusUseCase` checks the branch's reflog only when the branch would otherwise read as merged; `worktreeActivity` and cleanup selection follow it.
- `cli`: `shogun status` shows "not started" instead of "merged" for those branches.
- `desktopApp`: row chips and detail status, the settings save bar and title, `RepositoryProvisioningList`, `AndroidEmulatorsView`, `IosSimulatorsView`, `MachineViewParts`, strings in English and Spanish.
- Docs: the guide pages describing worktree states and the machine views, in both languages.
- Expected small conflicts with `cli-desktop-parity` (strings, `WorktreeDetailPane.kt`).
