# Proposal

## Why

`shogun multirepo trust --revoke` and `shogun multirepo delete` read the multirepo's declaration first, so a known multirepo whose declaration can't be read (its folder can't be reached, the folder has no declaration, or the declaration is invalid) can only be forgotten with "Remove" in the desktop app.
Worse, `trust --revoke` in such a folder walks up to the nearest readable declaration and may revoke an enclosing multirepo instead.
The roadmap lists this gap under "Multirepos for the whole team".

## What Changes

- `shogun multirepo trust [<folder>] --revoke` forgets the known multirepo at that folder (or the current directory) when its declaration can't be read, as the desktop app's "Remove" does: the entry, its approval and its local agent leave `~/.shogunai/multirepos.json`, and whatever is in the folder stays.
- Its output names the multirepo and why its declaration can't be read; `--json` reports the folder, the declaration path, `approved: false`, `changed: true` and the reason.
- A folder that is a known unavailable multirepo is never resolved to an enclosing multirepo for `--revoke`.
- `shogun doctor` names that command in the fix of a multirepo whose declaration is missing or invalid, and no longer says ShogunAi forgets it on its own.
- A core use case does the forgetting, so the CLI doesn't call the multirepo store directly.

## Capabilities

### New Capabilities

### Modified Capabilities

- `multirepo-trust`: revoking from the command line also forgets a known multirepo whose declaration can't be read.
- `installation-check`: the fixes for a missing or invalid multirepo declaration point to `shogun multirepo trust <folder> --revoke`.

## Impact

- `core`: `ResolveMultirepoUseCase` finds a known unavailable multirepo by folder; `ConfigureMultirepoUseCase` forgets it; `InstallationReportText` updates two fixes.
- `cli`: `ShogunCli.trustMultirepo`, the `trust` help text and `MultirepoJson`.
- Docs: the multirepo guide (`docs/guide/multirepo.md` and `.es.md`) and the roadmap at archive time.
- No change to the desktop app, the declaration format or `~/.shogunai/multirepos.json`.
