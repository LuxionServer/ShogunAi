# Proposal

## Why

Opening the app, or running `shogun multirepo init`, while an external volume is unmounted removes from `~/.shogunai/multirepos.json` every multirepo that lives on it, together with its approval and local agent. When the volume comes back the multirepo is gone and has to be added and reviewed again. The registry can't tell a deleted folder from one that is temporarily unreachable, and projects already behave the safe way: their entry stays and they show that the repository can't be found.

## What Changes

- **BREAKING (behavior)**: neither the app nor the CLI forgets a known multirepo on its own any more. `shogun multirepo init` and the app's reload leave `~/.shogunai/multirepos.json` untouched, including the approval and local agent of each entry.
- The app lists in the sidebar, as unavailable, every known multirepo whose declaration can't be read: its folder can't be reached (unmounted volume, network drive down, moved or deleted), the folder has no declaration, or the declaration is invalid. The row is named after the folder, marked as unavailable with the reason, and has no projects nested under it.
- Selecting an unavailable multirepo shows why it's unavailable and offers "Remove" and "Try again". "Remove", after a confirmation, forgets the entry with its approval and local agent; it's the only way the app drops it.
- When the folder becomes reachable again, the next reload shows the multirepo as before, still approved and with its local agent.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `multirepo-desktop`: the requirement "The app forgets multirepos whose folder is gone" is replaced by listing unreachable multirepos as unavailable with an explicit removal.
- `multirepo-groups`: the requirement "Forget multirepos whose folder is gone" is removed, and "Register an existing multirepo"'s moved-folder scenario no longer drops the old entry.

## Impact

- `core`: `MultirepoSource.forgetMissing` and its JSON and fake implementations are removed; `ResolveMultirepoUseCase` lists the known multirepos split into available and unavailable ones.
- `cli`: `shogun multirepo init` stops calling `forgetMissing`.
- `desktopApp`: `ShellViewModel` and `MultirepoCatalog` keep the unavailable multirepos; the sidebar, the multirepo destination and a removal dialog show them; new strings in English and Spanish.
- Docs: `docs/guide/multirepo*.md`, `docs/guide/desktop-app*.md`, `docs/guide/cli*.md` and the roadmap.
