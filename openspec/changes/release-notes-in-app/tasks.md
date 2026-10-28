# Tasks

## 1. Release notes in the release workflow

- [ ] 1.1 In `.github/workflows/release.yml`, make "Create draft release" build the body from `.github/release-notes/${VERSION}.md` and `${VERSION}.es.md` with the marker format of design.md, pass it with `--notes-file`, fall back to `--generate-notes` with a `::warning::` when the English file is missing, and warn when only the Spanish one is missing; verify by running the step's script locally against sample files for the three cases (both, English only, none) and checking the produced body and warnings
- [ ] 1.2 Write `.github/release-notes/` with the notes of the next release (English and Spanish) describing this change, so the first release after merging already uses the new format; verify the files exist and follow the guidelines of 1.3
- [ ] 1.3 Document in `docs/process/release.md` and `release.es.md` the notes files, how to write them (user-facing, plain language, no PR numbers or authors, short example), the body marker format the app relies on and the fallback; verify `mkdocs build --strict` passes

## 2. Domain: published releases and their notes

- [ ] 2.1 Add `ReleaseNotes(english, spanish)` with `parse(body)` and `forLanguage(AppLanguage)`, and `VersionNotes(version, pageUrl, notes)` in `core`; verify with `UpdateTest` (or a new `ReleaseNotesTest`) cases for markers in both languages, English only, generated notes cleanup (authors, PR links, "What's Changed", "Full Changelog", "New Contributors"), empty body, and the English fallback
- [ ] 2.2 Replace `ReleaseSource.latest()` with `published(): Result<List<PublishedRelease>>` and update `FakeReleaseSource`; make `GitHubReleaseSource` call `/releases?per_page=30`, skip drafts, pre-releases and non-version tags, and parse each body with `ReleaseNotes.parse`; verify with `GitHubReleaseSourceTest` cases for a listing with several releases, a pre-release, a draft, an unparseable tag, an empty listing and a non-200 answer
- [ ] 2.3 Make `CheckForUpdateUseCase` take the highest published version as the latest and fill `UpdateCheck.Available.notes` with the versions newer than the running one, newest first; verify with `CheckForUpdateUseCaseTest` cases for one and several versions behind, up to date, skipped version and empty listing
- [ ] 2.4 Update `docs/architecture/domain.md`, `infrastructure.md` and their `.es.md` translations where they describe the update check source and model; verify `mkdocs build --strict` passes

## 3. Desktop: notes dialog

- [ ] 3.1 Add `val language: AppLanguage` to `Strings` and its implementations, plus the new strings (notes dialog title, "View on GitHub", "No notes for this version", ⓘ tooltip/description) in English and Spanish; verify the desktop module compiles and the existing i18n tests pass
- [ ] 3.2 Add `MarkdownText` in `ui/components`: a pure block/inline parser (headings, bullets with one nesting level, paragraphs, bold, code, links, bare URLs) and a composable that renders it with theme typography and clickable links through an `onOpenUrl` callback; verify with parser unit tests and a UI test that clicking a link calls `onOpenUrl` with its URL
- [ ] 3.3 Add optional `onIconClick` and `iconDescription` to `Notice` so the leading icon becomes a button with tooltip and hand cursor only when set; verify with a UI test that the icon is clickable when set and that other notices are unchanged
- [ ] 3.4 Add `notes`, `showNotes` and `closeNotes` to `UpdateState`, and `ReleaseNotesDialog` in `UpdateViews.kt`; wire the notice's ⓘ and both "What's new" buttons to it and add "View on GitHub" in the dialog; verify in `UpdateViewsUiTest` the scenarios of the update-check delta: ⓘ and "What's new" open the dialog, Spanish notes in Spanish, English fallback, language switch with the dialog open, several versions newest first, empty notes message, and "View on GitHub" opens the newest page
- [ ] 3.5 Update `docs/guide/desktop-app.md`, `docs/architecture/ui.md` and their `.es.md` translations where they describe the update notice; verify `mkdocs build --strict` passes

## 4. Integration

- [ ] 4.1 Run `./gradlew test` and verify every module passes without warnings
- [ ] 4.2 Run the app against a stub of the GitHub listing with a running version two releases behind (marker notes in one, generated notes in the other), open the notes from the notice and from "Check for Updates…" in English and Spanish, and verify the dialog with a screenshot
- [ ] 4.3 Check whether the change affects `docs/roadmap.md` and `roadmap.es.md` and update both if it does

## Workflow follow-up

- Commit the implementation once 4.1 and 4.2 pass, archive the change (`/opsx:archive`) in the same branch, and open the pull request against `develop`.
