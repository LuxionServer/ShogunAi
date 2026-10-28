# Design

## Context

Every write to `.shogunai-multirepo.json` goes through `ConfigureMultirepoUseCase.update`, which refuses an unapproved multirepo, drops the ignored `agent`, writes the declaration and approves the written content. `shogun multirepo …` subcommands and the desktop app's multirepo settings (through `MultirepoSetup`) both call it. Two parts of the declaration are outside that path today: `env`, added by `multirepo-shared-variables` as hand-edited only, and the removal of the `workspace` field. `env` is a `SharedVariablesDeclaration` whose serializer keeps the valid string templates in `byAlias` and reports the rest in `problems`; `parseSharedEnvironment` validates the whole block against the multirepo's aliases.

Two existing writes already break `env`: `renameRepository` leaves `env.<old>` and `${port:<old>.<key>}` behind, and `removeRepository` leaves `env.<alias>` and references to it, so the declaration becomes invalid after either.

## Goals / Non-Goals

**Goals:**
- Shared variables and the workspace field are edited through the same write path as the rest of the declaration, so approval, the needs-review refusal and the ignored `agent` behave the same.
- No ShogunAi write leaves an `env` that the next read rejects, and none silently drops part of it.
- The desktop settings edit the same data with the same validation messages as the command line.

**Non-Goals:**
- `shogun group add <id> <worktree>` and any group state; it gets its own change.
- Editing a repository's own `.shogunai.json` `env` from the multirepo.
- Rewriting references inside a template other than `${port:<alias>.<key>}`, which is the only placeholder that names an alias.

## Decisions

### Editing keeps the multirepo approved

The approval exists so that content written by someone else (a pull, a clone) doesn't apply without the user seeing it. A template the user types into `env set` or the settings has been seen by definition, so these writes approve their result like every other ShogunAi write, and are refused while the multirepo needs review, so approving an edit can never approve unreviewed content that came with a pull. The alternative, writing and then asking for `shogun multirepo trust`, would add a step with nothing new to review.

### One use case for each operation, one for the settings

`ConfigureMultirepoUseCase` gains:
- `setSharedVariable(multirepo, alias, name, template)` and `unsetSharedVariable(multirepo, alias, name)`, used by the command line.
- `setSharedVariables(multirepo, byAlias)`, used by the settings, which replaces the whole block in one write: the settings edit a draft and save once, as they do for the name and defaults.

All three validate the resulting block with `parseSharedEnvironment` against the multirepo's aliases before writing, so messages are the ones `shogun multirepo trust` and `shogun env` already print. Validating the whole result rather than the single variable means a set can't succeed while leaving the block invalid for another reason. An unknown alias raises `UnknownMultirepoAlias`, which the command line already maps to exit code 2.

### Refuse to rewrite what can't be represented

When `env.problems` isn't empty, the serializer has already dropped those entries from `byAlias`, so any rewrite would delete them silently. On the command line this can't happen today, because `ResolveMultirepoUseCase.validate` rejects such a declaration before any subcommand runs, but the desktop settings read the declaration through `MultirepoSetup.read` and write through the same use case. So `update` and `addRepository` refuse with `WorktreeError.InvalidMultirepo` naming the first problem whenever `env.problems` isn't empty, for every write, not only the `env` ones, and the settings show the problem in the section instead of the editor. Fixing such a block stays a hand edit: a subcommand that loaded an invalid declaration to repair it would need its own path around validation, for a case the user created by hand.

### Rename and remove carry `env`

A pure function `renameSharedAlias(env, old, new)` moves `byAlias[old]` to `new` and replaces `${port:old.` with `${port:new.` in every template, skipping `$$` escapes as the template parser does. It works on the template text, not the parsed parts, so the template is otherwise kept as written. `renameRepository` uses it, and so does `workspace detect` when the declared alias isn't `workspace`.

`removeRepository` drops `byAlias[alias]` and refuses when another alias's template references it, naming each `<alias>.<name>`. Rewriting those references to something else has no right answer, so the user decides.

### `workspace detect`

`detectWorkspace(multirepo)` sets `workspace = null` through `update`. It refuses when the folder isn't a repository (same message as `setWorkspace`), when `repos` lists the folder as `.` (the field has no effect there), and when `workspace` is a key of `repos`, since detection would then add no workspace repository at all. With no field it returns the multirepo unchanged and the command line says it's already detected. A declared alias other than `workspace` is renamed with `renameSharedAlias`; `defaultRepos` never holds the workspace alias (it lives in `workspace.default`), so leaving the default is implicit, and the command line prints it.

The verb is `detect` because it names the result; `workspace unset` would read as turning the repository off, which is `clear`.

### Command line

`shogun multirepo env` with no further argument lists, like `show`, and is allowed while the multirepo needs review; `env set` and `env unset` are writes and refuse it. Templates contain `$`, so the help shows them single-quoted. Text output after a write prints the resulting list; `--json` prints `{ "env": … }` with the same shape as `show --json`.

### Settings

`MultirepoSettingsState` holds a list of `SharedVariableDraft(alias, name, template)` built from `sharedTemplates`, compared with the declaration in `hasChanges`. Each row is validated on change with `parseSharedEnvironmentVariable`, plus duplicate names per alias; `canSave` requires every row valid. `save` calls `setup.setSharedVariables` when the drafts differ. "Return to detection" is an immediate action like "Turn off detection", through `setup.detectWorkspace`.

## Risks / Trade-offs

- Older ShogunAi versions drop `env` when they rewrite the declaration, as documented by the previous change; editing from the CLI doesn't change that.
- The rename scans the template as the parser does, skipping `$$`, so a literal `$${port:api.http}` (which renders as the text `${port:api.http}`) isn't rewritten.
- Refusing `remove-repo` while it's referenced adds a step for the user, preferred to leaving an invalid declaration.
