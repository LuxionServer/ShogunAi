# Tasks

## 1. Smoke-test mode in the desktop app

- [x] 1.1 Add `SmokeTestScenario` and its report model in `desktopApp` (`app.luxion.shogunai.smoke`), with the command, hooks, tour, project and worktree steps on injected collaborators; verify with unit tests covering every scenario of the "Smoke-test command and hooks steps", "Smoke-test tour step" and "Smoke-test project and worktree steps" requirements (fakes, temporary folders)
- [x] 1.2 Write the report as JSON and decide the exit status (failed step → non-zero, dependent steps skipped); verify with unit tests of the serialised report and the exit status
- [x] 1.3 Wire it in `main.kt` and to `AppContainer`'s objects: run it when `SHOGUNAI_SMOKE_TEST_REPORT` is set and there are no arguments, without opening a window or starting background checks; verify with a unit test of the start decision and `./gradlew :desktopApp:test`
- [x] 1.4 Run the packaged app locally (`./gradlew :desktopApp:createDistributable -PappVersion=1.0.0`, then `open -W --env …`) against a sample repository and check the report; verify the report passes and the worktree has the copied files

## 2. Per-OS smoke scripts

- [x] 2.1 Add `.github/smoke/prepare-repository.sh` creating the sample repository (`.shogunai.json` copying `config/local.json`, ignored `.env.local`), and `.github/smoke/check-worktree.sh` comparing a worktree's copied files with the base's; verify by running both locally
- [x] 2.2 Add `.github/smoke/smoke-macos.sh`: attach the `.dmg`, install into `/Applications`, open with `open -W --env`, assert the report (available in the Homebrew folder, hooks installed, tour at `OPEN_AGENT`, files copied), run `shogun new` from `zsh -l` and check its files; verify locally with the `.dmg` from 1.4 where it doesn't need a fresh machine, and on CI in 4.2
- [x] 2.3 Add `.github/smoke/smoke-linux.sh`: install the `.deb`, create a fresh user, run the app twice as that user with `env -i` (first: not on `PATH` in `~/.local/bin`; second: available, hooks installed), run `shogun new` from `bash -l` and check its files; verified on CI in 4.2
- [x] 2.4 Add `.github/smoke/smoke-windows.ps1`: install the `.msi` silently, start the app with the registry's `PATH`, assert the report (installed by the app, restart needed, hooks installed), rebuild the `PATH` and run `shogun new` through `shogun.cmd`, and check its files; verified on CI in 4.2

## 3. Workflows

- [x] 3.1 Move the installer matrix and the CLI zip build from `release.yml` into a reusable `package.yml`, unchanged in behaviour; verify with `actionlint` (or `gh workflow view` parse) and in 4.2
- [x] 3.2 Add the reusable `smoke-test.yml` running the scripts of group 2 per installer, with `contents: read`, no secrets, a timeout and the report and logs uploaded always; verify with `actionlint` and in 4.2
- [x] 3.3 Reorder `release.yml`: `test → package → smoke-test → create-release → attest-assets → publish-release → manifests`; verify with `actionlint` and by reading the `needs` graph
- [x] 3.4 Add `release-candidate.yml` on `push` to `release/**` and `workflow_dispatch` (input `version`), validating the version and running `package` and `smoke-test` only; verify with `actionlint` and in 4.2
- [x] 3.5 Update `docs/process/release.md` and `release.es.md` with the release candidate run, the smoke test step before tagging and what a failure leaves behind; verify with `mkdocs build --strict`

## 4. Rehearsal script and validation

- [x] 4.1 Write `docs/process/rehearsal.md` and `rehearsal.es.md` (clean macOS account, install, Dock, guided tour to its end, checks per step, removing the account), link them from the release process and `mkdocs.yml`; verify with `mkdocs build --strict`
- [x] 4.2 Push the branch as `release/0.0.0`, check that `Release candidate` passes on the four runners (fixing what fails), then delete that branch; verify with `gh run view` of the green run
- [x] 4.3 Run `./gradlew test` and check it passes with no warnings

## Workflow follow-up

- Archive the change with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md`.
- Implementation commit, bring in `origin/develop`, rerun the tests and open the pull request against `develop` assigned to CiroFeliu.
