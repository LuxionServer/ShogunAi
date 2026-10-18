# Design

## Context

See proposal.md (Why). The docs site is built by mkdocs with Material and `mkdocs-static-i18n` (`docs_structure: suffix`). Each page has an English `page.md` and a Spanish `page.es.md`, and the two are kept structurally in sync: same headings and the same order. The code is split into three modules, `core`, `cli` and `desktopApp`. Today the user-facing guides (basic usage, command line, Gradle, emulators, iOS simulators, Xcode) live in `README.md`.

## Goals / Non-Goals

**Goals:**
- Docs readers can install, use and configure ShogunAi without opening the README.
- Each fact has one home. User guides explain how to use things; the architecture pages explain how the code is built, and they link to the guides instead of repeating them.
- The English and Spanish pages stay structurally identical.

**Non-Goals:**
- No changes to code, specs or the mkdocs theme and plugins.
- No rewriting of the historical entries in `decisions/index.md`.
- No screenshots or new diagrams. Visual polish is left for a later change.

## Decisions

- **A new "User guide" nav section with a `guide/` folder.** It holds `installation.md`, `desktop-app.md`, `cli.md`, `configuration.md` and `providers.md`, and sits between "Getting started" and "Using with agents". "Getting started" stays contributor-oriented (build from source).
  - Alternative: grow `getting-started.md` into one long page. Rejected because install, CLI reference and configuration are separate things readers look up.
- **`guide/cli.md` is the authoritative command reference.** It covers commands, options, aliases, output lines and exit codes. `architecture/cli.md` keeps only parsing, wiring (`Main.kt`, `ShogunCli`) and packaging, and links to the guide.
  - Alternative: keep the table in `architecture/cli.md`. Rejected because user-facing reference doesn't belong in architecture, and two copies would drift.
- **`guide/configuration.md` is the single reference for configuration files.** It covers `.shogunai.json` with every key, type, validation rule and template variable, plus `.worktreeinclude`, `~/.shogunai/*`, `<git admin dir>/shogunai-env.json`, the Gradle init script location, the Herdr `config.toml` lookup and the port ranges. `architecture/domain.md` and `architecture/infrastructure.md` keep the model and parser descriptions and link to it for the user-facing schema.
- **One `guide/providers.md` page with a section per provider** (Gradle, Android emulator, iOS simulator, Xcode). Each section covers what it does, how to enable it in `.shogunai.json`, the desktop and CLI commands, and cleanup.
  - Alternative: one page per provider. Rejected because each provider is short, and a single page makes it easy to compare them.
- **The README links to the guides instead of repeating them.** It keeps the intro, the install summary, a short "Basic usage" and "Development". The provider and command-line sections become a short summary each, with a link to the docs page. This avoids two copies drifting. The README is what GitHub and Homebrew users see first, so it keeps enough to get started.
- **The superseded decision gets a note outside the entry.** In `decisions/index.md`, a `!!! note` admonition placed before the entry points to `architecture/overview.md` and the `extract-core-module` change. This follows the "don't edit old entries" rule while still telling readers the entry is out of date.
- **Write English first, then mirror it in Spanish.** Each task edits both files of a page, so the pair never diverges between tasks.
- **Get facts from the code, not from memory.** CLI usage comes from `ShogunCli.kt` (USAGE) and `CliCommand.kt`. The configuration schema comes from `RepositoryProvisioningFiles.kt`, `JsonProjectRepository.kt` and `JsonSettingsFile.kt`. The output lines come from `ShogunCli.kt`.

## Risks / Trade-offs

- [The docs drift again as features land] → Each future OpenSpec change already owes its docs. The new guide pages give those updates an obvious home.
- [Links break when content moves out of the README] → `mkdocs build --strict` catches broken internal links. README links point to the published docs URLs, and those are checked by hand.
- [The Spanish translation lags or diverges] → Every page task covers both languages, and the final check compares the heading counts of each pair.

## Migration Plan

There is nothing to migrate. The change only affects the docs site and the README.
