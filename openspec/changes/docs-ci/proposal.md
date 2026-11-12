# Proposal

## Why

The documentation site is only built on contributors' machines, so a broken link, a page missing from the navigation or a regression in the changelog hook reaches `develop` unnoticed. Separately, `mkdocs serve` lost its live reload with Click 8.3.0 to 8.3.2, and nothing in `requirements-docs.txt` keeps a contributor's environment off those releases.

## What Changes

- A new GitHub Actions workflow, `Docs`, that builds the site with `mkdocs build --strict` and runs the changelog hook's tests (`python3 -m unittest discover -s scripts/mkdocs`) on every pull request that touches `docs/`, `mkdocs.yml`, `requirements-docs.txt`, `scripts/mkdocs/` or `.github/release-notes/`, and that a contributor can also start by hand.
- `mkdocs.yml` raises the validation of anchors and unrecognized links from info to warning, so the strict build fails on a link to a heading that doesn't exist.
- `requirements-docs.txt` pins Click to 8.5.1, a release where MkDocs 1.6.1 parses the default of `--livereload` as on, so `mkdocs serve` reloads again without extra flags.
- The one broken anchor the stricter validation finds today (`#título-de-la-release` in `docs/process/release.es.md`) is fixed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `continuous-integration`: adds a requirement that the documentation site is built strictly and the changelog hook is tested on every pull request that touches them.

## Impact

- New `.github/workflows/docs.yml`; `mkdocs.yml`, `requirements-docs.txt` and `docs/process/release.es.md` edited.
- No Kotlin code, Gradle build or existing workflow changes; the `Tests` workflow is unaffected.
- Contributors who rerun `pip install -r requirements-docs.txt` get Click 8.5.1.
