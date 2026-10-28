# Proposal

## Why

Two settings of a multirepo can only be changed by editing `.shogunai-multirepo.json` by hand: its shared variables, which then make the multirepo need review with `shogun multirepo trust` before they apply, and its workspace repository once `workspace clear` or `workspace set` has written the `workspace` field, since nothing removes it again. The roadmap's fourth item, "Multirepos for the whole team", lists both as left out; every other part of the declaration is already edited from `shogun multirepo …` and the multirepo settings.

## What Changes

- `shogun multirepo env` lists the shared variables; `shogun multirepo env set <alias> <name> <template>` adds or replaces one, validated with the rules of the declaration's `env`; `shogun multirepo env unset <alias> <name>` removes one. All accept `--json`.
- The multirepo settings gain a "Shared variables" section: one row per variable with its repository, name and template, which can be edited and removed, and a row to add one. They are saved with the rest of the settings, and an invalid row is named before anything is written.
- Editing shared variables through ShogunAi keeps the multirepo approved, as every other write of ShogunAi does: the user is the one writing the templates. Like the other writes, it is refused while the multirepo needs review, and also while the `env` block holds something ShogunAi can't represent (a non-object entry or a non-string value), which rewriting would lose.
- Renaming a repository carries its shared variables and the `${port:<alias>.<key>}` references to it over to the new alias; removing a repository that other repositories' shared variables reference is refused, naming them, and removing one drops its own shared variables. Today both leave a declaration whose `env` is invalid.
- `shogun multirepo workspace detect` removes the `workspace` field, so the folder goes back to being detected as the workspace repository `workspace`. When the declared alias was another one, its shared variables and references follow it as in a rename; it is refused when `workspace` is already the alias of a declared repository.
- The workspace section of the multirepo settings gains "Return to detection", shown when the workspace repository is declared or turned off.

`shogun group add <id> <worktree>`, the third piece of the roadmap item considered for this change, is left for a change of its own: it gives groups local state, which today have none, and touches group detection, shared variable resolution, cleanup and every group view.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `multirepo-environment`: shared variables can be listed, set and unset from the command line, and follow renames and removals of repositories.
- `multirepo-groups`: `shogun multirepo workspace detect` returns the workspace repository to detection; removing a referenced repository is refused.
- `multirepo-desktop`: the multirepo settings edit shared variables and return the workspace repository to detection.
- `multirepo-trust`: editing shared variables is one of ShogunAi's own writes that keep a multirepo approved.

## Impact

- `core`: `ConfigureMultirepoUseCase` gains `setSharedVariable`, `unsetSharedVariable`, `setSharedVariables` and `detectWorkspace`, and `renameRepository`, `removeRepository` and `setWorkspace` carry or check shared variables; a pure helper renames an alias in an `env` block.
- `cli`: `multirepo env` and `multirepo workspace detect` parsing, output (text and JSON) and help.
- `desktopApp`: `MultirepoSetup` and its implementation, `MultirepoSettingsState` and `MultirepoSettingsView`, English and Spanish strings.
- Docs: the multirepo guide (English and Spanish), and the roadmap on archive, where `shogun group add` stays as a pending item.
- No overlap with the parallel Maven, iOS caches and worktree origin ref changes beyond `ShogunCli.kt` and `CliHelp.kt`, where each adds its own subcommands.
