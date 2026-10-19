# Tasks

## 1. Version and macOS naming

- [ ] 1.1 Derive the macOS bundle version as major+1 in `desktopApp/build.gradle.kts` and name the `.dmg` after the release version in the suffix step; verify with `./gradlew :desktopApp:packageReleaseDmg -PappVersion=2.0.0` locally that the file is `ShogunAi-2.0.0-<arch>.dmg` and its `Info.plist` has `3.0.0`
- [ ] 1.2 Validate the version in `resolve-version` and pass it through `env` everywhere in `release.yml`; verify with `actionlint` and a dry run on a fork or a `workflow_dispatch` test branch that `release/1.4` fails in the first job

## 2. Tests before and across platforms

- [ ] 2.1 Extract the test job into a reusable workflow with the Linux, macOS and Windows matrix and call it from `tests.yml`; verify the check runs on all three on a pull request and fix or explicitly guard any test that fails on one of them
- [ ] 2.2 Make `create-release` depend on the reusable test workflow in `release.yml`; verify with `actionlint` that the job graph has no path to `build` without `test`

## 3. Checksums and manifests

- [ ] 3.1 Write and upload `SHA256SUMS` in `publish-release` before publishing; verify on the next release (or a draft on a fork) that `shasum -a 256 -c` passes for every asset
- [ ] 3.2 Make the cask, Scoop and winget jobs read checksums and `.dmg` names from the release instead of recomputing them; verify the rendered cask and Scoop manifest in the job log match `SHA256SUMS`

## 4. Privileges

- [ ] 4.1 Set `permissions: {}` at the top of `release.yml`, grant `contents: write` per job, add `persist-credentials: false`, and pin third-party actions to commit SHAs; verify with `actionlint` and by reading the job summaries' token permissions on the next run
- [ ] 4.2 Add `.github/dependabot.yml` for GitHub Actions and verify GitHub shows it in the repository's Dependabot settings

## 5. Docs

- [ ] 5.1 Update `docs/process/release.md` and `release.es.md` (branch from `develop`, the bundle version rule, `SHA256SUMS`, the pinned actions and their tags) and verify `mkdocs build --strict` passes

## Workflow follow-up

- Archive the change before opening its pull request; the first real check is the next release, so keep an eye on it.
