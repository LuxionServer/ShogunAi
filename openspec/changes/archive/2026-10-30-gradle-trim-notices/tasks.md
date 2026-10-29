# Tasks

## 1. Accumulated trim report

- [x] 1.1 Extend `PoolNoticeContent.Trimmed` with the number of trims and the time of the last one, and make `MachineResources` merge each trim into the view's existing trim report (same id) instead of appending one. Verify: `MachineResourcesTest` with three trims leaves one notice with their total, 3 trims and the last time, and dismissing then trimming starts again from one.
- [x] 1.2 Update `poolNoticeBanner` and the English and Spanish texts so the accumulated trim reads as one line with its total, the number of trims and the time of the last one. Verify: `PoolNoticesTest` for one trim, an accumulated trim, and releases with a trim.

## 2. Over the limit explained

- [x] 2.1 Add a pure function over `PoolUsage` that says whether the pool is over the limit with nothing idle to stop, with the busy and unknown counts. Verify: unit tests for idle daemons over the limit, only busy, busy and unknown, and within the limit.
- [x] 2.2 Use it in `PoolUsageSection`: informational chip and the explanation line when nothing can be stopped, the warning chip otherwise. English and Spanish texts.

## 3. Footer

- [x] 3.1 Count Gradle and Kotlin daemons apart in the Gradle daemons view's footer, English and Spanish. Verify: string tests for both languages with and without Kotlin daemons.

## 4. Validation

- [x] 4.1 `./gradlew test` passes.
- [x] 4.2 Check in the real app (`shogun headless start`): the Gradle daemons view with the pool over the limit and busy daemons shows the explanation, one trim banner and the split footer.
- [x] 4.3 Update the "Pools" paragraph of `docs/architecture/ui.md` and `ui.es.md`.
