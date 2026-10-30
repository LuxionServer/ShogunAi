# Proposal

## Why

The roadmap's "Reliability on any machine" asks for the desktop app's coverage outside its composables to reach 100%, and today it is at 83% of lines and 66% of branches (1,853 lines and 2,367 branches uncovered across 120 files). That is too much for one pull request, so it goes up in consecutive changes, one per package. This first one takes the app's texts (`ui/i18n`): the largest single block (509 uncovered lines and 475 uncovered branches), self-contained, and where an untested text is a real defect, since a translation that is blank or drops the name it was given reaches the user as is.

## What Changes

- A test goes through every text of the `Strings` interface in English and in Spanish, calling each function with sample arguments that reach all its branches (every value of an enum, both booleans, zero, one and several for counts, `null` for optional values), and checks that no text is blank and that every name or path it is given appears in it. Texts that take a domain object get explicit tests.
- Explicit tests for every error description (`EnglishErrors`, `SpanishErrors`) and for the installation texts and `ErrorDescription` that the existing tests don't reach.
- `kotlin-reflect` is added to the desktop app's test classpath to list the interface's members and their parameter types.
- The desktop floors in `desktopApp/build.gradle.kts` go up to the figures measured after this change.
- The plan for the remaining packages is written down (design.md) and the roadmap item names what is left after this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-localization`: every text the app produces is non-empty in every language and shows the values it is given; a check enforces it for every text.

## Impact

- Tests: new tests under `desktopApp/src/test/kotlin/app/luxion/shogunai/ui/i18n/`.
- Build: `gradle/libs.versions.toml` (`kotlin-reflect`, test only) and `desktopApp/build.gradle.kts` (test dependency and floors).
- Production code: only if a test finds a defective text (a blank translation or a dropped argument) or unreachable code, both noted in design.md.
- Docs: `docs/roadmap.md` and `docs/roadmap.es.md`.
- Parallel work: `desktop-host-environment` may touch the installation texts; a conflict there is small and resolved when bringing in `develop`. Coverage floors may conflict with the other coverage changes of roadmap point 1 (`core` and `cli` only touch their own modules).
