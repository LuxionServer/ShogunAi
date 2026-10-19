# Design

## Context

See proposal.md for motivation. `StatusJson.kt` builds `status --json` with `kotlinx.serialization.json` builders, and the spec already fixes its contract: one compact JSON object on standard output, errors as text on standard error, additive fields. `env --format json` prints the variables as an object. Each command's result already exists as a typed value inside `ShogunCli` before it's printed as text (use case results, `CleanupSelection`, provider states).

## Goals / Non-Goals

**Goals:**
- One rule set for every JSON output, enforced by a shared helper, so commands don't drift.
- JSON built from the same values the text output prints, never by parsing that text.

**Non-Goals:**
- JSON for hooks (`hook *`), `help` and `--version`.
- Streaming or progress events; each command prints its object once, at the end.
- A JSON envelope for errors; the established `status --json` contract (text on standard error, nothing on standard output) is kept.

## Decisions

**Shared output helper.** `JsonOutput.print(out, JsonObject)` prints `Json.encodeToString` without pretty printing, followed by a newline. Each command has a `<command>Json(...)` builder in a `CommandJson.kt` file, alongside the existing `statusJson`. Text and JSON branches share the computation and only differ in the final printing step.

**Field conventions.** camelCase names; paths absolute; absent values as `null`, never omitted, as in `status --json`; lists always present, possibly empty; enumerations as lowercase strings (`running`, `stopped`, `booting`). Objects per command:

- `new`: `{ path, branch, created: true, skippedPostCreateCommands: [[argv]], postCreateFailure: null | { command: [argv], exitCode, stderr } }`. On a post-creation failure the object is still printed (the worktree exists) and the exit code stays 1, mirroring how the text output prints the path.
- `provision`: `{ path, copied: [path], linked: [path], copiedInsteadOfLinking: [path], skippedPostCreateCommands, postCreateFailure }`; `provision --check`: `{ path, drift: [{ path, state: "missing" | "differs" | "not-linked" }] }` with exit code 3 when `drift` isn't empty.
- `trust`: `{ repository, commands: [[argv]], approved, changed }`.
- `clean`: `{ dryRun, candidates: [{ path, branch, reason: "merged" | "folder-missing" }], keptForAgents: [{ path, agents: [name] }], skipped: [{ path, reason }], removed: [path], deletedBranches: [name], failures: [{ path, message }], providers: { gradleDaemonsStopped, emulatorsStopped, simulatorsDeleted, derivedDataDeleted } }`.
- `gradle`: `{ daemons: [{ pid, kind, version, memoryBytes, state, worktree }], totalMemoryBytes }`; `gradle stop`: `{ stopped: [pid], keptBusy: [pid], failures: [{ pid, message }] }`.
- `xcode`: `{ folders: [{ path, sizeBytes, worktree }], orphans: [...], compilationCache }`; `xcode clean`: `{ deleted: [path], freedBytes, failures }`.
- `emulator ...`: `{ worktree, serial, avd, state }`; `simulator ...`: `{ worktree, udid, device, runtime, state }`.
- `remove`: `{ path, branchDeleted: null | name, providers }`; `repair`: `{ repaired: [path], failures }`.

The exact field lists are fixed by the docs page written in the same task as each builder, and tests assert every documented field is present.

**No questions in JSON mode.** `--json` selects a non-interactive approval (`PostCreateApproval.SKIP` for `new`/`provision`, as hooks do) and makes `trust` without `--yes` report only. `clean` without `--yes` is already a dry run.

**Parsing.** `--json` is accepted anywhere after the command, as other flags are; on a command without JSON output it's an unknown option (exit code 2), as `status --yaml` is today.

## Risks / Trade-offs

- [A field named now turns out wrong later] → Additive-only contract: a better field is added next to it, never renamed.
- [Large outputs on one line, such as `xcode` with many folders] → Acceptable for scripts; `jq` reformats it.
- [`remove`/`repair` don't exist yet when this is implemented] → Implemented after `cli-desktop-parity`; if that order changes, their two tasks move to that change.
