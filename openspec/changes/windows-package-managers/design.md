# Design

## Context

`release.yml` builds the installers in a matrix (two `.dmg`, one `.msi`, one `.deb`) and `shogun-<version>.zip`, publishes the draft release in `publish-release`, and then `update-homebrew-cask` renders `.github/homebrew/shogunai.rb` with `sed` and pushes it to `LuxionServer/homebrew-shogunai` with `HOMEBREW_TAP_TOKEN`. The `.msi` is named `ShogunAi-<version>.msi` (e.g. `ShogunAi-0.7.2.msi`; unlike the `.dmg`, its major version isn't rewritten). It's built by Compose Desktop's `packageReleaseMsi` (jpackage + WiX) with no `upgradeUuid` set in `desktopApp/build.gradle.kts`.

The desktop app's `main` runs the CLI instead of the window whenever it gets arguments, on every OS (`desktopApp/src/main/kotlin/app/luxion/shogunai/main.kt`), and the app bundles the `cli` module and a Java runtime. On Windows the `ShogunAi.exe` launcher is a GUI-subsystem executable, so a console sees none of its output; that's why the guide sends Windows users to the zip.

## Goals / Non-Goals

**Goals:**
- `winget install LuxionServer.ShogunAi` and `winget upgrade` work for every release with no manual step after the first submission.
- `scoop install shogunai` installs the app and a working `shogun` command, without Java, and `scoop update shogunai` follows every release.
- A newer `.msi` upgrades the installed one in place.

**Non-Goals:**
- A `shogun` command from the winget/`.msi` install (it would need a console launcher and a `PATH` entry in the installer; a follow-up if users ask).
- Code signing of the `.msi` (same reason as macOS notarization: a paid certificate).
- Chocolatey.
- Changing the `.msi`'s name or install scope.

## Decisions

### A fixed MSI upgrade code
`windows { upgradeUuid = "<uuid>" }` in `desktopApp/build.gradle.kts`. Without it, whether two builds share an `UpgradeCode` depends on jpackage's default, and winget relies on Windows Installer's major upgrade to replace a version. The value is the `UpgradeCode` of the already published `.msi` files if they share one (read from the `Property` table of `ShogunAi-0.7.1.msi` and `ShogunAi-0.7.2.msi`, e.g. with msitools' `msiinfo export <msi> Property`), so existing installs upgrade too. If they differ, a new UUID is generated once and the guide tells users of earlier versions to uninstall them first. The chosen value and how it was obtained are recorded in this file when known.

### winget: `winget-releaser` from the release workflow
A job `update-winget` (`needs: [resolve-version, publish-release]`, `runs-on: ubuntu-latest`) runs `vedantmgoyal9/winget-releaser@v2` (pinned to `@v2`, like the other actions) with:

- `identifier: LuxionServer.ShogunAi`
- `version: ${{ needs.resolve-version.outputs.version }}`
- `release-tag: v${{ needs.resolve-version.outputs.version }}`
- `installers-regex: '\.msi$'`
- `token: ${{ secrets.WINGET_TOKEN }}`

The action forks `microsoft/winget-pkgs` under the token's account and opens the update pull request; Microsoft's validation and moderation merge it, usually within a day. The action can only update an existing package, so the first version is submitted by hand (see Migration Plan). `WINGET_TOKEN` is a classic personal access token with `public_repo`, the only kind the action supports.

*Alternative considered:* `wingetcreate update --submit` on a Windows runner. Equivalent result; rejected because the action runs on Linux and needs no installation step.

### Scoop: an own bucket, updated like the Homebrew tap
A bucket is a plain Git repository; `LuxionServer/scoop-shogunai` holds `bucket/shogunai.json`. The template `.github/scoop/shogunai.json` uses the placeholders `@VERSION@` and `@SHA256_MSI@`, and the job `update-scoop-bucket` mirrors `update-homebrew-cask`: download `ShogunAi-<version>.msi` from the release with `curl -f`, compute its `sha256`, render the template with `sed`, clone the bucket with `SCOOP_BUCKET_TOKEN`, commit only when something changed and push.

*Alternative considered:* Scoop's `checkver`/`autoupdate` with the bucket's own scheduled Excavator workflow, which needs no secret here. Rejected to keep one model for every package manager (the release pushes, failures are visible in this repository's run) and to have the update the moment the release is published.

The manifest:

```json
{
    "version": "@VERSION@",
    "description": "Worktree layer for AI agents: provision, inspect and clean Git worktrees",
    "homepage": "https://github.com/LuxionServer/ShogunAi",
    "license": "GPL-3.0-only",
    "architecture": {
        "64bit": {
            "url": "https://github.com/LuxionServer/ShogunAi/releases/download/v@VERSION@/ShogunAi-@VERSION@.msi",
            "hash": "@SHA256_MSI@"
        }
    },
    "extract_dir": "<folder of the app inside the extracted msi>",
    "pre_install": "Set-Content -Path \"$dir\\shogun.cmd\" -Encoding ASCII -Value '@\"%~dp0runtime\\bin\\java.exe\" -cp \"%~dp0app\\*\" app.luxion.shogunai.MainKt %*'",
    "bin": "shogun.cmd",
    "shortcuts": [["ShogunAi.exe", "ShogunAi"]]
}
```

- Scoop extracts an `.msi` URL with `lessmsi` instead of running it, so no administrator rights and no Windows Installer registration: Scoop and a winget/`.msi` install don't interfere.
- `shogun.cmd` runs the bundled `java.exe` (a console executable) with the app's jars and the app's main class, which runs the CLI because it gets arguments. The exact `extract_dir`, the `app` and `runtime` folder names and whether the jars are directly in `app` are taken from the real layout of `ShogunAi-0.7.2.msi` extracted (`msiextract` from msitools, or `lessmsi x` on Windows); the implementation adjusts the template to that layout.
- `bin` makes Scoop create a `shogun` shim on the `PATH`.

### Job order and failure isolation
Both jobs depend only on `resolve-version` and `publish-release`, like `update-homebrew-cask`, so the three package managers update in parallel and a failure in one doesn't block the others or undo the published release. A missing `.msi` makes `update-scoop-bucket` fail before touching the bucket.

## Risks / Trade-offs

- [The winget pull request is rejected or stalls in moderation] → The release is already published; the run links the pull request, and the guide's manual download still works.
- [`WINGET_TOKEN` is a broad classic token] → It only needs `public_repo`, belongs to the maintainer and lives only in the repository's Actions secrets; it's named in `AGENTS.md` as a secret never to commit.
- [The extracted MSI layout changes with a Compose or jpackage upgrade] → The template's paths would break `shogun`; the manual check in the tasks runs on every Compose upgrade too (noted in `docs/process/release.md`).
- [Unsigned `.msi`: SmartScreen warns on manual installs] → Unchanged from today; winget shows the same publisher-unknown notice.

## Migration Plan

Manual steps by the maintainer, before the first release that runs the new jobs (the implementing agent documents them in `docs/process/release.md` but doesn't do them):

1. Create the public repository `LuxionServer/scoop-shogunai` with a `bucket/` folder and a README with the install commands.
2. Create a fine-grained token with `Contents: read and write` on that repository only, and store it as `SCOOP_BUCKET_TOKEN`.
3. Submit the first winget version by hand: `wingetcreate new https://github.com/LuxionServer/ShogunAi/releases/download/v<version>/ShogunAi-<version>.msi` with identifier `LuxionServer.ShogunAi`, and wait for it to be merged.
4. Create a classic token with `public_repo` and store it as `WINGET_TOKEN`.

Until step 3 is merged, `update-winget` fails on each release without affecting the others.

## Open Questions

- The `UpgradeCode` decision depends on what the published `.msi` files contain (task 1.1).
