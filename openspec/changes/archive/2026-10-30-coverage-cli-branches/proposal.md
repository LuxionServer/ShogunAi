# Proposal

## Why

The first item of the roadmap's "Reliability on any machine" raises the coverage floors that CI enforces up to 100%. `cli`'s lines are already at 100%, but its branches are at 83% (519 of 3,087 missed), so a change can add an untested `if` or `?:` to the command-line tool, or drop the test of one side of it, and still pass CI. Every command, agent and hook goes through `cli`, so its untested branches are paths users hit with no test holding them.

## What Changes

- New unit tests in `cli` cover every missed branch, so `cli`'s branch coverage reaches 100%.
- Where a branch can't be reached from any input, the production code is simplified to remove it instead of testing around it; each case is recorded in design.md.
- `cli`'s branch floor in `cli/build.gradle.kts` goes from 83 to 100, raised step by step as coverage goes up.
- Nothing is excluded from coverage beyond what is already excluded.
- The roadmap's coverage item no longer lists `cli`'s branches.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `test-coverage`: `cli` is held to 100% of its branches, not to a floor.

## Impact

- Tests: new and extended unit tests under `cli/src/test`.
- Production code in `cli` only where a branch is unreachable (see design.md); no change in behavior.
- Build: `cli/build.gradle.kts` (branch rule).
- Docs: `docs/roadmap.md` and `docs/roadmap.es.md`.
- Parallel changes touching `cli`: `herdr-e2e-windows` (end-to-end tests) and `doctor-provider-checks` (new `shogun doctor` checks); `coverage-core-branches` edits the same requirement of `test-coverage`. See design.md.
