## ADDED Requirements

### Requirement: Documentation checked on pull requests that touch it
The system SHALL build the documentation site in strict mode and run the changelog hook's tests in CI for every pull request that changes `docs/`, `mkdocs.yml`, `requirements-docs.txt`, `scripts/mkdocs/` or `.github/release-notes/`, whatever its base branch, and report the result on the pull request. A warning from the build, including a link to a page or an anchor that does not exist, SHALL fail the check.

#### Scenario: Pull request with a broken anchor
- **WHEN** a pull request edits a page under `docs/` so that it links to a heading that does not exist on the target page
- **THEN** the documentation check fails and its log names the page and the link

#### Scenario: Pull request that breaks the changelog hook
- **WHEN** a pull request changes `scripts/mkdocs/changelog_hook.py` so that one of its tests fails
- **THEN** the documentation check fails

#### Scenario: Pull request with valid documentation
- **WHEN** a pull request changes `docs/` or `.github/release-notes/`, the site builds without warnings and the hook's tests pass
- **THEN** the documentation check succeeds

#### Scenario: Pull request that touches no documentation
- **WHEN** a pull request changes only Kotlin sources
- **THEN** the documentation check does not run
