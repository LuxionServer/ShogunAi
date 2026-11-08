# Design

## Context

The documentation site is built with MkDocs 1.6, Material and `mkdocs-static-i18n` in its suffix layout (`page.md` for English, `page.es.md` for Spanish). Each release's notes live in `.github/release-notes/X.Y.Z.md` and `X.Y.Z.es.md`, outside `docs/`, written in the restricted Markdown the app renders (headings `#` to `###`, bullets, paragraphs, bold, code, links), starting today with a `## What's new` heading. The site has no CI job: it is built and previewed locally with `mkdocs build --strict` and `mkdocs serve`. See proposal.md for why the page is wanted.

## Goals / Non-Goals

**Goals:**
- The changelog pages exist only in the build output, generated from the notes on every build and every live reload.
- No new Python dependency and nothing in `requirements-docs.txt` changes.

**Non-Goals:**
- Release dates: the notes files don't carry them, and tags aren't always fetched in a local clone. The GitHub link shows the date.
- Importing GitHub's generated notes of the releases before notes files existed (0.9.4 and older): the page links to the releases list instead.
- A CI workflow that builds the documentation; it is left as a roadmap follow-up.

## Decisions

### A MkDocs hook that adds generated files

A Python hook script declared under `hooks:` in `mkdocs.yml` (`scripts/mkdocs/changelog_hook.py`) implements `on_files` and adds `changelog.md` and `changelog.es.md` to the build with `File.generated(config, path, content=...)`, so they never exist in `docs/` nor in Git.

- Alternatives considered: a pre-build script writing the pages into `docs/` (must be run by hand or by every caller, and leaves an ignored generated file in the sources that `mkdocs serve` would also watch, looping); a published plugin such as a changelog or include plugin (a new dependency, and none knows the per-language naming of these files); committing the page and checking it in CI (still maintained by hand, only verified).
- Hooks are loaded as plugins after the configured ones. `mkdocs-static-i18n` registers its `on_files` with priority `-100` precisely to run last, so the hook's default priority `0` runs before it and the i18n plugin sees both generated files like any other `page.md`/`page.es.md` pair, assigning `changelog.es.md` to the Spanish site.

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

### Navigation

A top-level `Changelog: changelog.md` entry after "Roadmap", with `Changelog: Historial de cambios` in the Spanish `nav_translations`.

## Risks / Trade-offs

- [A notes file merged to `develop` before its release is published shows a version that isn't out yet] → Notes are only written on the release branch, which reaches `develop` after the release is merged; acceptable, and the link simply 404s for a moment if it ever happens.
- [`mkdocs-static-i18n` changes how it orders its `on_files`] → `mkdocs build --strict` would then fail or omit the Spanish page; the version is pinned in `requirements-docs.txt` and the task list verifies both pages exist in `site/`.
- [`git-revision-date` has no Git history for a generated page] → it falls back to today's date with a printed notice that is not a MkDocs warning, so `--strict` still passes; verified during implementation.
- [The hook's tests aren't run in CI] → They run with plain `python3` in under a second; the CI job for the docs is a roadmap follow-up.
