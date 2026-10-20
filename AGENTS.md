# AGENTS.md

Style and documentation guidelines for AI agents working in this repository. **This is not project documentation** — that's what `docs/` is for (architecture, decisions, process). This file is the single source of guidelines; `CLAUDE.md` and any other tool-specific file redirect here instead of duplicating content.

Don't load all of `docs/` into context by default: search for the specific page you need (`docs/architecture/*.md`, `docs/decisions/index.md`, `docs/process/openspec.md`) only when the task requires it.

## Code style

- **All code is written in English**: identifiers and commit messages. Spanish is reserved for the Spanish translation of `docs/` and conversation with the user.
- Use cases return `Result<T>` with typed errors (`sealed class WorktreeError`), never untyped exceptions surfaced to the caller.
- Shell commands are run as **argv** (`listOf("git", ...)`), never as a string for a shell to parse.
- The domain depends on ports (`ShellCommandExecutor`, `FileManager`), never directly on concrete implementations.
- **Never use company identifiers** (internal repo names, task prefixes, etc.) in code, tests, or examples. Use generic placeholders (`~/projects/main-repo`, `TASK-123`).

## How to document

| What you're documenting | Where |
|---|---|
| Architecture, models, system layers | `docs/architecture/` (mkdocs) |
| Design decision for a specific change | The change's `design.md` in OpenSpec (`openspec/changes/<name>/`) |
| Decisions predating OpenSpec (historical) | `docs/decisions/index.md` — don't edit old entries, read-only |
| Work process (how work is planned, how PRs are done, etc.) | `docs/process/` (mkdocs) |
| Style/behavior guidelines for AI | This file |

Rules:

- `docs/` (mkdocs) is for humans: it explains the *what* and *why* of the system, without single-session jargon. It's bilingual: English is the default (`page.md`) and every page has its Spanish translation (`page.es.md`); update both when you change one.
- This file (`AGENTS.md`) is for agents only: style and process rules, not project content. If you're going to add an architecture explanation or a decision, it goes in `docs/` or OpenSpec, not here.
- Non-trivial changes are planned with OpenSpec (`/opsx:propose` → `/opsx:apply` → `/opsx:archive`) before being implemented — see `docs/process/openspec.md`.
- Archive a change (`/opsx:archive`) right after its implementation is tested and validated, in the same branch and **before** opening the pull request, so the archive, synced specs and roadmap update ship in that pull request. Don't leave archiving for a follow-up pull request after merge.
- When archiving a change, check whether it affects the roadmap (`docs/roadmap.md` and `docs/roadmap.es.md`) and update both in the same branch: remove the items the change completes and renumber the remaining ones, update the status of the layers in "Direction" if it changes, and add the follow-ups the change left out.

## Commands

```bash
./gradlew :desktopApp:run            # run the app
./gradlew :desktopApp:hotRun --auto  # run with hot reload
./gradlew :cli:run --args="status"   # run the shogun CLI
./gradlew test                       # run tests for all modules
mkdocs serve                         # serve the docs locally
```

## Git

- Don't add the `Co-Authored-By: Claude` trailer (or for any AI) in this project's commits.
- Commit messages are always in English, even though the user converses in Spanish.

### Opening a pull request

Branching model: `docs/process/branching.md`. `main` only receives releases and is never a pull request target for a feature, fix, chore or docs branch.

- Never take the base branch from the "main branch" shown in the session context, and never pass `--base main`. Derive it from where the branch comes from.
- Before `gh pr create`, run `git merge-base --fork-point` or compare `git log --oneline origin/develop..HEAD` with the epic's, and state the base you will use together with the evidence (the branch's parent and what the commits are).
- The base is `develop` by default, or the `epic/<name>` branch when the change was branched from one. A `release/**` branch is the only one allowed to target `main`.
- Always pass `--base <branch>` explicitly, never rely on the default.
- If the base is not obvious, ask before creating the pull request. A merged pull request on the wrong branch is not undone by closing it.

## Public repository

This repository (`LuxionServer/ShogunAi`) is **public**: every commit, commit message, PR and issue comment is published permanently, and rewriting history doesn't unpublish it.

- Never commit secrets: the `HOMEBREW_TAP_TOKEN`, `SCOOP_BUCKET_TOKEN` and `WINGET_TOKEN` used by the release workflow (they live only in the repository's Actions secrets), any other token or key, signing material, `.env` files or `local.properties`.
- Never commit a user's local app data: `~/.shogunai/projects.json` and `~/.shogunai/settings.json` contain real filesystem paths and project names. Tests and examples use generic placeholders (`~/projects/main-repo`, `TASK-123`).
- Never commit machine-specific state: absolute paths with a home directory, IDE or tool-local settings (e.g. `.claude/settings.local.json`), logs or build outputs.
- Before committing, review `git diff --staged`, stage explicit paths (no `git add -A` / `git add .`) and add new local or generated files to `.gitignore`.
- If something sensitive was already committed or pushed, stop and report it instead of amending or force-pushing: a leaked secret must be rotated first.
