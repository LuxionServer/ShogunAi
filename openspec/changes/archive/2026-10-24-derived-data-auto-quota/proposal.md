# Proposal

## Why

The DerivedData quota only protects the disk once the user opts in: by default there is no quota, so on most machines DerivedData still grows without bound. And applying it, or just showing the usage, waits for every folder to be measured one at a time from scratch on each app run, which on a location with hundreds of gigabytes takes minutes. Roadmap item 4 (two of its points).

## What Changes

- **Automatic DerivedData quota, the new default.** The quota becomes one of three modes: Automatic, a fixed size in gigabytes, or no quota. Automatic derives the quota from the disk that holds DerivedData, as the Automatic capacity profile derives limits from memory: a quarter of the space DerivedData could use (free space plus what DerivedData already takes), between 20 GB and 200 GB. Settings without a fixed quota move to Automatic, so deleting folders by quota is now on by default; the release rules (orphans first, never folders in use or of other projects) are unchanged. **BREAKING** for installs that relied on no quota by default: they have to choose "No quota" explicitly.
- Preferences > Resources offers the three modes and explains how Automatic is computed. The DerivedData view, its sidebar summary and `shogun xcode trim` show the quota in use and whether it is automatic.
- **Faster DerivedData sizes.** Folders are measured several at a time instead of one by one, and their sizes are remembered between runs of the app and the `shogun` command: a folder Xcode hasn't used since it was measured shows and counts its remembered size without being measured again. Reloading the view still measures every folder again, showing the remembered sizes until the new ones arrive.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `resource-pools`: the DerivedData quota setting gains the Automatic mode as its default, how it is computed, and the three-mode editor in Preferences.
- `xcode-provider`: usage against the quota with no quota only when chosen; remembered sizes and parallel measurement of DerivedData folders.
- `machine-resources`: sizes computed several at a time and shown from memory first; the view and summary say when the quota is automatic.
- `command-line-tool`: `shogun xcode trim` applies the automatic quota and only reports "no quota" when the user chose it.

## Impact

- `core`: `ResourceSettings` (quota mode, backward-compatible decoding), a use case that resolves the effective quota from free disk space and usage, free space of the DerivedData volume in `DerivedDataSource`, a remembered-sizes store under `~/.shogunai/` and a measuring use case that runs folders in parallel and reuses remembered sizes.
- `cli`: `xcode trim` and `xcode` use the effective quota and remembered sizes; JSON gains the quota mode.
- `desktopApp`: Preferences > Resources quota editor, Machine DerivedData measurement and quota labels; English and Spanish strings.
- `docs/` (English and Spanish): providers, CLI and desktop app guides; roadmap item 4 updated on archive.
- No new dependencies.
