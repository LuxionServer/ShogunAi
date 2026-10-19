# Design

## Context

See proposal.md for motivation. `desktopApp/build.gradle.kts` sets the macOS `packageVersion` to `1.<minor>.<patch>` when the version starts with `0.` and to the version itself otherwise, while `release.yml` (step "Download macOS dmgs and compute sha256") and `.github/homebrew/shogunai.rb` always build `1.` + the rest, so they disagree from 1.0.0 on, and 0.8.0 (bundle 1.8.0) would collide with a real 1.8.0. The release workflow has `resolve-version` → `create-release` → `build` (matrix: macOS arm64, macOS Intel, Windows, Linux) and `build-cli` → `publish-release` → cask, Scoop and winget jobs. It triggers on `vX.Y.Z` tags and on merged pull requests to `main`; `tests.yml` runs on `ubuntu-latest` only.

## Goals / Non-Goals

**Goals:**
- One rule for the macOS bundle version; `.dmg` names that humans and manifests can predict from the release version.
- No release without green tests on all three operating systems.

**Non-Goals:**
- Code signing and notarization (out of scope in the roadmap).
- Build provenance attestations; checksums are enough for now and can be extended later.

## Decisions

**Bundle version rule.** `macBundleVersion(v) = "${major + 1}.${minor}.${patch}"` for every version. It's monotonic, never starts with 0, and never collides (0.8.0 → 1.8.0, 1.8.0 → 2.8.0). The upgrade path from today's installs holds: 0.8.0 is already 1.8.0, and 0.9.0 becomes 1.9.0. Alternative: keep `0.x → 1.x` and identity from 1.0 on; rejected because 1.0.0 would downgrade below 1.9.0 for macOS. The rule lives in `build.gradle.kts`; the workflow never computes it again.

**`.dmg` names.** The existing post-build step that adds the architecture suffix renames the jpackage output to `ShogunAi-<release version>-<arch>.dmg`, so the bundle version never appears in a file name. The cask template drops `dmg_version` and uses `#{version}`.

**Tests before release.** A `test` job with the same OS matrix as `tests.yml` (factored into a reusable workflow `.github/workflows/test-suite.yml` called by both) runs after `resolve-version`; `create-release` needs it. Alternative: require the `Tests` check on the release branch only; rejected because the tag path has no pull request.

**OS matrix.** `ubuntu-latest`, `macos-latest`, `windows-latest`, `fail-fast: false` so all failures are visible. Tests that only make sense on one OS keep their existing guards.

**Version validation and quoting.** `resolve-version` checks the value against `^[0-9]+\.[0-9]+\.[0-9]+$` in a step that reads it from `env`. Every later use goes through `env: VERSION: ${{ needs.resolve-version.outputs.version }}` and `"$VERSION"` in the script; `-PappVersion="$VERSION"` is quoted.

**Checksums.** `publish-release` downloads the build artifacts, writes `SHA256SUMS` (`sha256sum` output format), uploads it and only then publishes. The cask, Scoop and winget jobs read their checksums from that file instead of downloading and hashing again.

**Asset upload.** Build jobs only package and keep their installer as a workflow artifact, with a read-only token. `publish-release` is the single job that writes: it downloads the artifacts, writes `SHA256SUMS`, uploads everything to the draft with `gh` and publishes. The tag on the merge commit is created in `create-release`, after the tests, through the API without a checkout, so a failing test run leaves no tag behind. `softprops/action-gh-release` is no longer used.

**Privileges.** Top-level `permissions: {}`; `contents: write` on `create-release`, the asset upload steps' jobs and `publish-release`; `contents: read` elsewhere. `persist-credentials: false` on every checkout except the jobs that push to the tap and bucket repositories (they push with their own token anyway, so they can also use `false` and set the remote URL explicitly). `vedantmgoyal9/winget-releaser` is pinned to the SHA of their current major tag. The project doesn't allow comments in code, so the usual `# v2` trailer isn't used; the tag each SHA corresponds to is listed in `docs/process/release.md`. Dependabot for GitHub Actions is enabled to keep the SHAs current.

## Risks / Trade-offs

- [macOS and Windows runners are slower and cost more minutes] → Superseded runs keep being cancelled, and `gradle/actions/setup-gradle` caches per OS.
- [Some tests may fail on Windows today] → Fix them in this change or guard them with an explicit OS assumption and a note in the task; don't drop Windows from the matrix.
- [Renaming `.dmg` files breaks links to old assets] → Old releases keep their names; only new ones change.

## Migration Plan

The next release after this change is the first with the new names and `SHA256SUMS`. If it fails, the release stays in draft as today and can be re-run.
