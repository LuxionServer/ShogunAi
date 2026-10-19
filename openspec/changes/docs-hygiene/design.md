# Design

## Context

See proposal.md. The review compiled every module with `--rerun-tasks` and listed 12 warnings; the unused strings are `copyCommand`, `daemonOwnerOther`, `goToWorktree`, `identitySection`, `pathsSection`, `noDerivedDataFolders` and `paletteCopyLaunchCommandHint`. The last one is used again by `desktop-review-fixes`, so it stays if that change lands first and is removed here otherwise. The release process docs (`docs/process/release.md`) are fixed by `release-hardening`, not here.

## Goals / Non-Goals

**Goals:**
- A warning-free build and docs that match the code.

**Non-Goals:**
- Treating warnings as errors in the build; it can be proposed once the build is clean.
- Dependency upgrades beyond the beta `androidx-lifecycle`.

## Decisions

**Deprecated APIs.** `painterResource(String)` → Compose resources (`Res.drawable…`) if the module already uses the resources plugin, otherwise `useResource` with `loadSvgPainter`/`loadImageBitmap`, whichever needs fewer changes in the four call sites. `LocalClipboardManager` → `LocalClipboard` with its suspend API, called from the existing coroutine scopes. Icons → `Icons.AutoMirrored.Filled.*`.

**Herdr manifest test.** A CLI test parses `integrations/herdr/herdr-plugin.toml` with a minimal TOML read (the file is flat), and asserts every `[[events]]` `on` value is one the CLI handles and the command is `["shogun", "hook", "herdr"]`. The plugin's `version` stays independent of the app's, and the docs say the plugin is installed from the `main` branch (released code).

**`settings.json` docs.** List every stored key with one line each: theme, language, window bounds, sidebar collapsed, collapsed projects, last destination, last project used to create a worktree, recent palette items, grouping per view.

## Risks / Trade-offs

- [Clipboard API change behaves differently on Linux] → Covered by the existing copy UI tests.

## Open Questions

- Whether `herdr plugin install` accepts a branch or tag; if it does, the docs show the `main` form explicitly, otherwise they say it follows the default branch and point to the release notes. It doesn't change the tasks.
