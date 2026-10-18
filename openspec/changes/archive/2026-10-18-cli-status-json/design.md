# Design

## Context

`ShogunCli.status()` lists the worktrees (`ListWorktreesUseCase`), resolves the main branch (`ResolveMainBranchUseCase`), reads each worktree's status (`ReadWorktreeStatusUseCase`, skipped for prunable worktrees) and classifies its origin (`classifyOrigin`, `null` for the main worktree), then formats each worktree with `statusLine`. The `cli` module already depends on `kotlinx-serialization-json` and uses `Json.parseToJsonElement` for hook input, but doesn't apply the serialization compiler plugin.

## Goals / Non-Goals

**Goals:**
- A JSON output for `status` that scripts can rely on across versions.
- The same data as the text output, plus the fields the text output leaves out because they'd crowd a line (HEAD, divergence from the main branch, last commit time).

**Non-Goals:**
- `--json` for other commands (`gradle`, `xcode`, `emulator status`, …): each gets it when a script needs it, following this design.
- JSON errors: errors stay as text on standard error.
- Agents, ports or emulator state per worktree: the text output doesn't show them either.

## Decisions

### The document is built with `buildJsonObject`
The output is built with `kotlinx.serialization.json`'s `buildJsonObject`/`buildJsonArray` and printed with `Json.encodeToString(JsonElement.serializer(), element)` in a new `cli/src/main/kotlin/app/luxion/shogunai/cli/StatusJson.kt` (a top-level function `statusJson(...)` returning a `JsonObject`). This avoids adding the serialization compiler plugin to the module and `@Serializable` DTOs whose only purpose is this output; the field names live in one function that is the contract.

*Alternative considered:* `@Serializable` data classes. Rejected for now: it needs the plugin in `cli/build.gradle.kts` and a parallel set of DTOs, for a single command.

### Shape of the document
A single object, compact (one line) and followed by a newline:

```json
{
  "repository": "/abs/path/main-repo",
  "mainBranch": "main",
  "worktrees": [
    {
      "path": "/abs/path/main-repo",
      "branch": "main",
      "head": "4f1c2e9…",
      "main": true,
      "locked": false,
      "folderMissing": false,
      "origin": null,
      "status": {
        "uncommittedChanges": 0,
        "upstream": { "ahead": 0, "behind": 0 },
        "mainBranch": null,
        "merged": false,
        "lastCommitAt": "2026-10-18T09:12:44Z"
      }
    }
  ]
}
```

- `repository`: `config.baseRepositoryPath`.
- `mainBranch`: the resolved main branch's name, or `null` when it can't be resolved.
- `branch`: `null` for a detached HEAD (the text output prints `(detached)`). `head`: `Worktree.head`, or `null`.
- `origin`: `"shogunai"`, `"claude-code"`, `"herdr"`, `"other"` (the same labels as the text output), or `null` for the main worktree.
- `status`: `null` when the folder is missing or the status couldn't be read; scripts tell them apart with `folderMissing`.
- `upstream` and `status.mainBranch`: `{ "ahead": n, "behind": n }`, or `null` when there's no upstream or no main branch.
- `lastCommitAt`: ISO-8601 UTC (`Instant.toString()`), or `null`.

An object at the top level (instead of a bare array) leaves room for repository-level fields later without breaking scripts. Every field is always present, with `null` when it doesn't apply, so `jq` paths never fail on a missing key.

### Parsing
`"status" -> parseStatus(rest)`: accepts only `--json`, rejects any other option or positional argument with a usage error (`unknown option '--x' for 'status'`, `'status' takes no arguments, got 'x'`), exit code 2 as today. `CliCommand.Status` becomes `data class Status(val json: Boolean = false)`.

### Stability
The guide states the contract: fields are only added; renaming, removing or changing a field's type is a breaking change that needs a new flag. The test asserts the full document of the fixture repository, so an accidental rename fails it.

## Risks / Trade-offs

- [Reading the status of many worktrees is slow, so scripts that poll may be slow] → Same cost as the text output; not addressed here.
- [Paths with non-ASCII characters] → `kotlinx.serialization` escapes them correctly; scripts get the exact path.
