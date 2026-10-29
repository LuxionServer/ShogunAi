# Tasks

## 1. Isolated environment

- [ ] 1.1 Register `desktopE2eTest` in `desktopApp/build.gradle.kts` (e2e package only, isolated `user.home`/`HOME`/`USERPROFILE`/`java.io.tmpdir`, empty `SHELL`, `PATH` without `shogun` and with `build/e2e/bin` first, no `CLAUDE_CONFIG_DIR`, `GIT_CONFIG_NOSYSTEM`, `shogunai.e2e.home`, folder wiped first, not cacheable); exclude the package from `test` and make `test` depend on it. Verify: `./gradlew :desktopApp:test --dry-run` lists `desktopE2eTest`.
- [ ] 1.2 Add the `E2eEnvironment` harness: guard on `shogunai.e2e.home`, per-test home reset with a generic `.gitconfig`, real repositories created with Git under a fresh temporary folder, a fake `shogun` launcher in `build/e2e/bin`, and waiting helpers. Verify: a guard test fails outside the task and the other tests pass inside it.

## 2. Shell under test

- [ ] 2.1 Move `main.kt`'s `ShellViewModel` construction to `shellViewModelOf(appContainer)` and use it from `main.kt`. Verify: `./gradlew :desktopApp:compileKotlin` and the app's existing tests pass.
- [ ] 2.2 Add `LocalFilePicker` with the `JFileChooser` pickers as default and route `ProjectSettingsView`'s picker calls through it. Verify: existing project settings tests pass.
- [ ] 2.3 Add the harness's `setShell` helper that renders `AppShell` with a real `AppContainer` as `main.kt` does, with a test picker. Verify: used by the tests in group 3.

## 3. End-to-end tests

- [ ] 3.1 Tour test: first-launch state → take the tour → (command step if shown) → sample created → later → sample's worktrees on the board → end the tour → project gone from the catalog and the sample folder removed. Verify: test passes in `desktopE2eTest`.
- [ ] 3.2 Add-project test: add project → choose a real repository → add a local file → save → listed in the sidebar and stored in the isolated `projects.json` with that file. Verify: test passes.
- [ ] 3.3 Create-worktree test: project with local files (a root file and a nested one) → new worktree → task id `TASK-123` → create → branch exists, worktree folder exists, files copied with their content, row shown. Verify: test passes.
- [ ] 3.4 Claude Code hooks test: fake launcher on the `PATH` → hooks section shows missing → install → confirm → isolated `~/.claude/settings.json` has the hooks running an existing `shogun` launcher and the section shows installed → remove → confirm → no ShogunAi hooks left. Verify: test passes.
- [ ] 3.5 Fix any bug the tests uncover with its regression test, or record it in design.md and ask Ciro if the fix is large. Verify: the regression test fails before the fix and passes after it.

## 4. Validation

- [ ] 4.1 Run `./gradlew test` and check that nothing was written to the real `~/.shogunai` or `~/.claude` during the run (modification times before and after). Verify: green build and unchanged files.
- [ ] 4.2 Document the end-to-end tests and their task in `docs/` (testing/architecture page, English and Spanish). Verify: `mkdocs build --strict` passes if available.

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md`.
- Implementation commit, bring in `origin/develop`, re-run tests, open the pull request against `develop` assigned to CiroFeliu.
