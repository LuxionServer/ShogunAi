# Tasks

## 1. Small files

- [x] 1.1 Cover the missed branches of `CliHelp.kt`, `DeviceWaitText.kt`, `GuideText.kt`, `HeadlessAppText.kt` and `DesktopTurnText.kt`; verify none of them has a missed branch in `./gradlew :cli:koverXmlReport`.
- [x] 1.2 Cover the missed branches of `StatusJson.kt`, `MultirepoJson.kt` and `CommandJson.kt`; verify none has a missed branch in the report and raise the `cli branches` floor to the measured whole percentage.

## 2. Command parsing

- [x] 2.1 Cover the missed branches of `CliCommand.kt` with argument parsing tests; verify it has no missed branch and raise the floor.

## 3. Commands

- [x] 3.1 Cover the missed branches of `ShogunCli.kt`, command family by command family, removing unreachable branches and listing them in design.md; raise the floor after each family.
- [x] 3.2 Set the `cli branches` rule to 100; verify `./gradlew :cli:koverVerify` passes and that the report shows 0 missed branches in `cli`.

## 4. Validation

- [x] 4.1 Full validation: `./gradlew test koverVerify` green locally.

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md`: the coverage item no longer lists `cli`'s branches.
- Implementation commit, bring in `origin/develop` (covering any `cli` branch a parallel change added), re-run `./gradlew test koverVerify`, open the pull request against `develop` assigned to CiroFeliu.
