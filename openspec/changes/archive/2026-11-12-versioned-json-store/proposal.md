# Proposal

## Why

The desktop app and the `shogun` CLI are separate processes, often of different versions (the app keeps running after `brew upgrade` or `scoop update` while the CLI is already new, the Windows zip is installed apart from the `.msi` or winget, and users downgrade), and they read and write the same files in `~/.shogunai`. Each store solves persistence its own way: no file carries a schema version, a rewrite drops the fields a newer version wrote, `trust.json` has no lock between processes and silently replaces an unreadable file, and the shared lock runs unlocked when it can't take its lock file.

## What Changes

- A shared infrastructure piece for the machine's JSON files: read, atomic write (temporary file and atomic move), lock between processes, schema version, ordered migrations and a single policy for unreadable files.
- Object files carry `"schemaVersion": N` at their root, written on every write. A file without it is version 1, as every file written so far. `projects.json` stays a JSON array with an implicit version 1, so binaries already released keep reading it.
- A rewrite keeps the fields this version doesn't know, so an additive change made by a newer version survives an older one. An incompatible change raises the schema version: an older binary still reads what it understands, but refuses to write the file and says it was written by a newer version.
- An unreadable file is never overwritten without first being kept next to it as `<file>.unreadable-<timestamp>`, unless it was written by a newer version, which is left untouched.
- The lock between processes fails instead of running the block unlocked when it can't create or take its lock file.
- `trust.json`, `projects.json`, `settings.json` and `multirepos.json` move to the shared piece. This fixes two bugs in `trust.json`: the app and the CLI approving at once lose one of the approvals, and an unreadable file is replaced silently on the next approval.
- The other machine stores (`compose-projects.json`, `orca.json`, `derived-data-sizes.json`, environment state, device queue, desktop turn, agent sessions, provisioning records, physical devices) stay as they are and are listed in the roadmap.

## Capabilities

### New Capabilities

- `machine-data-files`: how ShogunAi keeps its own files in `~/.shogunai` (schema version, compatibility between versions, concurrent writers, unreadable files).

### Modified Capabilities

- `repository-trust`: approvals made at once by the app and the CLI are all kept, and an unreadable `trust.json` approves nothing and is kept before the next approval replaces it.

## Impact

- `core/src/main/kotlin/app/luxion/shogunai/infrastructure`: new versioned JSON file and atomic write; `LocalFileLock`, `JsonRepositoryTrust`, `JsonProjectRepository`, `JsonSettingsFile`, `JsonMultirepoSource` and `LegacyAgentJson` move to them. Constructors keep their signatures.
- `WorktreeError` gains `LocalFileFromNewerVersion`, with English and Spanish texts in the desktop app.
- Docs: `docs/architecture/infrastructure.md` and its Spanish translation; the roadmap at archive time.
- No change to use cases, ports or composition roots.
