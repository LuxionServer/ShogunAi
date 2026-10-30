# Tasks

## 1. Apple-side setup (with the account holder)

- [x] 1.1 Create the Developer ID Application certificate from a CSR made in Keychain Access; verify `security find-identity -v -p codesigning` lists a "Developer ID Application" identity on the Mac.
- [x] 1.2 Export the certificate with its private key as a password-protected `.p12`; verify `openssl pkcs12 -info -nokeys -in <file>` (with `-legacy` if needed) opens it with that password.
- [x] 1.3 Create an App Store Connect team API key with the Developer role and download its `.p8`; note its key ID and the issuer ID.
- [x] 1.4 Store `MACOS_CERTIFICATE_P12` (base64), `MACOS_CERTIFICATE_PASSWORD`, `MACOS_NOTARY_KEY`, `MACOS_NOTARY_KEY_ID` and `MACOS_NOTARY_ISSUER_ID` with `gh secret set <NAME> -R LuxionServer/ShogunAi`; verify `gh secret list -R LuxionServer/ShogunAi` shows all five.
- [x] 1.5 Delete the local `.p12` and `.p8`; verify they're gone and that no repository file contains them (`git status`, `git grep`).

## 2. Signed build

- [x] 2.1 Add `desktopApp/macos/entitlements.plist` with the JIT, unsigned executable memory, disabled library validation and Apple Events entitlements; verify `plutil -lint`.
- [x] 2.2 In `desktopApp/build.gradle.kts` set `bundleID = "app.luxion.shogunai"`, the entitlements for app and runtime, `NSAppleEventsUsageDescription`, and `signing {}` enabled only when `macSigningIdentity` is set, with `macSigningKeychain` as its keychain; verify `./gradlew :desktopApp:packageReleaseDmg` without properties still builds an unsigned `.dmg` and `./gradlew test` passes.
- [x] 2.3 Build locally with `-PmacSigningIdentity=<identity>`, sign the `.dmg`, notarize it with the API key, staple it and verify `stapler validate`, `spctl -a -t open --context context:primary-signature -vv` on the `.dmg`, `codesign --verify --deep --strict` and `spctl -a -t exec -vv` on the app, and that the app starts and opens a worktree in Terminal.

## 3. Release workflow

- [x] 3.1 Add `.github/workflows/macos-installer.yml` (`workflow_call` with `version`/`ref`, `workflow_dispatch` with `version`) with the secret check, temporary keychain, signed build, `.dmg` rename and signing, notarization with log on failure, stapling, verification and cleanup steps from the design; verify `actionlint` on it.
- [x] 3.2 In `release.yml` replace the two macOS rows of `build` with a `build-macos` job calling the reusable workflow with `secrets: inherit`, and add it to the `needs` of `attest-assets` and `publish-release`; verify `actionlint` and that the artifact names stay `installer-macos-latest` and `installer-macos-26-intel`.
- [x] 3.3 Run the reusable workflow from a temporary branch with a `push` trigger, download both `.dmg` artifacts and verify them with `stapler validate`, `spctl` on the `.dmg` and app, and a first launch from a quarantined copy; then delete the temporary branch.

## 4. Documentation and Cask

- [x] 4.1 Remove the `caveats` from `.github/homebrew/shogunai.rb`; verify `ruby -c` on it.
- [x] 4.2 Remove the Gatekeeper warning and workaround from `README.md`, `README.es.md`, `docs/guide/installation.md` and `docs/guide/installation.es.md`; verify `git grep -i "notarized\|notarizada\|quarantine"` finds none left in them.
- [x] 4.3 Document the macOS signing secrets, the reusable workflow and certificate renewal and key rotation in `docs/process/release.md` and `docs/process/release.es.md`, and update the build step of its flow; verify `mkdocs build --strict`.

## Workflow follow-up

- Archive the change (`/opsx:archive`), syncing `macos-code-signing` and `homebrew-cask-distribution`, and remove "macOS signing and notarization" from "Out of scope" in `docs/roadmap.md` and `docs/roadmap.es.md`.
- Open the pull request against `develop`, assigned to CiroFeliu.
