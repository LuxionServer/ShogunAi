# Tasks

## 1. Color system

- [x] 1.1 Replace `ui/theme/Color.kt` with the neutral ramp, the brand accent (light and dark tones) and the five status colors with their container variants; verify no other file declares `Color(0x...)` literals (`grep` over `desktopApp/src/main`).
- [x] 1.2 Define every Material 3 role in both schemes in `Theme.kt`, including `surfaceContainerLowest` to `surfaceContainerHighest`, `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `errorContainer`, `outlineVariant`, `inverseSurface` and `scrim`; verify with a test that lists every `ColorScheme` property and asserts none equals Material's baseline value.
- [x] 1.3 Replace `ShogunExtendedColors` with `ShogunStatusColors` (success, warning, info, danger, neutral, each with `color`, `onColor`, `container`, `onContainer`) exposed as `ShogunTheme.status`; verify the project compiles with every former `extendedColors.success` call migrated.
- [x] 1.4 Add `contrastRatio` and `ThemeContrastTest` covering on-colors on their roles, status colors on `background` and every `surfaceContainer*`, and `onSurfaceVariant` on surfaces, in both schemes; verify `./gradlew :desktopApp:test` passes with every pair at 4.5:1 or above (3:1 for indicators).

## 2. Typography and shapes

- [x] 2.1 Add Geist and Geist Mono (400, 500, 600) and `OFL.txt` under `desktopApp/src/main/resources/fonts/`; verify the packaged app (`./gradlew :desktopApp:packageDistributionForCurrentOS`) contains them.
- [x] 2.2 Define every `Typography` slot with Geist in `Type.kt`, and add the `data` and `dataSmall` styles (Geist Mono, `tnum`) to `ShogunTheme.typography`; verify with a unit test that every `Typography` slot uses the Geist family.
- [x] 2.3 Add the `Shapes` scale to the theme and `xxs`/`xl` to `Spacing`; verify the theme passes `shapes` to `MaterialTheme`.

## 3. Shared components

- [x] 3.1 Add `InlineProgress` and replace every inline `CircularProgressIndicator` in `ui/`; verify by `grep` that `CircularProgressIndicator(` only remains inside `InlineProgress`.
- [x] 3.2 Add `Notice` and use it for the worktree screen's error and success messages, `CleanupIssues` and `DaemonStopMessage`; verify the existing `WorktreeViewModelTest` still passes and each notice is dismissible in the running app.
- [x] 3.3 Add `StatusChip` and map the current status labels onto it (merged, locked, inactive, folder missing, agent running, device states) using the status table in `design.md`; verify with a unit test of the label-to-status mapping function.
- [x] 3.4 Add `DangerButton`/`DangerTextButton` and use them for every delete or remove action and for the confirm action of deletion dialogs (project, worktree, force removal, cleanup, orphan deletion); verify by `grep` over `strings.delete`, `forceRemove` and `cleanUp` call sites.
- [x] 3.5 Add `IconAction` (tooltip and `contentDescription` from one label) and replace every icon-only `IconButton`; verify by `grep` that `IconButton(` only remains inside `IconAction`.
- [x] 3.6 Add `Modifier.interactive` with hover indication, hand pointer and focus ring, and use it on project rows, `SectionCard`'s collapsible header and the branch group headers; verify in the running app that the hand pointer appears on hover and focus is visible when tabbing.

## 4. Adoption and documentation

- [x] 4.1 Replace literal dp values in `ProjectListScreen`, `ProjectConfigScreen` and the worktree sections with `Spacing` tokens or named size constants; verify by `grep` that no `padding(<n>.dp)` literals remain in `ui/`.
- [x] 4.2 Apply the `data`/`dataSmall` styles to paths, branches, ports, PIDs, sizes, counts and relative times on every screen; verify in the running app that sizes refreshing during auto refresh don't shift the row.
- [x] 4.3 Add a "Design system" section to `docs/architecture/ui.md` and `ui.es.md` (palette and status meanings, fonts, type styles, shared components); verify `mkdocs build --strict` succeeds.
- [x] 4.4 Run the app on the secondary display in light and dark mode at 900 px and 1600 px wide, and capture the project list, project configuration, worktree screen and its dialogs; verify no baseline purple remains and every status uses the color from the table.

## Workflow follow-up

- Archive the change (`/opsx:archive ui-design-foundations`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `ui-design-foundations`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
