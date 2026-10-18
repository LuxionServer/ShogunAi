# Design

## Context

`RepositoryProvisioningFiles` reads `.shogunai.json` and `.worktreeinclude`, and already writes `.shogunai.json` atomically for "Move to .shogunai.json", appending `copy` patterns while keeping the other fields. The readers validate `providers` (`gradle`, `emulator`, `ios-simulator`, `xcode`), `env` and `envFile` and report an invalid file with a typed error. `ProjectConfigViewModel` already writes the repository file as part of saving, and handles a write failure by keeping the form open. After `project-settings-view`, the settings are a sectioned view with a save bar and a leave guard.

## Goals / Non-Goals

**Goals:**
- Declaring providers and the environment without knowing the file's format.
- Never losing anything a hand-written `.shogunai.json` contains.

**Non-Goals:**
- Editing `copy` patterns from the UI beyond the existing "move local files" action.
- Formatting-preserving edits (comments aren't valid JSON anyway). Whitespace and key order of untouched parts are best-effort.
- Committing the file. The app only writes it to the working tree.

## Decisions

### Edit the parsed JSON tree, not a typed model
The writer parses the file into a `JsonObject`, replaces only the `providers.<known id>` entries the user touched, `env` and `envFile`, and serializes with 2-space indentation, keeping key order. Unknown top-level keys and unknown providers stay untouched because they're never modeled. *Alternative considered:* a full typed model of the file. Rejected because it would drop fields that a newer ShogunAi version, or the user, added.

### One source of validation rules
The readers' rules (memory size regex, positive integer minutes, variable name regex, known template placeholders, relative `envFile` inside the worktree) are extracted into `RepositoryConfigRules` in `core`. The readers and the form both use them, so a value the form accepts is a value the reader accepts. Form errors are typed (`InvalidMemorySize`, `InvalidMinutes`, `InvalidVariableName`, `UnknownPlaceholder`, `EnvFileOutsideWorktree`) and rendered with localized messages.

### Concurrency check with a content hash
When the settings open, the file's content hash is recorded (or "absent"). Before writing, the hash is checked again. If it differs, the write is refused with a typed error, and the section offers to reload. The existing "move local files" path goes through the same check.

### Save order and atomicity
Saving runs: validate everything, show the preview if it's the first repository write in this visit, write `.shogunai.json` atomically, then save the project. If the repository write fails, the project isn't saved, which matches today's behavior for "move local files". After a successful save, the shell asks `WorktreeBoard` to reload that project and `MachineResources` to refresh `declaredProviders`.

### Section layout
"Repository configuration" sits between "Worktree files" and "Agent". It has a short note that it's stored in the repository's `.shogunai.json` and shared with everyone who clones it. Providers are rows with a switch. Their options expand under the switch when it's on. Environment variables are a two-column editable list (name and template) with add and remove `IconAction`s, plus a help popover with the templates. Platform-only providers aren't shown on other platforms, but their declarations are kept in the file.

## Risks / Trade-offs

- [Rewriting the file changes whitespace in parts the user didn't edit] → Key order is kept and indentation is normalized to 2 spaces. The preview shows exactly what will be written before the first write.
- [A team-shared file edited from one machine affects everyone] → The section says so, and the preview reminds the user to commit the change deliberately.
- [The template help can drift from the resolver's actual syntax] → The help text is built from the same template constants the resolver uses, and a test asserts each documented template resolves.
