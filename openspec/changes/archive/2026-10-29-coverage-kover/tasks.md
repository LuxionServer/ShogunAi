# Tasks

## 1. Measurement

- [x] 1.1 Add Kover 0.9.11 to the version catalog and apply it to `core`, `cli` and `desktopApp`; verify `./gradlew koverHtmlReport` produces a report per module.
- [x] 1.2 Exclude the `testFixtures` source set from every module and, in `desktopApp`, `@Composable`/`@Preview` functions and `ComposableSingletons` classes; verify no fake from the fixtures and no composable appears in the XML reports.

## 2. Tests for 100% of lines in core and cli

- [x] 2.1 List the uncovered lines of `core` from its XML report and cover the domain (`domain/**`) with unit tests; verify the domain's line coverage is 100% in the report.
- [x] 2.2 Cover the uncovered lines of `core`'s infrastructure (`infrastructure/**`) with unit tests, injecting `osName` where a line depends on the operating system; verify `core`'s line coverage is 100%.
- [x] 2.3 Cover the uncovered lines of `cli` with unit tests; verify `cli`'s line coverage is 100%.

## 3. Verification rules

- [x] 3.1 Add the verification rules: lines at 100 in `core` and `cli`; branches of `core` and `cli`, and lines and branches of `desktopApp`, at the floored figures measured after group 2; verify `./gradlew koverVerify` passes, and fails after temporarily raising one bound by one point.
- [x] 3.2 Add a "Verify coverage" step running `./gradlew koverVerify` to the macOS job of `test-suite.yml`; verify locally with the same command, whose numbers are the macOS job's.

## 4. Guidelines and docs

- [x] 4.1 Add the coverage rule (floors only go up, raised in the change that raises coverage) and the `koverVerify`/`koverHtmlReport` commands to `AGENTS.md`; verify by reading the file.
- [x] 4.2 Full validation: `./gradlew test koverVerify` green locally and the reports checked by hand for the three modules.

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md`: the coverage item keeps only what is left (branches of `core` and `cli` and the desktop app up to 100%).
- Implementation commit, bring in `origin/develop`, re-run `./gradlew test koverVerify`, open the pull request against `develop` assigned to CiroFeliu, and check that the macOS job of its Tests run passes the coverage verification.
