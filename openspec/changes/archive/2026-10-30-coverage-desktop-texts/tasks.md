# Tasks

## 1. Build

- [x] 1.1 Add `kotlin-reflect` to `gradle/libs.versions.toml` with the Kotlin version and as a `testImplementation` of `desktopApp`

## 2. Every text in both languages

- [x] 2.1 Add the reflective test over every member of `Strings` in English and Spanish: samples per parameter type, no blank text, every string argument shown, `environmentSummary(0, 0)` as the only empty text, and a failure naming any member whose parameters can't be sampled
- [x] 2.2 Cover the texts with domain-object parameters (Claude Code hooks, command-line tool status and installation, installation checks and report, resource limits) by building data classes and sealed types from their constructors in the samples, with an explicit test for the installation overview

## 3. Errors and installation texts

- [x] 3.1 Add tests for every `WorktreeError` and `GitFailureCause` description not yet covered, in both languages, including their optional parts
- [x] 3.2 Cover the remaining branches of `ErrorDescription`, the installation texts and `Strings.kt`
- [x] 3.3 Fix any blank translation or dropped argument the tests find, and remove code no input can reach, recording it in design.md

## 4. Floors and docs

- [x] 4.1 Check `ui/i18n` is at 100% of lines and branches with `./gradlew :desktopApp:koverXmlReport`, and raise the desktop floors in `desktopApp/build.gradle.kts` to the measured whole percentages, recorded in design.md
- [x] 4.2 Update the roadmap item in `docs/roadmap.md` and `docs/roadmap.es.md` with the desktop figures after this change and the packages left
- [x] 4.3 Run `./gradlew test koverVerify`

## Workflow follow-up

- Archive the change, bring in `origin/develop`, re-measure the floors and open the pull request against `develop`.
