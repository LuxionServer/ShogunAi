# Tasks

## 1. MSI upgrade code

- [ ] 1.1 Download `ShogunAi-0.7.1.msi` and `ShogunAi-0.7.2.msi` from the GitHub Releases (with `curl -L -f` to the scratch directory, not the repository) and read their `UpgradeCode` from the `Property` table (`msiinfo export <msi> Property` from msitools; install it with `brew install msitools` if missing). Record both values and the decision in `design.md` ("A fixed MSI upgrade code").
- [ ] 1.2 Set `upgradeUuid` in the `windows { }` block of `desktopApp/build.gradle.kts` to the shared value, or to a newly generated UUID if they differ; verify `./gradlew :desktopApp:tasks` still configures.

## 2. Scoop manifest

- [ ] 2.1 Extract `ShogunAi-0.7.2.msi` (`msiextract` from msitools, into the scratch directory) and note the folder containing `ShogunAi.exe`, the runtime's `java.exe` and the app's jars; record the layout in `design.md`.
- [ ] 2.2 Add `.github/scoop/shogunai.json` from the manifest in `design.md`, with the real `extract_dir`, jar folder and `java.exe` path, and the placeholders `@VERSION@` and `@SHA256_MSI@`; verify it's valid JSON after rendering it with `sed` and dummy values (`jq . `).
- [ ] 2.3 Check that the `shogun.cmd` line works: with the jars extracted in 2.1 and a local JDK 21, run `java -cp "<app folder>/*" app.luxion.shogunai.MainKt --version` and confirm it prints the version (the `.cmd` itself is checked on Windows in 4.2).

## 3. Release workflow

- [ ] 3.1 Add the `update-scoop-bucket` job to `.github/workflows/release.yml`, mirroring `update-homebrew-cask`: `needs: [resolve-version, publish-release]`, checkout of the source at the same ref, `curl -sL -f` of `ShogunAi-${VERSION}.msi`, `sha256sum`, `sed` render into `bucket/shogunai.json` of a clone of `LuxionServer/scoop-shogunai` made with `SCOOP_BUCKET_TOKEN`, commit only when `git diff --cached --quiet` fails, with the message `Update shogunai to v${VERSION}`.
- [ ] 3.2 Add the `update-winget` job: `needs: [resolve-version, publish-release]`, `runs-on: ubuntu-latest`, `vedantmgoyal9/winget-releaser@v2` with `identifier: LuxionServer.ShogunAi`, `version`, `release-tag: v<version>`, `installers-regex: '\.msi$'` and `token: ${{ secrets.WINGET_TOKEN }}`.
- [ ] 3.3 Validate the workflow syntax with `actionlint` if available (otherwise a YAML parse) and check that no job other than the two new ones changed (`git diff` of the file).

## 4. Docs and validation

- [ ] 4.1 Add "Windows (winget)" and "Windows (Scoop)" sections to `docs/guide/installation.md` after the Homebrew one, update the `shogun` command table (Scoop: already installed, no Java; winget or `.msi`: use the zip) and the note about the `.msi` launcher; same in `installation.es.md`. Update the install section of `README.md` and `README.es.md`.
- [ ] 4.2 In `docs/process/release.md` (and `.es.md`), describe the two new jobs, the maintainer's one-time setup from `design.md`'s Migration Plan (bucket repository, `SCOOP_BUCKET_TOKEN`, first `wingetcreate` submission, `WINGET_TOKEN`), and the manual check after a Compose upgrade: on Windows, `scoop install` from the rendered manifest and `shogun --version`.
- [ ] 4.3 In `AGENTS.md`'s "Public repository" section, list `SCOOP_BUCKET_TOKEN` and `WINGET_TOKEN` next to `HOMEBREW_TAP_TOKEN` as secrets that live only in the repository's Actions secrets.
- [ ] 4.4 Remove the "Windows package managers" bullet from the "Distribution" section of `docs/roadmap.md` and `docs/roadmap.es.md`, keeping the update-check bullet.
- [ ] 4.5 Run `./gradlew test` and `mkdocs build --strict`.

## Workflow follow-up

- The maintainer does the Migration Plan steps before the next release; until then the two new jobs fail without affecting the release.
- Archive the change (`/opsx:archive windows-package-managers`) once validated, in the same branch, before opening its pull request against `develop`.
