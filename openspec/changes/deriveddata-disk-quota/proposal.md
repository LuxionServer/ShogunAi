# Proposal

## Why

DerivedData is the one machine resource that ShogunAi pools without bounding: emulators, simulators and Gradle daemons are limited by count, but each worktree an agent builds in leaves a DerivedData folder of several gigabytes that nothing releases until its worktree is deleted. With agents working in parallel the folders pile up and fill the disk. Roadmap item 1.

## What Changes

- Add a **DerivedData disk quota** to the machine's resource settings, edited in Preferences > Resources next to the capacity profile. It is a size in gigabytes or no quota, and defaults to no quota so nothing is deleted until the user opts in.
- When the folders of Xcode's DerivedData exceed the quota, **release** folders until the total is within it, following the pool model: orphaned folders first, then the least recently used folders of worktrees that are not in use. Folders of other projects, folders on unavailable volumes and the shared compilation cache are never released.
- A worktree is **in use** while its agent is working or blocked, or while its folder was accessed within the last hour; those folders are never released, and if only they remain the quota is reported as exceeded instead of forced.
- Apply the quota in the desktop app after DerivedData sizes are measured, and from a new `shogun xcode trim` command (with `--dry-run`), mirroring `shogun pool trim` for Gradle daemons.
- Show usage against the quota in the Xcode DerivedData view and its sidebar summary, and report what was released.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `resource-pools`: the machine settings gain the DerivedData quota, which is not part of the capacity profiles and, like the pool limits, is never read from `.shogunai.json`.
- `xcode-provider`: new requirements for what counts toward the quota, which folders can be released, in which order, and when the quota is applied.
- `machine-resources`: the DerivedData view and summary show usage against the quota and the folders released.
- `command-line-tool`: new `shogun xcode trim` command.

## Impact

- `core`: `ResourceSettings` (persisted in `settings.json`, backward compatible), a new use case that plans and runs the release on top of `ListDerivedDataUseCase` and `DerivedDataSource.delete`, and a way to know whether a worktree is in use (agent state, last access).
- `cli`: `xcode trim` command, help and JSON output.
- `desktopApp`: Preferences > Resources field, DerivedData view and summary, automatic application after measuring; English and Spanish strings.
- `docs/` (English and Spanish): providers and CLI guides, roadmap item 1 removed on archive.
- No new dependencies. Not breaking: without a quota, behaviour is unchanged.
