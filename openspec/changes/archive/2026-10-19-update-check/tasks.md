# Tasks

## 1. Core

- [x] 1.1 Add the `ReleaseSource` port and the GitHub adapter over `java.net.http.HttpClient` (headers, 10-second timeout, lenient `tag_name`/`html_url` parsing); add adapter tests against a local HTTP server (release, malformed body, 403, timeout) and verify they pass
- [x] 1.2 Add version comparison, install channel detection (Homebrew, `.dmg`, Scoop, winget through `winget list`, `.msi`, `.deb`, unknown) and installer asset selection from the release's assets; add tests for tags with and without `v`, unparsable tags, `0.0.0`, each channel and each asset, and verify they pass
- [x] 1.3 Add `CheckForUpdateUseCase` with skipped version and manual mode, and the settings fields with backward-compatible reading; add tests with a fake clock and source, and verify they pass

## 2. Desktop app

- [x] 2.1 Add the friendly update notice with "What's new", the channel's reminder and action, "Skip this version" and close; add UI tests for each channel's reminder and for skipping, and verify they pass
- [x] 2.2 Add "Check for Updates…" to the Help menu (application menu on macOS) with its result dialog; add UI tests for up to date, newer and failed, and verify they pass
- [x] 2.3 Add "Check for updates automatically" to Preferences; add a test that no request is made when it's off, and verify it passes
- [x] 2.4 Add the strings in English and Spanish, document the check and the request it makes in `docs/guide/desktop-app.md`, `docs/guide/installation.md` and their Spanish versions, and verify `StringsTest` and `mkdocs build --strict` pass

## 3. Validation

- [x] 3.1 Run `./gradlew test` and verify every module passes
- [x] 3.2 Run the app with `-PappVersion=0.1.0` (secondary display) and check the notice, the copied Homebrew command and "Skip this version"; check that "Check for Updates…" with the network off reports the failure

## Workflow follow-up

- Archive the change before opening its pull request.
