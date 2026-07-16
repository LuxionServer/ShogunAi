# AGENTS.md

Style and documentation guidelines for AI agents working in this repository. **Not project documentation** — that lives in `docs/` (architecture, decisions, process). This file is the single source of guidelines; `CLAUDE.md` and any other tool-specific file redirect here instead of duplicating content.

Don't load all of `docs/` into context by default: search for the specific page you need (`docs/architecture/*.md`, `docs/decisions/index.md`, `docs/process/openspec.md`) only when the task requires it.

## Code style

- Domain comments in **Spanish**, consistent with the existing code.
- Use cases return `Result<T>` with typed errors (`sealed class WorktreeError`), never untyped exceptions to the caller.
- Shell commands are executed as **argv** (`listOf("git", ...)`), never as a string for a shell.
- The domain depends on ports (`ShellCommandExecutor`, `FileManager`), never directly on concrete implementations.
- **Never use company identifiers** (internal repo names, task prefixes, etc.) in code, tests, or examples. Use generic placeholders (`~/projects/main-repo`, `TASK-123`).

## How to document

| What you're documenting | Where |
|---|---|
| Architecture, models, system layers | `docs/architecture/` (mkdocs) |
| Design decision for a specific change | `design.md` of the change in OpenSpec (`openspec/changes/<name>/`) |
| Decisions predating OpenSpec (historical) | `docs/decisions/index.md` — don't edit old entries, read-only |
| Work process (how planning works, how PRs are done, etc.) | `docs/process/` (mkdocs) |
| Style/behavior guidelines for AI | This file |

Rules:

- `docs/` (mkdocs) is for humans: explains the *what* and *why* of the system, in Spanish, without single-session jargon.
- This file (`AGENTS.md`) is only for agents: style and process rules, not project content. If you're adding an architecture explanation or a decision, it goes in `docs/` or OpenSpec, not here.
- Non-trivial changes are planned with OpenSpec (`/opsx:propose` → `/opsx:apply` → `/opsx:archive`) before being implemented — see `docs/process/openspec.md`.

## Commands

```bash
./gradlew :desktopApp:run            # run the app
./gradlew :desktopApp:hotRun --auto  # run with hot reload
./gradlew test                       # run tests for all modules
mkdocs serve                         # serve the docs locally
```

## Git

- Don't add the `Co-Authored-By: Claude` trailer (or any AI's) to commits in this project.
- Commit messages are always in English, even though the rest of the project's documentation is in Spanish.
