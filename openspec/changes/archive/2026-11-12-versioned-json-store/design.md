# Design

## Context

See proposal.md for the problem. Facts that shape the approach:

- The CLI is the desktop app's own launcher (`docs/architecture/cli.md`, "How it's shipped"), so on macOS and Linux one install gives one version of both. Versions still mix: the app keeps running after `brew upgrade` or `scoop update` while new CLI runs start from the upgraded binary; on Windows the `shogun` zip is installed apart from the `.msi`/winget app; users downgrade; and `./gradlew :cli:run` dev builds use the same `~/.shogunai`.
- Every store decodes with `ignoreUnknownKeys = true` and rewrites the whole file, so an older binary drops what a newer one added. Binaries already released will keep doing so; this change protects files from this release on.
- `projects.json` is a JSON array at its root. Released binaries fail to decode anything else.
- `JsonRepositoryTrust` relies on `@Synchronized`, which only serializes callers of one instance: the app and the CLI each have their own, and so do two instances in one JVM.
- The four constructors are wired by `ShogunCli.kt`, `cli/Main.kt` and `AppContainer.kt`, which the parallel `shared-composition` change edits.

## Goals / Non-Goals

**Goals:**

- One internal piece, `VersionedJsonFile<T>`, owning read, atomic write, lock, schema version, migrations, unknown-field preservation and the unreadable-file policy for the four riskiest files.
- One atomic write function used by those stores and by the multirepo declaration write.
- Fix the two `trust.json` bugs with regression tests written first and seen failing.

**Non-Goals:**

- Migrating the other stores (listed in the roadmap).
- Changing ports, use cases or composition roots: constructors keep `(Path)`.
- Bumping any file's version: all four stay at version 1; the migration machinery is exercised by tests with a test schema.
- Surfacing a refused settings write in the desktop app (see Risks).

## Decisions

### Version as a root field, absent meaning 1

Object files get `"schemaVersion": N` at their root, written on every write; absent means 1, so every existing file is version 1 as it is. Released binaries ignore the field (`ignoreUnknownKeys`).

`projects.json` keeps its array root with an implicit version 1. Wrapping it (`{"schemaVersion": 2, "projects": [...]}`) would make released binaries report it unreadable and move it aside on their next save. A future incompatible change to projects decides then whether to wrap it. Its read normalization (`LegacyAgentJson`, adding `useHeadroom: true` to agents saved before the option existed) isn't a version step, since an array can't record having had it; it stays an idempotent transform run on every read, passed to the file as `normalize`.

Alternatives: wrapping every file (breaks released binaries), a separate `schema.json` (version apart from the data, can go out of sync).

### Schema: version derived from the migration list

```kotlin
internal class JsonSchema(
    val migrations: List<(JsonObject) -> JsonObject> = emptyList(),
    val normalize: (JsonElement) -> JsonElement = { it },
) { val version: Int get() = migrations.size + 1 }
```

`migrations[i]` upgrades a version `i + 1` object to `i + 2`. Reading a version `v` file applies `migrations[v - 1 until version - 1]` in order, then `normalize`. A version can't be declared without its migration, so the list can't skip one. `schemaVersion` must be a positive integer; anything else makes the file unreadable.

### Newer versions: keep unknown fields, refuse incompatible ones

The two policies combine (decided with the user):

- Additive changes (a new optional field, a new map entry) don't bump the version. Any binary with this change keeps fields it doesn't know when rewriting.
- An incompatible change bumps the version. An older binary reads what it can decode leniently but refuses every write with `WorktreeError.LocalFileFromNewerVersion(path, version, supportedVersion)`, before touching the file. If decoding fails, the read reports `LocalFileUnreadable` naming the newer version as the reason, and nothing moves the file aside.

A new enum value is an incompatible change for older binaries (decoding fails), so it needs a version bump; the design of such a change says so.

### Preserving unknown fields guided by the serializer's descriptor

On write, the new value is encoded to a `JsonElement` and merged with the file's (migrated) element, walking the serializer's `SerialDescriptor`:

- Class descriptors: written keys recurse into their element descriptor; original keys that aren't element names of the class are added back. Known keys the new value omits (defaults not encoded, nulls) stay omitted.
- Map descriptors: entries recurse by key into the value descriptor; entries the new value removed stay removed (a revoked approval doesn't come back).
- List descriptors: items that are objects with the same primitive `id` recurse into each other (projects); other items are written as they are, since matching by position would graft fields onto a neighbour after a removal.
- Anything else (primitives, enums, polymorphic, mismatched shapes) is written as it is.

The store-owned `schemaVersion` is removed from the original before the merge and written first in the result.

Alternative: keep the raw `JsonObject` in every model as an extra field. Rejected: it touches every `@Serializable` class and the domain models.

### The file's operations

```kotlin
internal class VersionedJsonFile<T>(
    path: Path, serializer: KSerializer<T>, json: Json, empty: T,
    schema: JsonSchema = JsonSchema(), privateFile: Boolean = false,
    decode: (JsonElement) -> T = { json.decodeFromJsonElement(serializer, it) },
)
fun read(): T
fun update(transform: (T) -> T): WorktreeError.LocalFileUnreadable?
```

- `read()` runs under `LocalFileLock`. Missing or blank file: `empty`. IO, JSON, version or decode failure: throws `LocalFileUnreadable`.
- `update()` runs read-modify-write under one lock hold. Newer version: throws `LocalFileFromNewerVersion` without writing. Unreadable at the current version or older: keeps the copy (`keepUnreadableCopy`), transforms `empty`, writes, and returns the problem so the caller can report it. When the transformed value equals the current one and nothing was unreadable, nothing is written (as `JsonMultirepoSource` did).
- `decode` lets `JsonMultirepoSource` keep decoding `multirepos` and each `local` entry on its own, so a malformed entry doesn't hide the rest; `privateFile` gives `rw-------` to `trust.json` and `multirepos.json`.

How each adapter shows problems stays where its port allows:

| File | Unreadable on read | Unreadable before a write | Newer version on write |
|---|---|---|---|
| `settings.json` | defaults, `lastReadProblem` | copy kept, `lastReadProblem` | write skipped |
| `projects.json` | throws `LocalFileUnreadable` | copy kept | throws |
| `trust.json` | nothing approved | copy kept | throws |
| `multirepos.json` | empty registry | copy kept | throws |

`trust.json` reading as "nothing approved" fails closed: the user is asked again, and approving keeps the old file. `RepositoryTrust.isApproved` returns a `Boolean` and is called from use cases this change doesn't touch.

### Atomic write

`writeAtomically(target, text, privateFile)` creates the parent folders, writes a temporary file next to the target (owner-only permissions first when `privateFile`, where POSIX permissions exist), and moves it over the target with `ATOMIC_MOVE` and `REPLACE_EXISTING`. If writing or moving fails, the temporary file is deleted and the error propagates. Every file ends with a newline.

### Strict lock

`LocalFileLock.withProcessLock` throws `WorktreeError.LocalFileUnreadable(target, "can't lock <target>.lock: <reason>")` when it can't create the lock file or take the lock, instead of running the block unlocked. This also applies to `JsonComposeProjectRegistry` and `JsonOrcaWorkspaceStore`, which already use it: a lock that silently doesn't lock is the same bug for them. On local file systems on macOS, Linux and Windows, `FileChannel.lock` is supported, so the error only shows where the folder can't be written or locks are unsupported, both real problems to report.

### New error

`WorktreeError.LocalFileFromNewerVersion(path, version, supportedVersion)`: "<path> was written by a newer version of ShogunAi (schema <version>; this version supports up to <supported>); update ShogunAi to change it". English and Spanish texts in the desktop app's error presentation.

### Coverage

No new `@Serializable` class: the piece works on `JsonElement`, so `core`'s branch maximum (18) stays. Every new line and branch has a unit test; migrations are covered with a test-only schema.

## Risks / Trade-offs

- [Released binaries still drop unknown fields and ignore `schemaVersion`] → Unavoidable; protection starts with this release. Their rewrite drops `schemaVersion`, which reads back as version 1, the version all four files are at.
- [A refused settings write in the app is invisible] → No settings version bump exists, so it can't happen yet. The first change that bumps `settings.json`'s version must surface it in the app; listed in the roadmap.
- [Unknown fields inside list items without `id` are lost] → Lists of such items in these files (recent worktrees, agents) are rewritten whole by their owner; documented here and in the architecture page.
- [Strict lock surfaces errors where the old code ran unlocked] → Those runs could lose writes; the error names the file and the reason.
- [Conflict with `shared-composition`] → No constructor signature changes.

## Migration Plan

No data migration: existing files are version 1 and are read as they are; the first write adds `schemaVersion`. Rolling back to a released version works: it ignores `schemaVersion` and drops it on its next write.
