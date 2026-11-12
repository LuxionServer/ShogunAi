# Tasks

## 1. Regression tests for `trust.json`

- [x] 1.1 In `JsonRepositoryTrustTest`, add a test where two `JsonRepositoryTrust` instances on the same file approve different repositories from concurrent threads and every repository counts as approved; run it and see it fail on the current code.
- [x] 1.2 Add a test where `trust.json` isn't valid JSON, approving a repository keeps `trust.json.unreadable-<timestamp>` with the old contents and the new file holds the approval; run it and see it fail on the current code.

## 2. Shared piece

- [x] 2.1 Add `writeAtomically` (temporary file, owner-only permissions when asked, atomic move, temporary file deleted on failure, trailing newline) with `AtomicWriteTest` covering the private file, the replaced file and a failed move leaving no temporary file.
- [x] 2.2 Make `LocalFileLock` throw `WorktreeError.LocalFileUnreadable` when the lock file can't be created or locked; update `LocalFileLockTest` accordingly.
- [x] 2.3 Add `WorktreeError.LocalFileFromNewerVersion` with English and Spanish texts in the desktop app's error presentation, covered by the existing error text tests.
- [x] 2.4 Add `JsonSchema` and `VersionedJsonFile` (read, update, version, migrations in order, normalize, unknown-field preservation by descriptor, newer-version refusal, unreadable copy, unchanged value not written) with `VersionedJsonFileTest` covering every scenario of the `machine-data-files` spec with a test schema.

## 3. Move the four stores

- [x] 3.1 `JsonRepositoryTrust` on `VersionedJsonFile` (private file, fail closed when unreadable); tests 1.1 and 1.2 pass and the existing trust tests still pass.
- [x] 3.2 `JsonSettingsFile` on `VersionedJsonFile`, keeping `lastReadProblem` and skipping a write refused for a newer version; add tests for a newer-version file and for a field this version doesn't know surviving an update.
- [x] 3.3 `JsonProjectRepository` on `VersionedJsonFile` with `LegacyAgentJson` as the schema's `normalize`; add tests for an unknown project field surviving another project's save; check `projects.json` stays an array without `schemaVersion`.
- [x] 3.4 `JsonMultirepoSource` registry on `VersionedJsonFile` with its per-entry decode, and its declaration write on `writeAtomically`; add a test that an unreadable registry is kept before the next write.

## 4. Verification and docs

- [x] 4.1 `./gradlew test koverVerify` passes with `core` at 100% lines and within its branch maximum, and `cli`/`desktopApp` rules hold.
- [x] 4.2 Update `docs/architecture/infrastructure.md` and `infrastructure.es.md`: the versioned JSON file, its policies and the four stores using it.
- [x] 4.3 `openspec validate versioned-json-store --strict` passes.

## Workflow follow-up

- Archive the change, syncing `machine-data-files` and `repository-trust`, and add to the roadmap: migrating the other machine stores to the shared piece, and surfacing a refused settings write in the app before `settings.json` first bumps its version.
- Open the pull request against `develop`, assigned to CiroFeliu.
