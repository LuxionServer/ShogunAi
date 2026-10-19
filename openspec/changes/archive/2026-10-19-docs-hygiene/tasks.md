# Tasks

## 1. Docs

- [x] 1.1 Add exit code 3 to `docs/guide/cli.md` and `cli.es.md`, and every `settings.json` key to `docs/guide/configuration.md` and `configuration.es.md`; verify `mkdocs build --strict` passes
- [x] 1.2 State the branch the Herdr plugin is installed from in `docs/agents.md` and `agents.es.md`, and add the manifest test in `cli`; verify the test passes
- [x] 1.3 Reword the language rule in `AGENTS.md` so it doesn't mention comments, and verify it still points to `docs/` for the Spanish translation

## 2. Build hygiene

- [x] 2.1 Fix the deprecated `painterResource`, `LocalClipboardManager` and icon uses and the unnecessary safe calls; verify `./gradlew build --rerun-tasks` reports no Kotlin warnings
- [x] 2.2 Remove the unused strings (keeping `paletteCopyLaunchCommandHint` if `desktop-review-fixes` already uses it) and translate Spanish `detachedHead`; verify `StringsTest` passes
- [x] 2.3 Move `androidx-lifecycle` to the latest stable and remove `uiToolingPreview` and `materialIconsCore`; verify `./gradlew test` passes and the app starts with `./gradlew :desktopApp:run`
- [x] 2.4 Add `.env*` and `.claude/settings.local.json` to `.gitignore` and verify `git check-ignore` matches both

## Workflow follow-up

- Archive the change before opening its pull request.
