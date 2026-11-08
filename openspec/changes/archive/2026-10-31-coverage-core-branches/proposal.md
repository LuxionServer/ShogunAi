# Proposal

## Why

`core` holds the domain and the adapters every other module relies on, and its lines are fully tested, but only 79% of its branches are: about 1,500 decisions (a `null` check, an `?:` fallback, an `if` on a command's exit code, a missing field in a JSON file) can change behavior without any test noticing. The first item of the roadmap's "Reliability on any machine" asks for every coverage floor CI enforces to reach 100%, and the branches of `core` are the first of those steps.

## What Changes

- New and extended unit tests in `core` cover every branch Kover counts in its production code, including the branches kotlinx.serialization generates for `@Serializable` classes (missing, present and default fields).
- Branches no input can reach are removed from the production code instead of being tested around, without changing behavior; each one is listed with its reason in design.md.
- `core`'s branch rule in `core/build.gradle.kts` rises with the coverage and ends as a maximum of missed branches: the eighteen deserializer checks kotlinx.serialization generates for classes whose fields are all optional, which no input can reach (see design.md).
- No new coverage exclusions.
- The roadmap drops `core`'s branches from its coverage item.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `test-coverage`: `core`'s threshold becomes 100% of lines and every reachable branch, instead of a branch floor.

## Impact

- Tests: `core/src/test` (new and extended test classes), and small additions to `core`'s test fixtures where a fake needs to answer something new.
- Build: `core/build.gradle.kts` (branch rule).
- Production code in `core`: only removals of unreachable branches, with today's behavior kept (see design.md).
- Docs: `docs/roadmap.md` and `docs/roadmap.es.md`.
- In-flight changes: anything that adds code to `core` after this change is in `develop` has to arrive with all its branches tested, or `koverVerify` fails.
