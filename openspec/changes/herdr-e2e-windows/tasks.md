# Tasks

## 1. Stand-in `herdr.exe`

- [ ] 1.1 Add `cli/src/e2eTest/windows/FakeRunnerLauncher.cs`, which reads the `.launch` file named like itself and starts the Java command with its own command line tail, and the Windows-only `fakeRunnerLauncher` task in `cli/build.gradle.kts` that compiles it, with `e2eTest` depending on it and receiving its path as `shogun.e2e.runnerLauncher`; verify `./gradlew :cli:e2eTest` still passes on macOS with the task skipped
- [ ] 1.2 Make `FakeRunners.install` copy the launcher as `herdr.exe` and write `herdr.launch` on Windows; verify `./gradlew :cli:e2eTest` passes on macOS

## 2. Herdr scenarios on Windows

- [ ] 2.1 Remove the Windows skip from the three Herdr scenarios in `RunnerEndToEndTest` and `MultirepoEndToEndTest`; verify they pass on the Windows job of the Tests workflow (push, then `gh run watch`) and on macOS locally
- [ ] 2.2 Update the end-to-end tests section of `docs/architecture/cli.md` and `cli.es.md` (the Windows launchers and no Herdr skip); verify both pages say the same

## 3. Validation

- [ ] 3.1 Run `./gradlew test koverVerify` locally and check the three operating systems' jobs pass on CI

## Workflow follow-up

- Archive the change (`/opsx:archive`) and remove the item from `docs/roadmap.md` and `docs/roadmap.es.md` before opening the pull request against `develop`.
