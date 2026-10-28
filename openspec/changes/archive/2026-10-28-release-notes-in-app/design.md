# Design

## Context

- `GitHubReleaseSource` calls `GET /repos/LuxionServer/ShogunAi/releases/latest` and keeps only `tag_name`, `html_url` and `assets`; the body is ignored.
- `CheckForUpdateUseCase` compares that release with the running version and returns `UpdateCheck.Available(running, release, channel, action)`.
- `UpdateViews` shows the notice (`Notice` with `NoticeKind.INFO` and its ⓘ icon) and the on-demand dialog; "What's new" opens `release.pageUrl` in the browser.
- The release workflow creates the draft with `--generate-notes`; the title comes from `.github/release-title`, overwritten on each release branch.
- The UI language lives in `Appearance.language`; composables only see `LocalStrings`.
- Releases come out several times a week, so users are often more than one version behind.

## Goals / Non-Goals

**Goals:**
- One unauthenticated request per check, as today, that also brings every newer version's notes in both languages.
- Notes are versioned in the repository, reviewed in the release pull request, and can't be published stale by mistake.
- The notes body on GitHub stays readable for people.

**Non-Goals:**
- A changelog page in the mkdocs site (a possible follow-up, fed by the same files).
- Showing notes after the update ("what's new since your last launch").
- Full Markdown support: only what the notes need.
- More than 30 versions behind: the dialog shows the 30 most recent and the release page link covers the rest.

## Decisions

### Notes files per version: `.github/release-notes/X.Y.Z.md` and `X.Y.Z.es.md`
Written on the `release/X.Y.Z` branch in the same commit as `.github/release-title`. Naming them after the version means a forgotten update can't republish the previous release's notes, which a single overwritten file (like `release-title`) would. The `.es.md` suffix matches the convention used in `docs/`. The folder also keeps the history for a future changelog page.

Alternatives: a single `.github/release-notes.md` overwritten on each release, rejected because it risks stale notes; Markdown under `docs/`, rejected for now because mkdocs would publish every file as a page.

### Release body: English visible, Spanish collapsed, delimited by HTML comments
```
<!-- shogunai:notes:en -->
…English notes…
<!-- /shogunai:notes:en -->

<details>
<summary>Español</summary>

<!-- shogunai:notes:es -->
…Spanish notes…
<!-- /shogunai:notes:es -->

</details>
```
GitHub doesn't render HTML comments, so the page shows the English notes and a collapsed "Español" section. The app extracts each language between its markers. Because the `/releases` listing already includes every body, a single request gives the app every language of every version.

Alternatives: uploading the notes as release assets (`release-notes.es.md`), rejected because it needs one extra download per version and language; showing both languages fully expanded, rejected because the page would be twice as long for every reader.

The workflow's "Create draft release" step writes this body to a file in `$RUNNER_TEMP` and passes it with `--notes-file`. If the English file is missing, it uses `--generate-notes` and emits `::warning::`. If only the Spanish file is missing, it publishes the English section and warns. The marker format is documented in `docs/process/release.md` because the app depends on it.

### Fetch the release listing instead of `/releases/latest`
`ReleaseSource.latest()` becomes `ReleaseSource.published(): Result<List<PublishedRelease>>`, calling `GET /releases?per_page=30`. The source drops drafts and pre-releases and entries whose tag isn't a version. `PublishedRelease` carries the version, page URL, assets and `ReleaseNotes`. `CheckForUpdateUseCase` takes the highest version as the latest; that is semver order instead of GitHub's "latest" flag, which is the same thing for this repository. The use case puts the notes of the versions newer than the running one in `UpdateCheck.Available.notes: List<VersionNotes>`, newest first. An empty listing fails as `UpdateCheckFailed`, like an unparseable answer does today. Skipped-version logic is unchanged.

### Notes parsing is pure domain code in `core`
`ReleaseNotes.parse(body)` returns `ReleaseNotes(english: String?, spanish: String?)`:
- markers present → each language's content, trimmed;
- no markers (older releases) → `english` = the body with every `by @user in https://…` suffix removed from the change lines, the `## What's Changed` heading and `**Full Changelog**` line dropped, and the `New Contributors` section dropped; `spanish` = null;
- empty result → both null.

`ReleaseNotes.forLanguage(AppLanguage)` returns the language's notes or falls back to English. Keeping this in `core` makes it unit-testable without UI and keeps `GitHubReleaseSource` thin.

### Language reaches the dialog through `Strings`
`Strings` gains `val language: AppLanguage` (`EnglishStrings` → `ENGLISH`, `SpanishStrings` → `SPANISH`). The dialog reads `LocalStrings.current.language`, so switching language with the dialog open recomposes it in the new language with no extra state. This avoids threading `Appearance` into `UpdateViews`.

### Minimal Markdown renderer in `desktopApp`
`ui/components/MarkdownText.kt` in two parts:
- a pure parser to blocks: heading (`#`–`###`), bullet item (`-`/`*`, one nesting level), paragraph, and blank-line separation; plus inline spans for `**bold**`, `` `code` ``, `[text](url)` and bare `https://` URLs;
- a composable that renders the blocks with the theme's typography and `LinkAnnotation.Clickable` links that call the caller's `onOpenUrl` (that is, `UpdateState.open`), so links go through the same opener as the rest of the app.

Alternatives: the `multiplatform-markdown-renderer` library, rejected because we control the notes' format and a dependency for four constructs isn't worth it; showing raw text, rejected because it isn't readable.

### UI: ⓘ on the notice and "What's new" both open `ReleaseNotesDialog`
- `Notice` gains an optional `onIconClick: (() -> Unit)?` with `iconDescription: String?`. When it is set, the leading icon becomes a button with a hand cursor and a tooltip. Other notices are unaffected.
- `UpdateState` gains `notes: UpdateCheck.Available?` with `showNotes(available)` and `closeNotes()`. Both the notice and the on-demand dialog call `showNotes`.
- `ReleaseNotesDialog` (in `UpdateViews.kt`) is an `AlertDialog` titled "What's new in X.Y.Z", with a scrollable body holding one section per `VersionNotes` (version heading, then the rendered notes or "No notes for this version"), "View on GitHub" (opens the newest version's page) and "Close". It stacks over the on-demand dialog when opened from it.

## Risks / Trade-offs

- [The app depends on the marker format in the release body] → The format is documented in `docs/process/release.md`, built only by the workflow, and covered by `ReleaseNotes.parse` tests; a body without markers degrades to the cleaned generated notes rather than failing.
- [GitHub's generated-notes format may change and the cleanup stops matching] → Only affects releases from before this change; the worst case is showing the lines with their authors and links.
- [`/releases` returns more data than `/releases/latest` (30 bodies)] → Still one request of a few tens of KB, once a day.
- [Semver max vs GitHub's "latest" flag could differ if an older patch is released after a newer minor] → Semver max is the correct answer for "is there something newer than what I run".
- [Notes written in a hurry turn into PR titles again] → `docs/process/release.md` gives the writing guidelines and an example; the notes are reviewed in the release pull request.

## Migration Plan

No data migration. Releases before this change keep their generated notes and the app cleans them. The first release after merging needs `.github/release-notes/X.Y.Z.md` and `.es.md`; until then the workflow falls back with a warning. Rollback: reverting the workflow step restores `--generate-notes`, and the app keeps working with both formats.
