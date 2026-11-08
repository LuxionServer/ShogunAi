# Tasks

## 1. The `.deb` without a system menus folder

- [ ] 1.1 Change `.github/smoke/smoke-linux.sh` to stop creating the menus folders, fail if `/etc/xdg/menus` exists, check `dpkg-query` reports the package installed, remove it and check it's gone, then create the folders, install again and check the desktop entry is added and removed; run it against the `0.0.0` release candidate's `.deb` in an `ubuntu:24.04` container and see it fail at the install
- [ ] 1.2 Rewrite the `.deb`'s `postinst` and `prerm` in a `doLast` of the Compose jpackage tasks targeting `Deb` in `desktopApp/build.gradle.kts`, failing the build when a script has no `xdg-desktop-menu` line; verify by building `:desktopApp:packageReleaseDeb` in a Linux container and inspecting the scripts
- [ ] 1.3 Run the changed `smoke-linux.sh` against the rebuilt `.deb` in the container and see it pass; also install the `0.0.0` `.deb` there first, then the new one, and check the upgrade and the removal succeed

## 2. Shared rendering of the Cask and the Scoop manifest

- [ ] 2.1 Add `.github/homebrew/render-cask.sh` and `.github/scoop/render-manifest.sh`, failing on an empty or malformed checksum or a leftover placeholder; verify by rendering with sample checksums (output equal to today's `sed`) and with an empty one (fails, names it)
- [ ] 2.2 Make `release.yml`'s `update-homebrew-cask` and `update-scoop-bucket` jobs call them; verify with `actionlint` and by reading the diff

## 3. Smoke testing the manifests

- [ ] 3.1 Add `.github/smoke/smoke-homebrew.sh` (local tap, URL check, cache seeding, install, `shogun --version` from a login shell, uninstall); verify with `bash -n` and `shellcheck` locally and in CI (installing a Cask on the developer's Mac would replace their `/Applications/ShogunAi.app`)
- [ ] 3.2 Add `.github/smoke/smoke-scoop.ps1` (Scoop install, URL check, cache seeding through `cache_path`, install, shortcut, `shogun --version` from a new session, uninstall); verify in CI
- [ ] 3.3 Add the `version` input and the `homebrew-cask` and `scoop` jobs to `smoke-test.yml`, and pass the version from `release.yml` and `release-candidate.yml`; verify with `actionlint`
- [ ] 3.4 Run `Release candidate` by hand on this branch (version `0.0.0`) and check every smoke job passes, including Linux, Homebrew and Scoop

## 4. Docs and checks

- [ ] 4.1 Update `docs/process/release.md` and `docs/process/release.es.md`: the Linux smoke checks, the manifests' smoke jobs, the render scripts, and drop the manual Scoop check after a jpackage upgrade that the job now does
- [ ] 4.2 Run `./gradlew test koverVerify` and `mkdocs build --strict`, both passing

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` (remove the item's second bullet).
- Bring in `develop`, re-run the checks, open the pull request against `develop` assigned to CiroFeliu.
