# Design

## Context

The `build` matrix of `.github/workflows/release.yml` packages one `.dmg` per architecture with `packageReleaseDmg` on `macos-latest` (arm64) and `macos-26-intel`, renames it `ShogunAi-<version>-<arch>.dmg` and uploads it as a workflow artifact that `attest-assets` and `publish-release` pick up. Nothing is signed: `nativeDistributions.macOS` in `desktopApp/build.gradle.kts` has no bundle identifier, signing or entitlements.

The Compose Gradle plugin (1.12.2) can sign through jpackage: `macOS { signing { sign; identity; keychain } }`, `entitlementsFile` and `runtimeEntitlementsFile`. When signing is on, it also signs the native libraries packed inside the app's jars (skiko and others), which Apple's notary service inspects and rejects when unsigned. Its `notarization {}` block only accepts an Apple ID, an app-specific password and a team ID.

The app runs `osascript` to drive Terminal and System Events (`MacTerminalLauncher`, `MacAppWindowService`). Under the hardened runtime, Apple Events sent on the app's behalf need the `com.apple.security.automation.apple-events` entitlement and an `NSAppleEventsUsageDescription` in `Info.plist`, or macOS denies them without asking.

## Goals / Non-Goals

**Goals:**
- Both release `.dmg` files signed with Developer ID, notarized and stapled, so Gatekeeper opens them without warnings.
- No release published with an unsigned installer.
- Local and test builds unchanged and secret-free.
- A way to produce signed installers without releasing, to validate the setup now and after every certificate renewal.

**Non-Goals:**
- Signing the Windows `.msi` (Authenticode) or the Linux `.deb`.
- Signing the CLI zip: its launcher is a shell script plus jars, which Gatekeeper doesn't assess.
- Mac App Store distribution or sandboxing.
- Auto-update.

## Decisions

**Sign with the Compose plugin, notarize with `notarytool` in the workflow.** Signing through the plugin lets jpackage sign the runtime, the launcher and the libraries inside the jars in the right order, which by hand would mean unpacking and re-signing every jar. Notarization runs in the workflow with `xcrun notarytool submit --wait` and an App Store Connect API key, then `xcrun stapler staple`, because the plugin's notarization task only supports an Apple ID with an app-specific password. Alternative considered: the plugin's `notarizeReleaseDmg` with an app-specific password, rejected because that password grants access to the whole Apple ID, while an API key is scoped to the team, can be revoked on its own and doesn't break when the Apple ID's password changes.

**Signing is opt-in through Gradle properties.** `-PmacSigningIdentity=<name>` turns signing on and `-PmacSigningKeychain=<path>` points at the keychain holding it. Without the identity property the build stays unsigned, so `./gradlew test`, `:desktopApp:run` and local packages need nothing. The identity value is the certificate's name without the `Developer ID Application: ` prefix the plugin adds; the workflow reads it from the imported certificate instead of storing it, so the account holder's name never appears in a repository file.

**Bundle identifier `app.luxion.shogunai`.** Matches the code's root package. A stable identifier is required for the signature and for macOS to remember Apple Events permissions across updates. App data lives in `~/.shogunai`, not under the identifier, so nothing migrates.

**Entitlements.** One file, `desktopApp/macos/entitlements.plist`, used for both the app and its runtime: `com.apple.security.cs.allow-jit`, `com.apple.security.cs.allow-unsigned-executable-memory`, `com.apple.security.cs.disable-library-validation` (the JVM loads libraries extracted at run time, signed by other teams) and `com.apple.security.automation.apple-events`. The usage description is added with `infoPlist { extraKeysRawXml }`.

**A reusable workflow for macOS installers.** `.github/workflows/macos-installer.yml` takes `version` and `ref`, runs on both runners as a matrix and uploads `installer-macos-latest` and `installer-macos-26-intel`, the artifact names the release already uses. `release.yml` calls it with `secrets: inherit` in place of the two macOS rows of `build`; `attest-assets` and `publish-release` add it to their `needs`. The same file also accepts `workflow_dispatch` with a `version` input, building the dispatched ref, so signed installers can be produced without a tag or release. The job's steps:

1. Fail with the missing secret's name if any secret is empty.
2. Decode the `.p12` into `RUNNER_TEMP`, create a keychain there with a random password, import the certificate restricted to `codesign`, allow `codesign` to use the key without a prompt (`security set-key-partition-list`) and add the keychain to the search list.
3. Read the identity name from the keychain (`security find-identity -v -p codesigning`) and strip its prefix.
4. `./gradlew :desktopApp:packageReleaseDmg -PappVersion=… -PmacSigningIdentity=… -PmacSigningKeychain=…`.
5. Rename the `.dmg` as today, then `codesign --force --timestamp --sign` it with the same identity.
6. Write the API key to `RUNNER_TEMP`, `notarytool submit --wait --timeout 1h`; on a status other than `Accepted`, print `notarytool log <id>` and fail.
7. `stapler staple`, then verify: `stapler validate`, `spctl -a -t open --context context:primary-signature -vv` on the `.dmg`, and `codesign --verify --deep --strict` plus `spctl -a -t exec -vv` on the app mounted from it.
8. Always (`if: always()`): delete the keychain and the decoded files.

**Secrets.** Five Actions secrets in `LuxionServer/ShogunAi`:

| Secret | Content |
|---|---|
| `MACOS_CERTIFICATE_P12` | The Developer ID Application certificate and its private key, exported as `.p12` and base64-encoded |
| `MACOS_CERTIFICATE_PASSWORD` | The password chosen when exporting the `.p12` |
| `MACOS_NOTARY_KEY` | The App Store Connect API key's `.p8` file content |
| `MACOS_NOTARY_KEY_ID` | The API key's ID |
| `MACOS_NOTARY_ISSUER_ID` | The issuer ID shown on App Store Connect's API keys page |

The team ID isn't needed: the API key already belongs to the team and the identity comes from the certificate. The keychain password is generated per job. The local `.p12` and `.p8` are deleted once stored. `docs/process/release.md` documents the secrets and how to renew the certificate (every five years) and rotate the key.

**Validation.** Dispatch only works for workflows already on the default branch, so before merging the reusable workflow ran once from a temporary branch, `ci/macos-signing-check`, holding this change plus a `push` trigger for that branch, deleted afterwards; the change's own branch never carried the trigger. Its artifacts were downloaded and checked on a Mac with `spctl`, `stapler validate` and a first launch from a quarantined copy.

## Risks / Trade-offs

- **A native library the plugin doesn't sign** (outside a jar, or with an unusual extension) makes notarization fail → the notary log names the file; caught by the validation run before any release.
- **Notarization delays** can hold a release for minutes or, rarely, hours → `--timeout 1h` fails the build instead of hanging; the release stays in draft and the run can be re-run.
- **A fork or a missing secret can't release** → intended: an unsigned macOS installer is never published.
- **Certificate expiry** (Developer ID certificates last five years) breaks releases on that date → the renewal is documented in the release process page; apps already notarized keep opening because their signatures are timestamped.
- **Apple Events permissions are tied to the signature** → users of the unsigned builds are asked once more for Terminal/System Events control after updating, then never again for later signed releases.
