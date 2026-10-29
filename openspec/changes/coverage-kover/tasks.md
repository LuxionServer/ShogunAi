# Tasks

## 1. Measurement

- [ ] 1.1 Add Kover 0.9.11 to the version catalog and apply it to `core`, `cli` and `desktopApp`; verify `./gradlew koverHtmlReport` produces a report per module.
- [ ] 1.2 Exclude the `testFixtures` source set from every module and, in `desktopApp`, `@Composable`/`@Preview` functions and `ComposableSingletons` classes; verify no fake from the fixtures and no composable appears in the XML reports.

## 2. Tests for 100% of lines in core and cli

- [ ] 2.1 List the uncovered lines of `core` from its XML report and cover the domain (`domain/**`) with unit tests; verify the domain's line coverage is 100% in the report.
- [ ] 2.2 Cover the uncovered lines of `core`'s infrastructure (`infrastructure/**`) with unit tests, injecting `osName` where a line depends on the operating system; verify `core`'s line coverage is 100%.
- [ ] 2.3 Cover the uncovered lines of `cli` with unit tests; verify `cli`'s line coverage is 100%.

## 3. Verification rules

- [ ] 3.1 Add the verification rules: lines at 100 in `core` and `cli`; branches of `core` and `cli`, and lines and branches of `desktopApp`, at the floored figures measured after group 2; verify `./gradlew koverVerify` passes, and fails after temporarily raising one bound by one point.
- [ ] 3.2 Run `./gradlew test koverVerify` in `test-suite.yml`; verify on the pull request's CI run that the three systems pass, and lower a branch floor to the lowest figure among them if one differs.

## 4. Guidelines and docs

- [ ] 4.1 Add the coverage rule (floors only go up, raised in the change that raises coverage) and the `koverVerify`/`koverHtmlReport` commands to `AGENTS.md`; verify by reading the file.
- [ ] 4.2 Full validation: `./gradlew test koverVerify` green locally and the reports checked by hand for the three modules.

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md`: the coverage item keeps only what is left (branches of `core` and `cli` and the desktop app up to 100%).
- Implementation commit, bring in `origin/develop`, re-run `./gradlew test koverVerify`, open the pull request against `develop` assigned to CiroFeliu.
