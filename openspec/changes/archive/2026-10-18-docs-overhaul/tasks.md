# Tasks

## 1. Navigation

- [x] 1.1 Add the "User guide" section (Installation, Desktop app, Command-line reference, Configuration, Providers) to `mkdocs.yml`'s nav, add the Spanish `nav_translations` for it and for `Release`, and broaden `site_description`; verify with `mkdocs build --strict` once group 2 lands

## 2. User guide pages (English and Spanish)

- [x] 2.1 Write `docs/guide/installation.md` and `.es.md` (Homebrew cask, .deb/.msi/zip, `shogun` symlink, the macOS `xattr` workaround, optional runtime tools); verify every command against `process/release.md` and the README
- [x] 2.2 Write `docs/guide/desktop-app.md` and `.es.md` (projects, worktree creation and its base, overview, status, auto refresh, cleanup, Open in terminal/Herdr, theme, language, keyboard shortcuts); verify against `architecture/ui.md` and the `keyboard-shortcuts` spec
- [x] 2.3 Write `docs/guide/cli.md` and `.es.md` with every command, option, alias, output line and exit code; verify each one against `CliCommand.kt` and the USAGE text in `ShogunCli.kt`
- [x] 2.4 Write `docs/guide/configuration.md` and `.es.md` (`.shogunai.json` keys, types, validation and templates; `.worktreeinclude`; `~/.shogunai/` files; `shogunai-env.json`; Gradle init script; Herdr `config.toml`; port ranges); verify against `RepositoryProvisioningFiles.kt`, `JsonProjectRepository.kt` and `JsonSettingsFile.kt`
- [x] 2.5 Write `docs/guide/providers.md` and `.es.md` (Gradle, Android emulator, iOS simulator with the KMP snippet and `gradle-init`, Xcode), moving the guide content from README; verify the snippets and commands against the code and the README text they replace

## 3. Existing pages (English and Spanish)

- [x] 3.1 Update `index.md`/`.es.md`: feature summary, "Where to look" rows for the guide, agents, CLI and UI, and `:cli:run`; verify the links resolve
- [x] 3.2 Update `getting-started.md`/`.es.md`: the module tree (`desktopApp/infrastructure`, `BuildInfo`, core infrastructure scope) and a link to the optional tools in the installation guide
- [x] 3.3 Update `agents.md`/`.es.md`: the `WorktreeCreate` hook starts from the caller worktree's HEAD; replace README references with links to the guide pages; link `integrations/herdr/README.md`
- [x] 3.4 Update `architecture/overview.md`/`.es.md`: the CLI command count, the full list of ports and the use case areas
- [x] 3.5 Update `architecture/cli.md`/`.es.md`: drop the command table in favour of a link to `guide/cli.md`, fix the "four commands" sentence and the `Main.kt` wiring (`MacDerivedDataSource`, `CliHerdrClient` default)
- [x] 3.6 Update `architecture/domain.md`/`.es.md`: add the 14 missing `WorktreeError` variants, `WorktreeOrigin`/`RunningAgent`, and headings for `ResolveStartPointUseCase`, `PrepareWorktreeEnvironmentUseCase` and `WriteEnvironmentFileUseCase`; verify against `core/.../domain`
- [x] 3.7 Update `architecture/infrastructure.md`/`.es.md`: the `NioFileManager` operations, a link to the provisioning validation reference, `cli` as a fixtures user, and the full infrastructure test list
- [x] 3.8 Update `architecture/ui.md`/`.es.md`: what `AppContainer` builds, how `main.kt` dispatches (CLI vs. UI, the `-psn_` filter, the Dock icon), and the order of the Herdr paragraphs
- [x] 3.9 Add the superseded note before the `shared` entry in `decisions/index.md`/`.es.md`, leaving the entry text unchanged
- [x] 3.10 Update `roadmap.md`/`.es.md`: the worktree-base item (what's done vs. pending) and the Android/KMP/JVM status

## 4. README

- [x] 4.1 Shorten README's Command line, Gradle, Android emulators, iOS simulators and Xcode sections to summaries that link to the published docs pages; verify no information is lost compared with the guide pages

## 5. Verification

- [x] 5.1 Run `mkdocs build --strict` with no warnings, and check with a script that each `page.md` and `page.es.md` pair has the same heading count
- [x] 5.2 Serve the site (`mkdocs serve`) and check the new nav in both languages and a few cross-links by hand

## Workflow follow-up

- Archive the change (`/opsx:archive`) before opening the pull request.
