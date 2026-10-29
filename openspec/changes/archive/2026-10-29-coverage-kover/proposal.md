# Proposal

## Why

Nothing measures how much of ShogunAi the tests exercise, so a change can add untested code, or drop the test of an existing path, and still pass CI. The first item of the roadmap's "Reliability on any machine" asks for line and branch coverage measured on every module and enforced in CI, so that a change that lowers it fails the build. Reaching 100% everywhere in one change is disproportionate (about 3,300 uncovered lines and 4,900 uncovered branches today, many of them branches Kotlin generates), so Ciro chose a ratchet: 100% of lines in `core` and `cli` now, and every other figure pinned at its current level and raised by later changes up to 100%.

## What Changes

- Kover measures line and branch coverage of `core`, `cli` and `desktopApp` whenever their tests run, with an HTML and XML report per module.
- Coverage counts production code only: test fixtures and compiler-generated interface bridges are excluded everywhere, the CLI's process entry point is excluded from `cli`, and in the desktop app composable functions and the lambdas Compose generates for them are excluded too, since the end-to-end tests of the desktop app will cover them.
- Each module has a verification rule:
  - `core` and `cli`: 100% of lines, and branches at least at their current percentage.
  - `desktopApp` outside its composables: lines and branches at least at their current percentage.
- New unit tests in `core` and `cli` cover every line that isn't covered today.
- The Tests workflow verifies the rules on its macOS job, where the floors are measured, so a pull request that lowers coverage below a rule fails its check.
- A guideline in `AGENTS.md`: a change that raises a module's coverage raises its floor to the new level, and no change lowers a floor.
- The roadmap keeps the remaining steps: raising branches of `core` and `cli`, and lines and branches of the desktop app, up to 100%.

## Capabilities

### New Capabilities

- `test-coverage`: what coverage is measured on, what is excluded, the threshold each module must meet and how thresholds evolve.

### Modified Capabilities

- `continuous-integration`: the test workflow fails when a module's coverage falls below its threshold.

## Impact

- Build: `gradle/libs.versions.toml` (Kover 0.9.11), `core`, `cli` and `desktopApp` build scripts.
- CI: `.github/workflows/test-suite.yml` runs the coverage verification after the tests.
- Tests: new and extended unit tests under `core/src/test` and `cli/src/test`, and small additions to `core`'s test fixtures. Production code changes only where a line couldn't be reached: dead code and unreachable branches removed, and a few seams with today's behavior as default (see design.md).
- Docs: `AGENTS.md` (coverage guideline and command), `docs/roadmap.md` and `docs/roadmap.es.md`.
- In-flight changes: `host-environment` and `worktree-preflight` add production code to `core` and `cli`, which has to arrive with all its lines tested once this change is in `develop`; see design.md.
