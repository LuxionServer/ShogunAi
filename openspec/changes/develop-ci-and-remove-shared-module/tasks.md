## 1. CI on develop

- [ ] 1.1 Rename `.github/workflows/validate-release-branch.yml` to `.github/workflows/tests.yml` and the workflow to `Tests`
- [ ] 1.2 Trigger it on pushes to `develop` and `release/**` and on pull requests targeting `develop`, with `concurrency` cancelling superseded runs and read-only `contents` permission
- [ ] 1.3 Update `docs/process/release.md` and `docs/process/release.es.md` to reference the renamed workflow and its wider triggers

## 2. Remove the `shared` module

- [ ] 2.1 Delete `shared/build.gradle.kts`, `include(":shared")` from `settings.gradle.kts` and `implementation(projects.shared)` from `desktopApp/build.gradle.kts`
- [ ] 2.2 Remove the `kotlinMultiplatform` plugin from the root `build.gradle.kts` and prune the version catalog entries only `shared` used
- [ ] 2.3 Update `docs/architecture/overview.md`/`.es.md` and `docs/getting-started.md`/`.es.md` so they no longer describe `shared`

## 3. Verification

- [ ] 3.1 `./gradlew test` and `./gradlew :desktopApp:compileKotlin` pass
- [ ] 3.2 The workflow YAML parses and passes `actionlint` (or careful review if unavailable)
- [ ] 3.3 `openspec validate --all` passes
