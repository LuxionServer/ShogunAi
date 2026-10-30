# Release process

How a new version of the desktop installer (`.dmg`/`.msi`/`.deb`) is cut and published.

## Flow

```mermaid
graph LR
    A["Create branch release/X.X.X"] --> B["Push to release/X.X.X"]
    B --> C["Tests + Release candidate"]
    C --> R["Rehearsal on a clean account"]
    R --> D["PR release/X.X.X → main"]
    D --> E["Merge"]
    E --> F["Release: test + build + smoke test + tag + publish"]
```

1. **Create the branch** `release/X.X.X` (where `X.X.X` is the version to publish) from `develop`, write the headline feature of the release in `.github/release-title` (see [Release title](#release-title)) and its notes in `.github/release-notes/X.X.X.md` and `X.X.X.es.md` (see [Release notes](#release-notes)).
2. **Validate** — every push to `release/**` triggers two workflows, used to iterate and fix issues before opening the PR:
      - `Tests` (`.github/workflows/tests.yml`), which runs `./gradlew test`. The same workflow also runs on every pull request targeting `develop` or an `epic/**` branch and every push to them (a push to a branch with such a pull request open skips it, as the pull request already runs it), so regressions are caught before they reach a release branch.
      - `Release candidate` (`.github/workflows/release-candidate.yml`), which builds the installers for the version in the branch name and runs the [packaged smoke test](#packaged-smoke-test) on them. It never creates a tag, a release or a package manifest. It can also be started by hand from the Actions tab on any branch, giving the version to package.
3. **Rehearse** on a clean macOS account, following the [rehearsal script](rehearsal.md), before the release or before a demo.
4. **Open the PR** from `release/X.X.X` to `main` and review it like any other.
5. **Merge** — when a PR whose source branch matches `release/*` is merged, the `Release` workflow (`.github/workflows/release.yml`):
      1. validates the version taken from the branch name (or the tag): it must be `X.Y.Z` with digits only, otherwise the run fails before building anything,
      2. runs the full test suite on the release commit on Linux, macOS and Windows (`.github/workflows/test-suite.yml`, the same one `Tests` uses). Nothing else runs until it passes,
      3. builds the installers on macOS Apple Silicon, macOS Intel, Windows and Linux (`packageReleaseDmg`/`Msi`/`Deb`) passing that version, with a read-only token, and in parallel the standalone command-line tool once on Linux (`:cli:distZip`, see [Command-line tool](../architecture/cli.md)), all in `.github/workflows/package.yml`, the same one `Release candidate` uses. Each macOS `.dmg` is renamed `ShogunAi-X.X.X-<arch>.dmg` (`arm64`/`x64`, taken from the runner's own architecture) right after the build, and each installer and `shogun-X.X.X.zip` are kept as workflow artifacts,
      4. runs the [packaged smoke test](#packaged-smoke-test) on each installer (`.github/workflows/smoke-test.yml`). Nothing public exists yet, and nothing else runs until it passes on every operating system,
      5. creates and pushes the `vX.X.X` tag on the merge commit (the version comes from the branch name, no need to write it twice) and creates the GitHub Release in **draft** mode with the release notes as its body (see [Release notes](#release-notes)) titled `vX.X.X — <headline>` (the headline read from `.github/release-title`, see [Release title](#release-title)), only once,
      6. a job that holds only the permissions to sign attestations (it doesn't check out the repository or run the build) generates a signed build provenance attestation for each installer and the zip (`actions/attest`). The release isn't published until every attestation exists,
      7. once every attestation exists, the only job that writes to the repository writes `SHA256SUMS` with the checksum of every asset, uploads everything to the draft and publishes the release (takes it out of draft),
      8. regenerates the Homebrew Cask from `.github/homebrew/shogunai.rb` with the new version and the `.dmg` checksums read from `SHA256SUMS`, and pushes it to the `LuxionServer/homebrew-shogunai` tap. The Cask installs the app and links its launcher as the `shogun` command,
      9. regenerates `bucket/shogunai.json` in the `LuxionServer/scoop-shogunai` bucket from `.github/scoop/shogunai.json` with the new version and the `.msi` checksum read from `SHA256SUMS`, and pushes it,
      10. opens a pull request to `microsoft/winget-pkgs` updating `LuxionServer.ShogunAi` to the new `.msi` (`winget-releaser` action).

Steps 8, 9 and 10 run in parallel and are independent: if one fails, the release stays published and the others still run.

The whole flow from step 5 happens in a single CI run, with no manual intervention. The release is created as a draft and only published at the end because an already-published release is immutable for GitHub (it rejects new assets), and because writing the notes only once avoids duplicates if each matrix build wrote them on its own.

## Packaged smoke test

The test suite runs the code from Gradle on a machine set up like the developer's; the smoke test checks what a user downloads instead, installed on a fresh runner and started the way the desktop starts it. Each operating system has its own job and script in `.github/smoke/`:

| Runner | Installs | Starts the app | Then checks |
|---|---|---|---|
| macOS Apple Silicon and Intel | the `.dmg` into `/Applications` | with `open`, so it gets the Dock's minimal `PATH` | `shogun` linked into Homebrew's `bin`, the hooks installed, the tour started, then `shogun new` from a login shell |
| Windows | the `.msi`, silently | with the `PATH` a new session reads from the registry | `shogun.cmd` installed and its folder added to the user's `PATH`, the hooks installed, then `shogun new` through `shogun.cmd` from a new session |
| Linux | the `.deb` | as a new user without `~/.local/bin`, twice | first `shogun` installed into `~/.local/bin` but not on the `PATH`; after the restart, available and the hooks installed; then `shogun new` from a login shell |

The app does the work in its smoke-test mode: started with `SHOGUNAI_SMOKE_TEST_REPORT=<file>`, it opens no window and, with the same objects its buttons use, installs the command and Claude Code's hooks, goes through the start of the guided tour and ends it, adds the sample repository (`SHOGUNAI_SMOKE_TEST_REPOSITORY`) as a project with its local files (`SHOGUNAI_SMOKE_TEST_LOCAL_FILES`), creates a worktree and checks the copied files. It writes a JSON report of every step and exits with a non-zero status when a step failed. Each script then compares the report with what its machine should show, and checks the files `shogun new` copied (a `.env.local` listed in the project and a `config/local.json` declared in the repository's `.shogunai.json`).

A failure fails its job with the step or file that went wrong. Every job uploads a `smoke-test-<runner>` artifact with the report, the app's log and the installer's log, to diagnose it. In the `Release` workflow a failure means no tag, no release, no attestation and no updated manifest: fix it on a new `release/` branch (or the same one, before merging) and release again.

To try the smoke-test mode locally without touching your own settings, build the app (`./gradlew :desktopApp:createReleaseDistributable -PappVersion=1.0.0`), prepare a repository with `.github/smoke/prepare-repository.sh <folder>`, and run the launcher with an isolated home: `JAVA_TOOL_OPTIONS=-Duser.home=<empty folder>`, plus the three variables above.

## Release title

Every release reads its title from `.github/release-title`: the first line of that file is the headline feature, and the workflow titles the release `vX.X.X — <headline>`. For example, 0.9.0 shipping multi-repo support is released as `v0.9.0 — Multi-repo support`.

Write it when creating the release branch, in the same commit that bumps the version. If the file is empty or missing, the workflow falls back to the plain `vX.X.X` title and logs a warning, so a forgotten title never blocks a release — it is still worth an entry in the release notes.

## Release notes

Every release has notes written by hand for its users, in English and Spanish, in two files named after the version: `.github/release-notes/X.X.X.md` and `.github/release-notes/X.X.X.es.md`. Write them on the release branch, in the same commit as `.github/release-title`, and review them in the release pull request. Naming them after the version means a forgotten update can't republish the previous release's notes, and the folder keeps the history of every release.

How to write them:

- Describe what changes for the person using ShogunAi, in plain language: what they can do now, what works better, what was fixed.
- No pull request numbers, authors, branch names, internal class names or OpenSpec change names.
- Short: a heading per group (for example "What's new" and "Fixes"), one bullet per change, the feature in bold followed by a sentence or two.
- The Spanish file says the same as the English one; it isn't a summary.
- Only headings (`#` to `###`), bullets (one level of nesting), paragraphs, `**bold**`, `` `code` ``, links and bare `https://` URLs: the app renders that much and shows anything else as plain text.

For example:

```markdown
## What's new

- **Release notes inside the app.** Click the ⓘ icon on the update notice to read what a new version brings, in your language.

## Fixes

- Removing a worktree on Windows no longer fails on very long paths.
```

### Release body format

The workflow builds the GitHub release body from both files: the English notes visible and the Spanish ones in a collapsed "Español" section. Each language is enclosed in HTML comments, which GitHub doesn't render:

```markdown
<!-- shogunai:notes:en -->

…contents of X.X.X.md…

<!-- /shogunai:notes:en -->

<details>
<summary>Español</summary>

<!-- shogunai:notes:es -->

…contents of X.X.X.es.md…

<!-- /shogunai:notes:es -->

</details>
```

The desktop app reads each language between its markers to show the notes in the update notice (see [Desktop app](../guide/desktop-app.md)), so don't change the markers without changing the app. Releases published before this format only have GitHub's generated notes; the app shows them without authors, pull request links or the "Full Changelog" line.

If `X.X.X.md` is missing, the workflow publishes the release with GitHub's generated notes and logs a warning naming the missing files, so forgotten notes never block a release. If only `X.X.X.es.md` is missing, the body has only the English section and the workflow logs a warning; the app then shows the English notes to Spanish users.

## Windows package managers

One-time setup by the maintainer, before the first release that runs steps 8 and 9 (until it's done, those jobs fail without affecting the release):

1. Create the public repository `LuxionServer/scoop-shogunai` with a `bucket/` folder and a README with the install commands.
2. Create a fine-grained token with `Contents: read and write` on that repository only and store it as the `SCOOP_BUCKET_TOKEN` secret.
3. Submit the first winget version by hand with `wingetcreate new https://github.com/LuxionServer/ShogunAi/releases/download/v<version>/ShogunAi-<version>.msi`, using the identifier `LuxionServer.ShogunAi`, and wait for it to be merged (the action can only update an existing package).
4. Create a classic token with `public_repo` and store it as the `WINGET_TOKEN` secret.

The `.msi` declares a fixed upgrade code (`upgradeUuid` in `desktopApp/build.gradle.kts`), so a newer `.msi` replaces the installed version; don't change it. jpackage strips `java.exe` from the bundled runtime, so `desktopApp/build.gradle.kts` copies it back from the build JDK after `createRuntimeImage`: the Scoop `shogun` command runs it.

After a Compose Desktop or jpackage upgrade, check on Windows that the `.msi` layout still matches the Scoop manifest: install from the rendered manifest (`scoop install` with a local copy of `.github/scoop/shogunai.json` with the version and `sha256` filled in) and run `shogun --version`.

## Release checksums

Every release includes a `SHA256SUMS` file. To verify a download: `shasum -a 256 -c SHA256SUMS --ignore-missing`.

## Build provenance

Every installer and the CLI zip have a signed build provenance attestation that ties them to the release workflow run and the commit they were built from. To verify a download: `gh attestation verify ShogunAi-X.X.X-arm64.dmg --repo LuxionServer/ShogunAi`. Releases published before the `release-provenance-strict-warnings` change have no attestation.

## Pinned actions

Actions outside `actions/*` and `gradle/*` are referenced by commit SHA, because they receive secrets. Dependabot (`.github/dependabot.yml`) proposes updates weekly.

| Action | Tag | Commit |
|---|---|---|
| `vedantmgoyal9/winget-releaser` | `v2` | `b7370d4d152efdce803a26cc6ca9eee58cb2ad54` |

## Installer version

`compose.desktop.application.nativeDistributions.packageVersion` (in `desktopApp/build.gradle.kts`) reads the Gradle property `appVersion`:

```kotlin
packageVersion = (findProperty("appVersion") as String?) ?: "0.0.0"
```

- In CI, `-PappVersion=X.X.X` (extracted from the tag or the `release/X.X.X` branch name) is always passed, so the published installer always matches the tag.
- In local builds (without that property) it falls back to the default `0.0.0`, which is also the version shown in the app (`BuildInfo.VERSION`, generated by the `generateBuildInfo` task, which reruns whenever `appVersion` changes).
- The macOS bundle version is derived from it by one rule, `macBundleVersion` in `desktopApp/build.gradle.kts`: major + 1 (`0.9.0` → `1.9.0`, `1.4.0` → `2.4.0`). It is always valid for macOS, increases with the release version and never collides. The `.dmg` files are named after the release version, never the bundle version.
- jpackage requires strictly numeric versions (`MAJOR.MINOR.PATCH`), it doesn't accept suffixes like `-dev`. On macOS the first number must also be greater than zero, so packaging locally (`packageDmg`, `createDistributable`) needs an explicit version, e.g. `./gradlew :desktopApp:packageDmg -PappVersion=1.0.0`; `./gradlew :desktopApp:run` works with the default.

## Manual path (escape hatch)

The `Release` workflow is also triggered by a direct tag push (`git tag vX.X.X && git push origin vX.X.X`), without going through a `release/` branch. Useful for one-off hotfixes, but the normal flow is the `release/*` branch flow described above.

## Why the tag doesn't trigger the workflow on its own in the merge case

If the merge job pushed the tag using the default `GITHUB_TOKEN`, GitHub would **not** trigger the same workflow's `on: push: tags` (a restriction to prevent loops). That's why the build and publish don't depend on that re-trigger: they run in the same run that creates the tag, using `needs` between jobs.
