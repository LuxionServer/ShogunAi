# Proposal

## Why

The mkdocs site (`docs/`, English and Spanish) has fallen behind the last two weeks of changes. Its architecture pages are close to the code, but some sentences are stale (four CLI commands instead of twelve, an incomplete port list, an outdated `AppContainer` description, the old worktree base in the agents guide). It also has no user-facing material. Installation, the provider guides, the `.shogunai.json` reference and the inventory of files under `~/.shogunai/` exist only in `README.md` or are spread across architecture pages. Readers of the docs site can't learn how to use or configure ShogunAi without leaving it.

## What Changes

- Add a **User guide** section to the site with four new pages, each in English and Spanish:
  - **Installation**: Homebrew cask, `.deb`/`.msi`/zip, the `shogun` symlink, the macOS `xattr` workaround, optional runtime tools.
  - **Desktop app**: a walkthrough of projects, worktrees, overview and cleanup, theme, language and keyboard shortcuts.
  - **Command-line reference**: every `shogun` command, its options and aliases, its output lines and its exit codes.
  - **Configuration**: the `.shogunai.json` keys and validation, `.worktreeinclude`, the files under `~/.shogunai/`, per-worktree state and port ranges.
- Add a **Providers** guide page covering Gradle daemons and settings, Android emulators, iOS simulators (including the KMP snippet and `gradle-init`) and Xcode DerivedData and compilation cache. This moves the user guide content that today lives only in `README.md` into the docs.
- Fix stale content in the existing pages:
  - `index.md`: broaden the feature summary and the "Where to look" table.
  - `getting-started.md`: complete the module tree and the optional tools.
  - `agents.md`: correct the `WorktreeCreate` base, and link to the docs pages instead of README sections.
  - `architecture/overview.md`: update the CLI command count, ports and use cases.
  - `architecture/cli.md`: keep the internals here, and link to the new reference instead of duplicating the command table.
  - `architecture/domain.md`: add the missing `WorktreeError` variants and the overview models.
  - `architecture/infrastructure.md`: add the provisioning validation rules, fixture users and the test list.
  - `architecture/ui.md`: describe what `AppContainer` builds and how `main.kt` dispatches.
  - `roadmap.md`: update the worktree-base item and the Android/KMP status.
- Add a "superseded" note to `decisions/index.md` for the `shared`-module entry. The note sits outside the entry, so the historical text is left untouched.
- Update `mkdocs.yml`: add the new nav section, its `nav_translations` (including the missing `Release` entry), and a broader `site_description`.
- Trim `README.md`'s long guide sections to a summary that links to the docs site, so there is a single source of truth.

## Capabilities

### New Capabilities

None. This is a documentation-only change.

### Modified Capabilities

None. No spec-level behavior changes, so the change sets `skip_specs: true`.

## Impact

- `docs/**/*.md` and `docs/**/*.es.md`: new and edited pages.
- `mkdocs.yml`: the nav, translations and description.
- `README.md`: the guide sections are shortened and point to the docs site.
- No code, build or behavior changes.
