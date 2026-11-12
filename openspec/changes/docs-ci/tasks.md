# Tasks

## 1. Strict validation and existing warnings

- [ ] 1.1 Add `validation: links: anchors: warn` and `unrecognized_links: warn` to `mkdocs.yml`; verify `mkdocs build --strict` now fails on `#título-de-la-release` in `docs/process/release.es.md`
- [ ] 1.2 Enable the `attr_list` extension, give the Spanish heading the id `titulo-de-la-release` and point its two links at it; verify `mkdocs build --strict` passes with no anchor or link warning in either language

## 2. Live reload

- [ ] 2.1 Add `click==8.5.1` to `requirements-docs.txt`; verify in a fresh virtual environment that `pip install -r requirements-docs.txt` installs Click 8.5.1 and upgrades an environment holding Click 8.3.1
- [ ] 2.2 Run `mkdocs serve` with no flags; verify the served page includes the live-reload script and that editing a page triggers a rebuild and a reload

## 3. Workflow

- [ ] 3.1 Add `.github/workflows/docs.yml` as described in the design (paths filter, `workflow_dispatch`, concurrency, checkout with full history, Python 3.13 with pip cache, hook tests, strict build); verify its syntax and run the same commands locally
- [ ] 3.2 Push the branch and verify the `Docs` check runs and succeeds on the pull request
- [ ] 3.3 Verify locally, with the workflow's commands and uncommitted throwaway edits, that a broken anchor and a failing hook test each make them exit non-zero; verify from the pull request's checks that the `Docs` workflow is listed only because the pull request touches its paths

## Workflow follow-up

- Archive the change with `/opsx:archive`, removing the two "Robustness and maintenance" bullets about the documentation workflow and live reload from `docs/roadmap.md` and `docs/roadmap.es.md`.
- Bring the branch up to date with `origin/develop` and open the pull request to `develop`, assigned to CiroFeliu.
