# Proposal

## Why

`shogun status` prints one line per worktree with space-separated details (`changes=2`, `ahead=1 behind=0`, `merged`). That's fine to read, but scripts, agent hooks and integrations such as the Herdr plugin have to parse it with regular expressions that break as soon as a detail is added or reordered. The roadmap's "Command-line output for scripts" item asks for a machine-readable output, starting with `status`.

## What Changes

- `shogun status --json` prints the same information as `shogun status` as a single JSON document on standard output: the repository, its main branch and one object per worktree (path, branch, HEAD, main, locked, folder missing, origin and, when it could be read, its status: uncommitted changes, divergence from its upstream and from the main branch, merged, last commit time).
- The JSON fields are a stable contract: later versions may add fields, but never rename or remove them or change their type.
- Errors keep going to standard error as text with the same exit codes, so a script checks the exit code before parsing.
- `shogun status` without `--json` is unchanged, and any other option is still a usage error.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `command-line-tool`: "Show worktree status from the command line" gains the `--json` output.

## Impact

- CLI: `CliCommand.Status` gains `json: Boolean`; `parseArguments` parses `status [--json]`; `ShogunCli.status` renders either the text lines or the JSON document. The help text lists `--json`.
- No `core` change: the JSON is built from the `Worktree`, `WorktreeStatus` and `WorktreeOrigin` values the text output already uses.
- Docs: `docs/guide/cli.md` documents `--json` and its fields; `docs/architecture/cli.md` notes how it's built; both with their `.es.md` versions. `docs/roadmap.md` (and `.es.md`) records that `status` already has `--json`.
