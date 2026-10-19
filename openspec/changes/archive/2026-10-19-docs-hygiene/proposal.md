# Proposal

## Why

The project review of 2026-10-19 found small drifts that don't change behavior but make the project harder to trust and maintain: docs that miss an exit code and most of `settings.json`, a Herdr plugin manifest nothing checks, compiler warnings from deprecated APIs, unused strings and dependencies, and ignore rules that rely on the maintainer's global configuration in a public repository.

## What Changes

- `docs/guide/cli.md` documents exit code 3 (`provision --check` found differences), and `docs/guide/configuration.md` describes everything `settings.json` stores; both in English and Spanish.
- `docs/agents.md` states which branch the Herdr plugin is installed from, and a test checks the plugin manifest (event names and command) against the CLI.
- The 12 compiler warnings are fixed: `painterResource(String)`, `LocalClipboardManager`, the non-mirrored list and sidebar icons and the unnecessary safe calls.
- The seven unused strings are removed from the three string files, and Spanish `detachedHead` is translated.
- `androidx-lifecycle` moves to a stable version, `compose.uiToolingPreview` and the redundant `materialIconsCore` dependency are removed.
- The repository's `.gitignore` covers `.env*` and `.claude/settings.local.json`.
- `AGENTS.md` no longer mentions code comments as something written in English, consistent with the no-comments rule.

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- Docs: `docs/guide/cli.md`, `docs/guide/configuration.md`, `docs/agents.md` and their `.es.md` versions.
- `desktopApp`: the files with deprecated API uses, `Strings.kt`, `EnglishStrings.kt`, `SpanishStrings.kt`, `build.gradle.kts`.
- `cli`: `ShogunCli.kt` safe calls; a new test for `integrations/herdr/herdr-plugin.toml`.
- `gradle/libs.versions.toml`, `.gitignore`, `AGENTS.md`.
- No behavior changes, so no spec deltas.
