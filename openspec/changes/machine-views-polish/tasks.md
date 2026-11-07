# Tasks

## 1. Shared parts

- [ ] 1.1 `OwnerCell` draws a dimmed "unknown" with the `owner-unknown` tag for a `null` owner; Gradle rows use it instead of `DaemonOwnerCell`; the Gradle UI test looks for the new tag and passes
- [ ] 1.2 Add `ListActionBlock`, the `ListActionReason` composable and `noneIdleReason` extracted from `stopIdleReason`; "Stop idle daemons" of the Gradle view uses them and its UI tests still pass

## 2. Maven daemons view

- [ ] 2.1 `mavenStopBlock` and `mavenStopIdleBlock` with unit tests for every branch; the row Stop is a `GhostButton` inside `DisabledReason`, and "Stop idle daemons" uses `ListActionReason`; a UI test checks the written reason, the ghost Stop's tooltip state and the dimmed unknown owner

## 3. Device views

- [ ] 3.1 Emulator (worktree and unowned) and simulator rows use `GhostButton` for Start and Stop; existing tests pass
- [ ] 3.2 `simulatorOrphansDeleteBlock` with unit tests; the simulators' "Delete orphans" uses `ListActionReason`

## 4. DerivedData and Compose views

- [ ] 4.1 `derivedDataDeleteBlock` with unit tests; "Delete orphans" uses `ListActionReason`; a UI test checks "No folder is orphaned" is written when there are none
- [ ] 4.2 `composeTakeDownBlock` and `composeLeftoversBlock` with unit tests; the Compose view's list actions use `ListActionReason`

## 5. Strings and verification

- [ ] 5.1 New strings in `Strings`, `EnglishStrings` and `SpanishStrings`; the strings tests pass
- [ ] 5.2 `./gradlew test koverVerify` passes, raising a floor if a figure crosses a whole percentage
- [ ] 5.3 Check the views in the headless app (`shogun headless start`) with sample data and capture them

## Workflow follow-up

- Archive with `/opsx:archive`, removing the second bullet of roadmap item 2 in `docs/roadmap.md` and `docs/roadmap.es.md`
- Merge `origin/develop`, re-run `./gradlew test koverVerify`, and open the pull request against `develop` assigned to CiroFeliu
