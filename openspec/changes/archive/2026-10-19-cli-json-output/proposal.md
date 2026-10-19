# Proposal

## Why

ShogunAi is meant to be the worktree layer agents and their scripts build on, but only `shogun status --json` and `shogun env --format json` have output a script can parse safely. Everything else prints English text whose wording changes between versions, so an agent that wants to know which files `provision --check` found stale, which worktrees `clean` would remove or which emulator serial a worktree has must scrape prose. This is the roadmap's "Command-line output for scripts".

## What Changes

- `--json` on every command that reports a result: `new`, `provision` (and `--check`), `trust`, `clean`, `gradle`, `gradle stop`, `xcode`, `xcode clean`, `emulator start|stop|status`, `simulator start|stop|status`, and `remove` and `repair` from `cli-desktop-parity`.
- Every JSON output follows the rules `status --json` already has: one compact object on a single line on standard output, errors as text on standard error with nothing on standard output, the same exit codes as the text output, and fields that later versions only add to.
- With `--json` a command never asks a question: post-creation commands that aren't approved are skipped and reported, and `trust` only reports unless `--yes` is given.
- `docs/guide/cli.md` documents each object's fields.

## Capabilities

### New Capabilities

### Modified Capabilities
- `command-line-tool`: machine-readable output for the remaining commands, and its general rules.

## Impact

- `cli`: a `--json` flag on the listed `CliCommand`s, one JSON builder per command next to `StatusJson.kt`, the usage text; no change in `core`.
- Docs: `docs/guide/cli.md` and `cli.es.md`.
- Implement after `cli-desktop-parity`, which adds `remove` and `repair`; hooks keep their fixed protocols and get no `--json`.
