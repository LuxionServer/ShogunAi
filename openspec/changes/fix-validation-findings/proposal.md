## Why

A full validation of the last three merged pull requests (the branch dropdown grouping fix, `fix-repository-relocation` and `add-ui-localization`) — unit tests, end-to-end Git checks and a manual pass over the installed `.dmg` — found that they work, but surfaced several defects and a documentation set that no longer matches the code:

- When `git worktree repair` fails after saving a moved project, the error flashes and disappears: the reload that follows (and the view model's initial load, which can finish later) clears it, so the user never learns the repair failed. The `worktree-workspace` spec already requires the error to stay visible.
- When the OS can't start a command at all (e.g. `git` missing from `PATH`), `CommandExecutionException` carries a hardcoded Spanish message that is shown verbatim, even with the UI in English.
- A branch that already has a worktree is rendered disabled in the "Existing branch" dropdown, but an accessibility press (VoiceOver / assistive technologies) still selects it, and "Create worktree" then becomes enabled for it. A plain mouse click is correctly ignored.
- The segmented "New branch / Existing branch" toggle truncates its labels ("Existing b…", "Rama exist…"): the segmented row sizes its buttons from their minimum intrinsic width, which for wrappable text is its longest word.
- Local builds show a stale version (`v0.0.0` after packaging with `-PappVersion=1.6.99`): `generateBuildInfo` doesn't declare the version as an input, so Gradle keeps it `UP-TO-DATE`.
- Documentation drift: the architecture docs still describe the terminal-launch flow removed in favour of copying the launch command (`OpenWorktreeTerminalUseCase`, `TerminalLauncher`, `TerminalEmulatorDetector`, `TerminalPreference`), and don't cover creating worktrees from existing branches, the clipboard port, or repairing worktrees after a move. `docs/process/release.md` claims local builds fall back to `0.0.0`, which `jpackage` rejects on macOS. `mkdocs.yml` points to the wrong repository. `AGENTS.md` doesn't state that the repository is public.

## What Changes

- `WorktreeViewModel.repairAfterMove` keeps a repair failure visible after the follow-up reload; stale list results from an earlier load can no longer overwrite state from a newer one.
- Process start failures (`CommandExecutionException`) are rendered with a localized message that names the command; the exception's own message is in English.
- `DropdownSelector` ignores selection of a disabled option regardless of the input method, and the "Create worktree" action in "Existing branch" mode is only enabled for a branch that is available.
- `SegmentedSelector` labels don't wrap, so each segment is sized to its whole label.
- `generateBuildInfo` declares `appVersion` as a task input.
- Architecture, process and home docs (English and Spanish), `README.md`/`README.es.md`, `mkdocs.yml` and `AGENTS.md` are brought in line with the code.
- The validated changes `add-ui-localization` and `fix-repository-relocation` are archived (the latter's artifacts translated to English, as `openspec/config.yaml` requires).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-workspace`: a branch that already has a worktree can't be selected by any input method.

## Impact

- `ui/worktree/WorktreeViewModel.kt`, `ui/worktree/WorktreeScreen.kt`, `ui/components/Selector.kt`, `ui/i18n/Strings.kt`, `ui/i18n/EnglishStrings.kt`, `ui/i18n/SpanishStrings.kt`, `domain/executor/ShellCommandExecutor.kt`.
- `desktopApp/build.gradle.kts`.
- Tests: `WorktreeViewModelTest`, `StringsTest`, `SelectorTest`.
- `docs/**` (both languages), `README*.md`, `mkdocs.yml`, `AGENTS.md`, `openspec/changes/` → `openspec/changes/archive/`, `openspec/specs/`.
