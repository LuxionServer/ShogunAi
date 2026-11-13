# Tasks

## 1. Sessions in the machine's daemon listing

- [ ] 1.1 Change `MachineUseCases.listDaemons` to receive the worktree paths, and wire it in `AppContainer` through `ListGradleDaemonsUseCase` with `ports.agentSessions`, returning the processes
- [ ] 1.2 Pass the paths of every saved project's listed worktrees from `MachineResources`, with a test that the listing receives them

## 2. Build line in the Gradle daemons view

- [ ] 2.1 Add `Strings.daemonBuild(tasks, started, session)` next to the other daemon texts, in English and Spanish, and raise the three files' length caps by those lines
- [ ] 2.2 Add `holdingBuildText` and show it as a secondary line under the row of each daemon with a holding build in `GradleDaemonsView`
- [ ] 2.3 Unit tests of `holdingBuildText` for every combination of tasks, start and session, in both languages, and a UI test that the line appears under a busy daemon and not under an idle one

## 3. Validation and wrap-up

- [ ] 3.1 Run `./gradlew test koverVerify`
- [ ] 3.2 Check the view with `shogun headless start` and `shogun headless capture` while a Gradle build runs
- [ ] 3.3 Remove the roadmap item from `docs/roadmap.md` and `docs/roadmap.es.md` when archiving
