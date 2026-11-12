# Design

## Context

The site is built with MkDocs 1.6.1, Material, `mkdocs-static-i18n` and `mkdocs-git-revision-date-plugin`, pinned in `requirements-docs.txt`, plus a local hook (`scripts/mkdocs/changelog_hook.py`) with standard-library unit tests next to it. Every existing workflow is Gradle-based; none installs Python. Building today with `--strict` passes, but MkDocs reports a link to a missing anchor only at info level, so one such link (`#título-de-la-release` in `docs/process/release.es.md`) goes unnoticed: the `toc` extension's default slugify drops non-ASCII letters, so the heading's real id is `ttulo-de-la-release`.

## Goals / Non-Goals

**Goals:**
- A fast, single-job check that a documentation change builds and keeps the hook working.
- Local and CI builds resolve the same package versions.

**Non-Goals:**
- Publishing the site (no GitHub Pages or deploy step).
- Making the check required in branch protection; that is a repository setting, not part of this change.
- Changing the `toc` slugify for the whole site, which would change every existing anchor.

## Decisions

### A separate `Docs` workflow filtered by paths
A new `.github/workflows/docs.yml` runs on `pull_request` with a `paths` filter (`docs/**`, `mkdocs.yml`, `requirements-docs.txt`, `scripts/mkdocs/**`, `.github/release-notes/**`, and the workflow file itself) and on `workflow_dispatch`. It has no branch filter, so release pull requests to `main`, which add the release notes the changelog page is built from, are checked too. It uses the same `concurrency` group pattern as `Tests` to cancel superseded runs.
Alternative: a job inside `tests.yml`. Rejected: `Tests` has no path filter and runs a three-OS Gradle matrix; documentation-only pull requests would pay for it, and Kotlin-only ones would build the site for nothing.

### One Ubuntu job, Python from `actions/setup-python`
Steps: checkout with `fetch-depth: 0` (the revision-date plugin reads each page's Git history; a shallow clone gives every page the same date and a warning), `actions/setup-python` with Python 3.13 and pip caching keyed on `requirements-docs.txt`, `pip install -r requirements-docs.txt`, the hook's tests, then `mkdocs build --strict`. Tests run first because they take milliseconds and pinpoint a hook failure before the build reports it less clearly.

### Anchor and unrecognized-link validation as warnings
`mkdocs.yml` gets `validation: links: anchors: warn` and `unrecognized_links: warn`, so `--strict` turns both into failures. The other link validations already default to warn. Today this surfaces exactly one problem, fixed below.

### Fix the Spanish anchor with an explicit heading id
The heading becomes `## Título de la release {#titulo-de-la-release}` (with the `attr_list` Markdown extension enabled) and its two links point to `#titulo-de-la-release`. The English page is already correct.
Alternatives: linking to the generated `#ttulo-de-la-release` (works but reads as a typo, and breaks if slugify ever changes), or a Unicode slugify for the whole site (changes every existing anchor, see Non-Goals).

### Pin Click to 8.5.1
MkDocs 1.6.1 declares `--livereload` as a flag with `default=True` that shares its destination with `--no-livereload`. Parsing `mkdocs serve` with no flags through Click's real parser gives `livereload = True` on 8.1.8 to 8.2.2, `False` on 8.3.0, 8.3.1 and 8.3.2, and `True` again from 8.4.0 to 8.5.1, the latest. So the regression was Click's and is fixed upstream; what is missing is keeping environments off the broken releases. `requirements-docs.txt` adds `click==8.5.1`, exact like every other line, so a contributor's existing environment on 8.3.x is upgraded by `pip install -r requirements-docs.txt` and CI builds with the same version.
Alternatives: `click>=8.4` (keeps the fix but lets CI and local drift, unlike the rest of the file); documenting `mkdocs serve --livereload` (a workaround everyone has to remember, and unnecessary once the broken releases are excluded).

## Risks / Trade-offs

- [The exact Click pin gets old] → Same as the other pins: bumped deliberately in a change, and the workflow verifies the bump.
- [A path-filtered check never reports on pull requests it skips; if it were made required, those pull requests would wait forever] → It is not made required here; if it becomes required later, it needs a companion no-op job or a filter moved into the job.
- [Material prints an end-of-life notice about MkDocs 2 on every build] → It is plain output, not a MkDocs warning, so it doesn't fail strict mode; the workflow sets `NO_MKDOCS_2_WARNING=1` to keep the log clean.
