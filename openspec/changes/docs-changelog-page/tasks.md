# Tasks

## 1. Changelog rendering

- [ ] 1.1 Write `scripts/mkdocs/test_changelog_hook.py` (standard `unittest`) covering the spec scenarios that need no MkDocs: file selection, numeric descending order, Spanish fallback and Spanish-only exclusion, release links and the older-releases link, heading shifting (including notes without headings and code fences), and the empty state; verify it fails before the module exists
- [ ] 1.2 Implement the pure functions of `scripts/mkdocs/changelog_hook.py` (no comments, no MkDocs import at module level) and verify `python3 -m unittest discover -s scripts/mkdocs` passes

## 2. MkDocs integration

- [ ] 2.1 Add `on_files` (generated `changelog.md` and `changelog.es.md`) and `on_serve` (watch `.github/release-notes/`) to the hook, register it under `hooks:` in `mkdocs.yml`, and add the nav entry with its Spanish translation; verify `mkdocs build --strict` passes and `site/changelog/index.html` and `site/es/changelog/index.html` show 0.9.5 in their language
- [ ] 2.2 Verify with `mkdocs serve` that both pages render in the navigation and that editing a notes file reloads the page with the change (then revert the edit)
- [ ] 2.3 Update `docs/process/release.md` and `docs/process/release.es.md` to say the notes also build the changelog page, linking to it; verify `mkdocs build --strict` still passes

## Workflow follow-up

- Archive the change with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` (remove the changelog bullet of point 7, add the docs CI job as a follow-up).
- Bring the branch up to date with `develop`, then open the pull request against `develop`, assigned to CiroFeliu.
