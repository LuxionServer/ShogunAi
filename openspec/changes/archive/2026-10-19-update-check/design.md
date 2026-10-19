# Design

## Context

See proposal.md for motivation. The running version is `BuildInfo.VERSION`, `0.0.0` in development builds. Releases are published on `LuxionServer/ShogunAi` from `release/X.Y.Z` branches with `SHA256SUMS`, and distributed through the Homebrew cask, Scoop, winget, and `.dmg`/`.msi`/`.deb` downloads. Settings persist in `settings.json`.

## Goals / Non-Goals

**Goals:**
- One small anonymous request at most once a day, never blocking start-up.
- The suggested update path matches how the app was installed.

**Non-Goals:**
- Downloading or installing updates from the app (no auto-update framework; the app isn't signed or notarized).
- Checking for updates from the `shogun` command; `brew`/`scoop` already handle it for the command's installs.
- Pre-release channels.

## Decisions

**Source.** `GET https://api.github.com/repos/LuxionServer/ShogunAi/releases/latest` with `Accept: application/vnd.github+json`, a `User-Agent: ShogunAi/<version>` header and a 10-second timeout, through `java.net.http.HttpClient`. The endpoint already excludes drafts and pre-releases. The response's `tag_name` (`v1.2.3` or `1.2.3`) and `html_url` are read leniently. Alternative: the Homebrew tap's cask file; rejected because it only covers macOS. No token: the unauthenticated rate limit (60 per hour per IP) is far above one request a day.

**Version comparison.** Strict `X.Y.Z` numeric comparison; a tag that doesn't parse is ignored. Development builds (`0.0.0`) never check.

**Schedule.** On start-up, after the first frame, in the background: when `checkForUpdates` is on and `lastUpdateCheck` is older than 24 hours, check. `lastUpdateCheck` is written after any completed request, successful or not, so a failing network doesn't retry on every start. Automatic failures are silent; the manual check reports them.

**Install channel.** Detected once, from the running app's location, and only when a newer version was found:
- macOS with a `shogunai` folder in `/opt/homebrew/Caskroom` or `/usr/local/Caskroom` → Homebrew: `brew upgrade --cask shogunai`.
- macOS otherwise → the `.dmg` asset for the running architecture (`-arm64` or `-x64`).
- Windows with a path under `\scoop\apps\shogunai\` → Scoop: `scoop update shogunai`.
- Windows otherwise → `winget list --id LuxionServer.ShogunAi --exact` (10-second timeout); when it lists the app, `winget upgrade LuxionServer.ShogunAi`, else the `.msi` asset.
- Linux with the app under `/opt/shogunai` → the `.deb` asset, plus `sudo apt install ./<asset name>` to run after downloading.
- Anything else → the release page.
Installer assets are picked from the release's `assets` list by extension and architecture, not by a hard-coded name. Commands are copied as text; the app never runs them.

**Notice.** A friendly, dismissible banner at the top of the main content, e.g. "ShogunAi 1.3.0 is out! You're on 1.2.0." with "What's new" (opens `html_url`), the channel's reminder ("Installed with Homebrew? Run `brew upgrade --cask shogunai` to update." with "Copy", or "Download the new installer" with "Download"), "Skip this version" (stores `skippedVersion`) and close (hides it until the next start). It never takes focus or opens a dialog. A skipped version's notice comes back only for a newer version or a manual check.

**Controls.** Help > "Check for Updates…" (on macOS in the app menu, next to About) runs a check immediately and shows a small dialog: up to date, newer version (same actions as the notice), or the error. Preferences gains "Check for updates automatically", on by default.

## Risks / Trade-offs

- [Users who don't want the app to contact the network] → The option is in Preferences and the docs say exactly which request is made; no identifiers beyond the version in the User-Agent are sent.
- [GitHub API changes or rate limits] → Lenient parsing; a failure is silent when automatic and reported when manual.
- [A release is published before the cask or Scoop manifest is updated] → The notice says the version is available; `brew upgrade` may lag by the minutes the release workflow takes, which the docs mention.
