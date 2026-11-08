# Design

## Context

The documentation site is built with MkDocs 1.6, Material and `mkdocs-static-i18n` in its suffix layout (`page.md` for English, `page.es.md` for Spanish). Each release's notes live in `.github/release-notes/X.Y.Z.md` and `X.Y.Z.es.md`, outside `docs/`, written in the restricted Markdown the app renders (headings `#` to `###`, bullets, paragraphs, bold, code, links), starting today with a `## What's new` heading. The site has no CI job: it is built and previewed locally with `mkdocs build --strict` and `mkdocs serve`. See proposal.md for why the page is wanted.

## Goals / Non-Goals

**Goals:**
- The changelog pages are never committed: they are regenerated from the notes on every build and every live reload.
- No new Python dependency and nothing in `requirements-docs.txt` changes.

**Non-Goals:**
- Release dates: the notes files don't carry them, and tags aren't always fetched in a local clone. The GitHub link shows the date.
- Importing GitHub's generated notes of the releases before notes files existed (0.9.4 and older): the page links to the releases list instead.
- A CI workflow that builds the documentation; it is left as a roadmap follow-up.

## Decisions

### A MkDocs hook that writes the pages before each build

A Python hook script declared under `hooks:` in `mkdocs.yml` (`scripts/mkdocs/changelog_hook.py`) implements `on_pre_build`, which renders `docs/changelog.md` and `docs/changelog.es.md` from the notes before MkDocs collects the files. Both paths are in `.gitignore`, so they never reach Git and nobody edits them: every build overwrites them.

- First choice, discarded during implementation: adding the pages in memory in `on_files` with `File.generated(...)`. `mkdocs-static-i18n` 1.3.1 rebuilds every documentation file from `docs_dir` and its `src_path` (and fails on a file whose `abs_src_path` is `None`), so it only handles pages that exist on disk inside `docs/`.
- Other alternatives considered: a separate script writing the pages that has to be run by hand before building (easy to forget, and `mkdocs serve` wouldn't regenerate them); a published plugin such as a changelog or include plugin (a new dependency, and none knows the per-language naming of these files); committing the page and checking it in CI (still maintained by hand, only verified).
- A file is only rewritten when its content changes. `mkdocs serve` watches `docs/`, so rewriting identical content on every build would make each rebuild trigger the next one; with the check, a change to the notes costs one extra rebuild and then stops.

### Pure rendering separated from MkDocs

The hook module keeps the logic in plain functions over strings and file names (which files are notes, version ordering, heading shifting, page rendering) and imports MkDocs only inside the event functions. The unit tests (`scripts/mkdocs/test_changelog_hook.py`, standard `unittest`) therefore run with any Python 3, without MkDocs installed: `python3 -m unittest discover -s scripts/mkdocs`.

### Page layout

```
# Changelog                       (Spanish: # Historial de cambios)
intro: what the page is, newest first, link to all GitHub releases for older versions
## 0.9.5
[View on GitHub](<repo_url>/releases/tag/v0.9.5)      (Spanish: Ver en GitHub)
### What's new                    (notes shifted one level down)
- ...
```

- The release heading is the bare version, so its anchor (`#095`) is stable and linkable.
- The notes' headings are shifted so the shallowest one becomes `###` (never deeper than `######`), keeping relative levels. Notes written with `#`, `##` or no heading all end up under their release. Lines inside fenced code blocks (``` or `~~~`) are not touched.
- The repository URL comes from `repo_url` in `mkdocs.yml`, not hardcoded.
- The two languages' fixed texts (title, intro, link label, empty state) live in a small table in the hook, keyed by locale.

### Selecting and ordering the files

A file is a release note when its name matches `^(\d+)\.(\d+)\.(\d+)(\.es)?\.md$`; anything else is ignored. A version is listed when its English file exists (the same condition under which the release workflow publishes notes); the Spanish page uses the `.es.md` file when present, else the English one, matching the app's fallback. Versions sort by the integer tuple, descending. Files are read as UTF-8 and trailing whitespace is trimmed.

The notes folder is found relative to the directory holding `mkdocs.yml` (`config.config_file_path`), so building from another working directory still works.

### Live reload

The hook's `on_serve` adds `.github/release-notes/` to the live-reload server's watched paths, so editing a notes file triggers a rebuild that regenerates both pages.

With recent Click releases, MkDocs 1.6.1's `mkdocs serve` starts with live reload off (its `--livereload` flag loses its default), so no path is watched at all, `docs/` included; `mkdocs serve --livereload` turns it back on. That affects the whole site, not this page, and is left out of this change.

### Navigation

A top-level `Changelog: changelog.md` entry after "Roadmap", with `Changelog: Historial de cambios` in the Spanish `nav_translations`.

## Risks / Trade-offs

- [A notes file merged to `develop` before its release is published shows a version that isn't out yet] → Notes are only written on the release branch, which reaches `develop` after the release is merged; acceptable, and the link simply 404s for a moment if it ever happens.
- [Someone edits `docs/changelog.md` by hand] → the next build overwrites it; the file is ignored by Git, so the edit can't be committed by accident either.
- [`git-revision-date` has no Git history for a generated page] → it falls back to today's date with a printed notice that is not a MkDocs warning, so `--strict` still passes; verified during implementation.
- [The hook's tests aren't run in CI] → They run with plain `python3` in under a second; the CI job for the docs is a roadmap follow-up.
