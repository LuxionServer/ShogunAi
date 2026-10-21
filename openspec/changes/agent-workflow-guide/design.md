# Design

## Context

`shogun hook session-start` (`ShogunCli.hookSessionStart`) prints one line of context: the worktree's variables plus the emulator and simulator hints, built as concatenated strings in `ShogunCli`. It returns silently when the environment and the hints are empty. `ClaudeCodeHooks` edits Claude Code's JSON settings; nothing manages Markdown instructions files. Agents without hooks (Codex, Cursor, Gemini) only have `provision` recipes in `docs/agents.md`. Herdr availability is already queried through `HerdrClient`. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- One source of truth for the rules, consumed by the hook, `shogun guide` and the `AGENTS.md` block.
- A `SessionStart` context that stays small and fits where the agent runs.
- People can replace their own copies of these rules with the guide, with a review step.

**Non-Goals:**
- Teaching `shogun` the origin ref of a worktree; the rule for choosing a pull request's base stays a team policy.
- A Settings section in the desktop app; the commands are the first delivery.
- Detecting duplicated prose reliably; the migration is done by an agent with the person's review.

## Decisions

**A rule catalog in `core`, selected by a context value.** A rule is `id`, `topic`, `text`, `condition` and `inSessionStart`. The context (`WorkflowContext`) holds location (main checkout or worktree), Herdr availability and declared providers. `rulesFor(context)` returns applicable rules, and `inapplicable` the rest with their condition. The hook, `guide`, and the tests use the same function, so a test per rule asserts it appears exactly where its condition holds. Alternative: separate strings per output, rejected because it is the drift this change removes.

**Fixed texts, data kept apart.** Emulator and simulator rules are plain sentences that point to `shogun emulator status` / `shogun simulator status`. The values (`ANDROID_SERIAL`, the UDID) stay in the existing variables line. The existing hint requirements in `command-line-tool` keep holding because the variables line and the rules are printed together in the hook's context. Alternative: templates with placeholders in the catalog, rejected because the catalog would need runtime state and be harder to test.

**`session-start` runs in every Git repository.** The early return changes from "nothing to say" to "not a repository". The hook is installed by the person for use with `shogun`, and the rules are about the workflow in any repository. Cost: a few lines per session. Alternative: only repositories with a ShogunAi project, rejected because `shogun new` works without one (`--worktrees-root`) and those repositories need the same rules.

**Budget by flag, checked by a test.** `inSessionStart` marks the short subset; a test fails if the applicable subset in the richest context exceeds ten lines. Detail stays in `guide`. The first line states the precedence, the last points to `shogun guide`.

**`guide install` is a separate command from `hooks`.** In the specs "hook" means a Claude Code event; a Markdown block is another mechanism with another idempotence story. Targets are explicit: `--agent` (a table of known user-level files), `--project`, or `--file`. Claude is not an `--agent`: it has the hook, and a `--project` block read through `CLAUDE.md` would inject the rules twice, which `status` warns about.

**The block is minimal and versioned.** Markers carry a version, for example `<!-- shogun:guide v1 -->` … `<!-- /shogun:guide -->`; the body says to run `shogun guide` and nothing else, so the real rules always come from the installed binary. Status compares the version and body with the current ones (installed, outdated, missing). Text outside the markers is never modified; writing preserves it byte for byte.

**Migration by agent, not by regex.** `guide --migrate` only prints the rules with ids and an instruction; it neither reads nor writes the file. A duplicate heuristic (`shogun new|open|clean` outside the block) exists only to warn. Alternatives: deleting matching paragraphs automatically (unsafe on a private file), or documentation only (leaves contradictory copies).

**Precedence line in every output.** The guide is a default; the project's `AGENTS.md` and the person win. It lets a person migrate gradually without a conflict breaking anything.

## Risks / Trade-offs

- [Rules injected in repositories where the person doesn't use ShogunAi] → they are few lines and the hook is installed deliberately; `hooks uninstall` removes it.
- [User-level instruction paths of Codex, Cursor and Gemini are unverified] → verify each against its documentation before listing it in `--agent`; `--file` always works; unverified ones are documented as such, like the Codex recipe today.
- [Catalog text goes stale when commands change] → every rule that names a command is covered by a test that parses the command through `CliCommand`, so a renamed option fails the build.
- [The duplicate warning has false positives] → it only warns and says why; it never edits.
- [Two injection paths in Claude Code (hook and `AGENTS.md` block)] → `status` warns; `install` doesn't offer Claude as an agent.

## Migration Plan

1. Ship the catalog, `guide` and the hook; people who already have the rules in their own `AGENTS.md` see them twice until they migrate, which the precedence line makes harmless.
2. People run `shogun guide --migrate --file <their AGENTS.md>` with their agent and review the proposed removals.
3. For a repository's `AGENTS.md`, the same flow goes through a pull request.

Rollback: `shogun guide uninstall` removes the block; reverting the hook change restores the previous output.

## Open Questions

- Which user-level files do Codex, Cursor and Gemini read, and does each follow `AGENTS.md`? Answered while implementing the `--agent` table.
